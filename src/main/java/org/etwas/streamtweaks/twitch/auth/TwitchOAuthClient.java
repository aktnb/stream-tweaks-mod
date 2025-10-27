package org.etwas.streamtweaks.twitch.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.etwas.streamtweaks.StreamTweaks.LOGGER;
import static org.etwas.streamtweaks.StreamTweaks.devLogger;

public class TwitchOAuthClient {
    private final Gson GSON = new GsonBuilder().create();
    private final HttpClient http = HttpClient.newHttpClient();
    public final TwitchCredentialStore store;
    public final String CLIENT_ID = "p5xrtcp49if1zj6b86y356htualkth";
    public final String[] DEFAULT_SCOPES = new String[] {
            "user:read:chat"
    };

    private LocalHttpCallbackServer currentServer = null;
    private String currentState = null;
    private CompletableFuture<AuthResult> currentTokenFuture = null;
    private final Object authorizationLock = new Object();

    public TwitchOAuthClient() {
        this(new TwitchCredentialStore());
    }

    TwitchOAuthClient(TwitchCredentialStore store) {
        this.store = store;
    }

    public CompletableFuture<Boolean> hasValidToken() {
        var credentials = store.loadOrCreate();
        if (credentials.accessToken() == null) {
            return CompletableFuture.completedFuture(false);
        }
        return validateToken(credentials.accessToken())
                .thenApply(validation -> validation.isValid);
    }

    public CompletableFuture<AuthResult> getAccessToken(Consumer<String> onRequiresUserInteraction) {
        var credentials = store.loadOrCreate();
        if (credentials.accessToken() != null) {
            var validation = validateToken(credentials.accessToken()).join();
            if (validation.isValid) {
                return CompletableFuture
                        .completedFuture(new AuthResult(credentials.accessToken(), AuthResult.AuthType.CACHED_TOKEN));
            }
        }
        return authorize(onRequiresUserInteraction);
    }

    public CompletableFuture<AuthResult> authorize(Consumer<String> onRequiresUserInteraction) {
        var state = Long.toHexString(Double.doubleToLongBits(Math.random()));
        var scope = String.join("+", DEFAULT_SCOPES);
        var url = "https://id.twitch.tv/oauth2/authorize" +
                "?client_id=" + CLIENT_ID +
                "&redirect_uri=" + URLEncoder.encode("http://localhost:7654/callback", StandardCharsets.UTF_8) +
                "&response_type=token" +
                "&scope=" + URLEncoder.encode(scope, java.nio.charset.StandardCharsets.UTF_8) +
                "&state=" + state;

        synchronized (authorizationLock) {
            if (currentTokenFuture != null && !currentTokenFuture.isDone()) {
                currentTokenFuture.complete(null);
            }

            currentState = state;
            currentTokenFuture = new CompletableFuture<>();

            if (currentServer == null) {
                try {
                    currentServer = new LocalHttpCallbackServer(7654, "/callback", this::handleCallback);
                    currentServer.start();
                } catch (Exception e) {
                    currentTokenFuture.complete(null);
                    return currentTokenFuture;
                }
            }

            onRequiresUserInteraction.accept(url);
            return currentTokenFuture;
        }
    }

    public CompletableFuture<ValidateResult> validateToken(String token) {
        var request = HttpRequest.newBuilder(URI.create("https://id.twitch.tv/oauth2/validate"))
                .setHeader("Authorization", "OAuth " + token)
                .GET().build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var body = response.body();
                var tokenValidationResponse = GSON.fromJson(body, TokenValidationResponse.class);
                var result = tokenValidationResponse != null && tokenValidationResponse.client_id.equals(CLIENT_ID)
                        && hasScopes(tokenValidationResponse, DEFAULT_SCOPES);
                return CompletableFuture.completedFuture(new ValidateResult(result, tokenValidationResponse.login));
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Failed to validate Twitch token", e);
        }
        return CompletableFuture.completedFuture(new ValidateResult(false, null));
    }

    private void handleCallback(Map<String, String> params) {
        synchronized (authorizationLock) {
            var receivedState = params.get("state");

            if (currentState != null && currentState.equals(receivedState) &&
                    currentTokenFuture != null && !currentTokenFuture.isDone()) {

                if (params.containsKey("access_token")) {
                    var accessToken = params.get("access_token");
                    var validation = validateToken(accessToken).join();
                    if (!validation.isValid) {
                        currentTokenFuture.complete(null);
                        cleanupServer();
                        return;
                    }

                    devLogger("Obtained new access token for user: %s".formatted(validation.login));
                    store.save(new TwitchCredentials(accessToken, validation.login));
                    currentTokenFuture.complete(new AuthResult(accessToken, AuthResult.AuthType.NEW_AUTHORIZATION));
                    cleanupServer();
                } else if (params.containsKey("error")) {
                    LOGGER.warn("OAuth Error: {} - {}", params.get("error"),
                            params.getOrDefault("error_description", ""));
                    currentTokenFuture.complete(null);
                    cleanupServer();
                } else {
                    currentTokenFuture.complete(null);
                    cleanupServer();
                }
            }
        }
    }

    private void cleanupServer() {
        if (currentServer != null) {
            currentServer.close();
            currentServer = null;
        }
        currentState = null;
        currentTokenFuture = null;
    }

    private boolean hasScopes(TokenValidationResponse validation, String[] scopes) {
        for (var scope : scopes) {
            boolean found = false;
            for (var s : validation.scopes) {
                if (s.equals(scope)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    public record TokenValidationResponse(
            String client_id,
            String login,
            String user_id,
            int expires_in,
            String[] scopes) {
    }

    public record ValidateResult(boolean isValid, String login) {
    }
}
