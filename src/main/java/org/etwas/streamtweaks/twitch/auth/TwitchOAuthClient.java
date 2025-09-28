package org.etwas.streamtweaks.twitch.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.etwas.streamtweaks.StreamTweaks.LOGGER;
import static org.etwas.streamtweaks.StreamTweaks.devLogger;

public class TwitchOAuthClient {
    private final Gson GSON = new GsonBuilder().create();
    private final HttpClient http = HttpClient.newHttpClient();
    public final TwitchCredentialStore store = new TwitchCredentialStore();
    public final String CLIENT_ID = "p5xrtcp49if1zj6b86y356htualkth";

    // シングルトンサーバーと現在のリクエスト管理
    private LocalHttpCallbackServer currentServer = null;
    private String currentState = null;
    private CompletableFuture<AuthResult> currentTokenFuture = null;
    private final Object authorizationLock = new Object();

    public CompletableFuture<AuthResult> getAccessToken(String[] scopes, Consumer<String> onRequiresUserInteraction) {
        var credentials = store.loadOrCreate();
        if (credentials.accessToken != null) {
            var validation = validateToken(credentials.accessToken);
            if (validation != null && validation.client_id.equals(CLIENT_ID) && hasScopes(validation, scopes)) {
                return CompletableFuture.completedFuture(new AuthResult(credentials.accessToken, AuthResult.AuthType.CACHED_TOKEN));
            }
        }
        return authorize(scopes, onRequiresUserInteraction);
    }

    public CompletableFuture<AuthResult> authorize(String[] scopes, Consumer<String> onRequiresUserInteraction) {
        var state = Long.toHexString(Double.doubleToLongBits(Math.random()));
        var scope = String.join("+", scopes);
        var url = "https://id.twitch.tv/oauth2/authorize" +
                "?client_id=" + CLIENT_ID +
                "&redirect_uri=" + urlEncode("http://localhost:7654/callback") +
                "&response_type=token" +
                "&scope=" + urlEncode(scope) +
                "&state=" + state;

        synchronized (authorizationLock) {
            // 前のリクエストをキャンセル
            if (currentTokenFuture != null && !currentTokenFuture.isDone()) {
                currentTokenFuture.complete(null); // キャンセル扱い
            }

            // 新しいリクエストを設定
            currentState = state;
            currentTokenFuture = new CompletableFuture<>();

            // サーバーが起動していない場合のみ起動
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

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private void handleCallback(Map<String, String> params) {
        synchronized (authorizationLock) {
            var receivedState = params.get("state");

            // 最新のstateと一致する場合のみ処理
            if (currentState != null && currentState.equals(receivedState) &&
                    currentTokenFuture != null && !currentTokenFuture.isDone()) {

                if (params.containsKey("access_token")) {
                    var accessToken = params.get("access_token");
                    var validation = validateToken(accessToken);
                    if (validation == null || !validation.client_id.equals(CLIENT_ID)) {
                        currentTokenFuture.complete(null);
                        cleanupServer();
                        return;
                    }

                    devLogger("Obtained new access token for user: %s".formatted(validation.login));
                    var credentials = store.loadOrCreate();
                    credentials.accessToken = accessToken;
                    credentials.login = validation.login;
                    store.save(credentials);
                    currentTokenFuture.complete(new AuthResult(accessToken, AuthResult.AuthType.NEW_AUTHORIZATION));
                    cleanupServer();
                } else if (params.containsKey("error")) {
                    LOGGER.warn("OAuth Error: {} - {}", params.get("error"), params.getOrDefault("error_description", ""));
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

    public TokenValidationResponse validateToken(String token) {
        var request = HttpRequest.newBuilder(URI.create("https://id.twitch.tv/oauth2/validate"))
                .setHeader("Authorization", "OAuth " + token)
                .GET().build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var body = response.body();
                return GSON.fromJson(body, TokenValidationResponse.class);
            }
        } catch (IOException | InterruptedException ignored) {
        }
        return null;
    }

    public static class TokenValidationResponse {
        public String client_id;
        public String login;
        public String user_id;
        public int expires_in;
        public String[] scopes;
    }

}
