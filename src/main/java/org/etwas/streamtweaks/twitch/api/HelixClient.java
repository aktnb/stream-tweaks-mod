package org.etwas.streamtweaks.twitch.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.etwas.streamtweaks.twitch.eventsub.SubscriptionSpec;
import org.etwas.streamtweaks.StreamTweaks;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class HelixClient {
    private static final String TWITCH_API_BASE = "https://api.twitch.tv/helix";
    private static final String EVENTSUB_SUBSCRIPTIONS_ENDPOINT = TWITCH_API_BASE + "/eventsub/subscriptions";

    private final HttpClient httpClient;
    private final Gson gson;
    private volatile String accessToken;
    private volatile String clientId;

    public HelixClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }

    public void setCredentials(String accessToken, String clientId) {
        this.accessToken = accessToken;
        this.clientId = clientId;
    }

    /**
     * Create EventSub Subscription
     *
     * @param subscription サブスクリプション仕様
     * @param sessionId    WebSocketセッションID
     * @return CompletableFuture<CreateSubscriptionResponse>
     */
    public CompletableFuture<CreateSubscriptionResponse> createEventSubSubscription(SubscriptionSpec subscription,
            String sessionId) {
        if (accessToken == null || clientId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("認証情報が設定されていません"));
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("type", subscription.type());
        requestBody.addProperty("version", subscription.version());
        requestBody.add("condition", gson.toJsonTree(subscription.condition()));

        JsonObject transport = new JsonObject();
        transport.addProperty("method", "websocket");
        transport.addProperty("session_id", sessionId);
        requestBody.add("transport", transport);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(EVENTSUB_SUBSCRIPTIONS_ENDPOINT))
                .header("Authorization", "Bearer " + accessToken)
                .header("Client-Id", clientId)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    StreamTweaks.LOGGER.debug("EventSub subscription response: {} {}", response.statusCode(),
                            response.body());

                    if (response.statusCode() == 202) {
                        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
                        return CreateSubscriptionResponse.success(responseJson);
                    } else {
                        String errorBody = response.body();
                        return CreateSubscriptionResponse.error(response.statusCode(), errorBody);
                    }
                })
                .exceptionally(throwable -> {
                    StreamTweaks.LOGGER.error("EventSub subscription request failed", throwable);
                    return CreateSubscriptionResponse.error(-1, throwable.getMessage());
                });
    }

    /**
     * Get EventSub Subscriptions
     * 
     * @return CompletableFuture<GetSubscriptionsResponse>
     */
    public CompletableFuture<GetSubscriptionsResponse> getEventSubSubscriptions() {
        if (accessToken == null || clientId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("認証情報が設定されていません"));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(EVENTSUB_SUBSCRIPTIONS_ENDPOINT))
                .header("Authorization", "Bearer " + accessToken)
                .header("Client-Id", clientId)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    StreamTweaks.LOGGER.debug("EventSub subscriptions list response: {} {}", response.statusCode(),
                            response.body());

                    if (response.statusCode() == 200) {
                        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
                        return GetSubscriptionsResponse.success(responseJson);
                    } else {
                        String errorBody = response.body();
                        return GetSubscriptionsResponse.error(response.statusCode(), errorBody);
                    }
                })
                .exceptionally(throwable -> {
                    StreamTweaks.LOGGER.error("EventSub subscriptions list request failed", throwable);
                    return GetSubscriptionsResponse.error(-1, throwable.getMessage());
                });
    }

    /**
     * Delete EventSub Subscription
     * 
     * @param subscriptionId サブスクリプションID
     * @return CompletableFuture<DeleteSubscriptionResponse>
     */
    public CompletableFuture<DeleteSubscriptionResponse> deleteEventSubSubscription(String subscriptionId) {
        if (accessToken == null || clientId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("認証情報が設定されていません"));
        }

        String url = EVENTSUB_SUBSCRIPTIONS_ENDPOINT + "?id=" + subscriptionId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Client-Id", clientId)
                .DELETE()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    StreamTweaks.LOGGER.debug("EventSub subscription delete response: {} {}", response.statusCode(),
                            response.body());

                    if (response.statusCode() == 204) {
                        return DeleteSubscriptionResponse.success();
                    } else {
                        String errorBody = response.body();
                        return DeleteSubscriptionResponse.error(response.statusCode(), errorBody);
                    }
                })
                .exceptionally(throwable -> {
                    StreamTweaks.LOGGER.error("EventSub subscription delete request failed", throwable);
                    return DeleteSubscriptionResponse.error(-1, throwable.getMessage());
                });
    }

    public static record CreateSubscriptionResponse(
            boolean isSuccess,
            int statusCode,
            JsonObject data,
            String subscriptionId,
            String errorMessage) {
        public static CreateSubscriptionResponse success(JsonObject data) {
            String subscriptionId = null;
            try {
                var dataArray = data.getAsJsonArray("data");
                if (dataArray != null && dataArray.size() > 0) {
                    var subscription = dataArray.get(0).getAsJsonObject();
                    if (subscription.has("id")) {
                        subscriptionId = subscription.get("id").getAsString();
                    }
                }
            } catch (Exception e) {
                StreamTweaks.LOGGER.warn("Failed to extract subscription ID from response", e);
            }
            return new CreateSubscriptionResponse(true, 202, data, subscriptionId, null);
        }

        public static CreateSubscriptionResponse error(int statusCode, String errorMessage) {
            return new CreateSubscriptionResponse(false, statusCode, null, null, errorMessage);
        }
    }

    public static record GetSubscriptionsResponse(
            boolean isSuccess,
            int statusCode,
            JsonObject data,
            String errorMessage) {
        public static GetSubscriptionsResponse success(JsonObject data) {
            return new GetSubscriptionsResponse(true, 200, data, null);
        }

        public static GetSubscriptionsResponse error(int statusCode, String errorMessage) {
            return new GetSubscriptionsResponse(false, statusCode, null, errorMessage);
        }
    }

    public static record DeleteSubscriptionResponse(
            boolean isSuccess,
            int statusCode,
            String errorMessage) {
        public static DeleteSubscriptionResponse success() {
            return new DeleteSubscriptionResponse(true, 204, null);
        }

        public static DeleteSubscriptionResponse error(int statusCode, String errorMessage) {
            return new DeleteSubscriptionResponse(false, statusCode, errorMessage);
        }
    }
}
