package org.etwas.streamtweaks.twitch.service;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import org.etwas.streamtweaks.StreamTweaks;
import org.etwas.streamtweaks.twitch.api.HelixClient;
import org.etwas.streamtweaks.twitch.api.HelixClient.TwitchUser;
import org.etwas.streamtweaks.twitch.auth.AuthResult.AuthType;
import org.etwas.streamtweaks.twitch.auth.TwitchOAuthClient;
import org.etwas.streamtweaks.twitch.eventsub.EventSubManager;
import org.etwas.streamtweaks.twitch.eventsub.EventSubManager.EventNotification;
import org.etwas.streamtweaks.twitch.eventsub.SubscriptionSpec;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

public final class TwitchService {
    private static volatile TwitchService instance;

    private final HelixClient helixClient = new HelixClient();
    private final EventSubManager subscriptionManager = new EventSubManager(helixClient);
    private final TwitchOAuthClient oauthClient = new TwitchOAuthClient();
    private final AtomicReference<ConnectionState> connectionState = new AtomicReference<>();

    private record ConnectionState(String login, String displayName, String broadcasterUserId,
            SubscriptionSpec chatSubscription) {
    }

    private TwitchService() {
        subscriptionManager.setNotificationHandler(this::handleEventSubNotification);
    }

    public static TwitchService getInstance() {
        if (instance == null) {
            synchronized (TwitchService.class) {
                if (instance == null) {
                    instance = new TwitchService();
                }
            }
        }
        return instance;
    }

    public CompletableFuture<Void> ensureAuthenticated() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return oauthClient.getAccessToken(new String[] { "user:read:chat" }, url -> {
                    MutableText msg = Text.literal("[StreamTweaks] 認証が必要です．")
                            .formatted(Formatting.YELLOW)
                            .append(Text.literal("ここをクリックして認証を行ってください．")
                                    .styled(style -> style
                                            .withColor(Formatting.AQUA)
                                            .withUnderline(true)
                                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                                            .withHoverEvent(
                                                    new HoverEvent.ShowText(Text.literal("クリックしてブラウザで開く")))));
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null) {
                        client.execute(() -> {
                            if (client.player != null) {
                                client.player.sendMessage(msg, false);
                            }
                        });
                    }
                });
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).thenCompose(authFuture -> authFuture)
                .thenAccept(result -> {
                    if (result == null || result.token == null) {
                        throw new CompletionException(
                                new IllegalStateException("Twitch access token was not obtained"));
                    }

                    helixClient.setCredentials(result.token, oauthClient.CLIENT_ID);

                    if (result.authType == AuthType.NEW_AUTHORIZATION) {
                        MutableText msg = Text.literal("[StreamTweaks] 認証が完了しました．")
                                .formatted(Formatting.GREEN);
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null) {
                            client.execute(() -> {
                                if (client.player != null) {
                                    client.player.sendMessage(msg, false);
                                }
                            });
                        }
                    }

                    StreamTweaks.devLogger(
                            "Got Twitch access token: %s (type: %s)".formatted(result.token, result.authType));
                })
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        StreamTweaks.LOGGER.error("Failed to get Twitch access token", throwable);
                    }
                });
    }

    public CompletableFuture<String> connectToChannel(String channelLogin) {
        if (channelLogin == null || channelLogin.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("チャンネル名が指定されていません"));
        }

        String normalizedLogin = channelLogin.toLowerCase().trim();
        StreamTweaks.LOGGER.info("Connecting to channel: {}", normalizedLogin);

        return ensureAuthenticated()
                .thenCompose(ignored -> helixClient.getUserByLogin(normalizedLogin))
                .thenCompose(response -> {
                    if (response.isSuccess() && !response.users().isEmpty()) {
                        TwitchUser user = response.users().get(0);
                        String userId = user.id();

                        StreamTweaks.LOGGER.info("Successfully found user: {} (ID: {})", user.displayName(), userId);

                        return subscribeToChat(userId)
                                .thenApply(subscription -> {
                                    ConnectionState newState = new ConnectionState(normalizedLogin, user.displayName(),
                                            userId, subscription);
                                    ConnectionState previousState = connectionState.getAndSet(newState);
                                    if (previousState != null && previousState.chatSubscription() != null
                                            && !previousState.chatSubscription().equals(subscription)) {
                                        subscriptionManager.removeDesired(previousState.chatSubscription());
                                    }

                                    MinecraftClient client = MinecraftClient.getInstance();
                                    if (client != null) {
                                        MutableText msg = Text.literal("[StreamTweaks] チャンネル「")
                                                .formatted(Formatting.GREEN)
                                                .append(Text.literal(user.displayName())
                                                        .formatted(Formatting.AQUA))
                                                .append(Text.literal("」に接続しました。")
                                                        .formatted(Formatting.GREEN));

                                        client.execute(() -> {
                                            if (client.player != null) {
                                                client.player.sendMessage(msg, false);
                                            }
                                        });
                                    }
                                    return userId;
                                });
                    } else {
                        String errorMsg = "チャンネル「" + normalizedLogin + "」が見つかりませんでした";
                        StreamTweaks.LOGGER.error(errorMsg);

                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null) {
                            MutableText msg = Text.literal("[StreamTweaks] " + errorMsg)
                                    .formatted(Formatting.RED);

                            client.execute(() -> {
                                if (client.player != null) {
                                    client.player.sendMessage(msg, false);
                                }
                            });
                        }

                        return CompletableFuture.failedFuture(new RuntimeException(errorMsg));
                    }
                })
                .exceptionally(throwable -> {
                    Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                            ? throwable.getCause()
                            : throwable;
                    String detail = cause.getMessage();
                    if (detail == null || detail.isBlank()) {
                        detail = cause.getClass().getSimpleName();
                    }
                    String errorMsg = "チャンネル接続に失敗しました: " + detail;
                    StreamTweaks.LOGGER.error(errorMsg, cause);

                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null) {
                        MutableText msg = Text.literal("[StreamTweaks] " + errorMsg)
                                .formatted(Formatting.RED);

                        client.execute(() -> {
                            if (client.player != null) {
                                client.player.sendMessage(msg, false);
                            }
                        });
                    }

                    throw new RuntimeException(errorMsg, cause);
                });
    }

    public CompletableFuture<SubscriptionSpec> subscribeToChat(String broadcasterUserId) {
        if (broadcasterUserId == null || broadcasterUserId.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("ブロードキャスターユーザーIDが指定されていません"));
        }

        String normalizedBroadcasterUserId = broadcasterUserId.trim();
        StreamTweaks.LOGGER.info("Subscribing to chat for broadcaster user ID: {}", normalizedBroadcasterUserId);

        return helixClient.getCurrentUser()
                .thenCompose(response -> {
                    if (response.isSuccess() && !response.users().isEmpty()) {
                        TwitchUser currentUser = response.users().get(0);
                        String authenticatedUserId = currentUser.id();

                        StreamTweaks.devLogger("Authenticated user ID: %s (%s)"
                                .formatted(authenticatedUserId, currentUser.displayName()));

                        try {
                            SubscriptionSpec chatSubscription = new SubscriptionSpec(
                                    "channel.chat.message",
                                    "1",
                                    Map.of(
                                            "broadcaster_user_id", normalizedBroadcasterUserId,
                                            "user_id", authenticatedUserId));

                            subscriptionManager.addDesired(chatSubscription);

                            StreamTweaks.devLogger(
                                    "Chat subscription added: broadcaster=%s, user=%s".formatted(
                                            normalizedBroadcasterUserId, authenticatedUserId));

                            return CompletableFuture.completedFuture(chatSubscription);
                        } catch (Exception e) {
                            String errorMsg = "チャット購読の設定に失敗しました: " + e.getMessage();
                            StreamTweaks.LOGGER.error(errorMsg, e);
                            return CompletableFuture.<SubscriptionSpec>failedFuture(new RuntimeException(errorMsg, e));
                        }
                    } else {
                        String errorMsg = "認証されたユーザー情報の取得に失敗しました";
                        StreamTweaks.LOGGER.error(errorMsg);
                        return CompletableFuture.<SubscriptionSpec>failedFuture(new RuntimeException(errorMsg));
                    }
                })
                .exceptionally(throwable -> {
                    String errorMsg = "チャット購読に失敗しました: " + throwable.getMessage();
                    StreamTweaks.LOGGER.error(errorMsg, throwable);

                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null) {
                        MutableText msg = Text.literal("[StreamTweaks] " + errorMsg)
                                .formatted(Formatting.RED);

                        client.execute(() -> {
                            if (client.player != null) {
                                client.player.sendMessage(msg, false);
                            }
                        });
                    }

                    throw new RuntimeException(errorMsg, throwable);
                });
    }

    public void disconnect() {
        ConnectionState previousState = connectionState.getAndSet(null);
        if (previousState == null || previousState.chatSubscription() == null) {
            StreamTweaks.LOGGER.info("No active Twitch channel connection to disconnect.");

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                MutableText msg = Text.literal("[StreamTweaks] 切断できるチャンネルがありません。")
                        .formatted(Formatting.YELLOW);
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.sendMessage(msg, false);
                    }
                });
            }
            return;
        }

        subscriptionManager.removeDesired(previousState.chatSubscription());
        String channelName = previousState.displayName() != null ? previousState.displayName() : previousState.login();
        StreamTweaks.LOGGER.info("Disconnected from Twitch channel: {}", channelName);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            MutableText msg = Text.literal("[StreamTweaks] チャンネル「")
                    .formatted(Formatting.YELLOW)
                    .append(Text.literal(channelName).formatted(Formatting.AQUA))
                    .append(Text.literal("」から切断しました。")
                            .formatted(Formatting.YELLOW));

            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(msg, false);
                }
            });
        }
    }

    private void handleEventSubNotification(EventNotification notification) {
        if (!"channel.chat.message".equals(notification.type())) {
            return;
        }

        JsonObject event;
        try {
            event = JsonParser.parseString(notification.json()).getAsJsonObject();
        } catch (Exception e) {
            StreamTweaks.LOGGER.error("channel.chat.message 通知の解析に失敗しました", e);
            return;
        }

        ConnectionState state = connectionState.get();
        if (state == null) {
            return;
        }

        String broadcasterId = optString(event, "broadcaster_user_id");
        if (broadcasterId != null && !broadcasterId.equals(state.broadcasterUserId())) {
            return;
        }

        JsonObject messageObj = event.has("message") && event.get("message").isJsonObject()
                ? event.getAsJsonObject("message")
                : null;

        String text = optString(messageObj, "text");
        if (text == null) {
            text = "";
        }

        String displayName = firstNonBlank(optString(event, "chatter_user_name"),
                optString(event, "chatter_user_login"),
                "Unknown");

        StreamTweaks.devLogger("Twitch chat message from %s: %s".formatted(displayName, text));

        boolean isAction = "action".equalsIgnoreCase(optString(event, "message_type"));
        if (!isAction && messageObj != null && messageObj.has("is_action")) {
            try {
                isAction = messageObj.get("is_action").getAsBoolean();
            } catch (ClassCastException | IllegalStateException ignored) {
            }
        }

        String rawColor = optString(event, "color");
        TextColor twitchColor = adjustForReadability(parseTwitchColor(rawColor));

        MutableText chatLine = Text.literal("[Twitch] ").formatted(Formatting.LIGHT_PURPLE);

        MutableText nameComponent = Text.literal(displayName);
        if (twitchColor != null) {
            nameComponent = nameComponent.styled(style -> style.withColor(twitchColor));
        } else {
            nameComponent = nameComponent.formatted(Formatting.GOLD);
        }

        chatLine.append(nameComponent);

        MutableText separator = Text.literal(isAction ? " " : ": ").formatted(Formatting.GRAY);
        chatLine.append(separator);

        MutableText body = Text.literal(text).formatted(Formatting.WHITE);
        if (isAction) {
            body = body.formatted(Formatting.ITALIC);
            if (twitchColor != null) {
                body = body.styled(style -> style.withColor(twitchColor));
            }
        }

        chatLine.append(body);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        final MutableText finalLine = chatLine;
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(finalLine, false);
            }
        });
    }

    private static String optString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsString();
        } catch (ClassCastException | IllegalStateException e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static TextColor parseTwitchColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return null;
        }
        String normalized = hex.startsWith("#") ? hex.substring(1) : hex;
        if (normalized.length() != 6) {
            return null;
        }
        try {
            int rgb = Integer.parseUnsignedInt(normalized, 16);
            return TextColor.fromRgb(rgb);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static TextColor adjustForReadability(TextColor color) {
        if (color == null) {
            return null;
        }

        int rgb = color.getRgb();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double target = 160.0;
        if (luminance >= target) {
            return color;
        }

        double blend = Math.min(0.7, (target - luminance) / target);
        int nr = (int) Math.round(r + (255 - r) * blend);
        int ng = (int) Math.round(g + (255 - g) * blend);
        int nb = (int) Math.round(b + (255 - b) * blend);

        return TextColor.fromRgb((nr << 16) | (ng << 8) | nb);
    }
}
