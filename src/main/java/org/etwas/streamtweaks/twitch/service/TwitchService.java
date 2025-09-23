package org.etwas.streamtweaks.twitch.service;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.etwas.streamtweaks.StreamTweaks;
import org.etwas.streamtweaks.twitch.api.HelixClient;
import org.etwas.streamtweaks.twitch.api.HelixClient.TwitchUser;
import org.etwas.streamtweaks.twitch.auth.AuthResult.AuthType;
import org.etwas.streamtweaks.twitch.auth.TwitchOAuthClient;
import org.etwas.streamtweaks.twitch.eventsub.EventSubManager;
import org.etwas.streamtweaks.twitch.eventsub.SubscriptionSpec;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
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

    public void ensureAuthenticated() {
        try {
            oauthClient.getAccessToken(new String[] { "user:read:chat" }, url -> {
                MutableText msg = Text.literal("[StreamTweaks] 認証が必要です．")
                        .formatted(Formatting.YELLOW)
                        .append(
                                Text.literal("ここをクリックして認証を行ってください．")
                                        .styled(style -> style
                                                .withColor(Formatting.AQUA)
                                                .withUnderline(true)
                                                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                                                .withHoverEvent(
                                                        new HoverEvent.ShowText(Text.literal("クリックしてブラウザで開く")))));
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.sendMessage(msg, false);
                    }
                });
            })
                    .thenAccept(result -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (result != null && result.token != null) {
                            helixClient.setCredentials(result.token, oauthClient.CLIENT_ID);

                            if (result.authType == AuthType.NEW_AUTHORIZATION) {
                                MutableText msg = Text.literal("[StreamTweaks] 認証が完了しました．")
                                        .formatted(Formatting.GREEN);
                                client.execute(() -> {
                                    if (client.player != null) {
                                        client.player.sendMessage(msg, false);
                                    }
                                });
                            }
                            StreamTweaks.LOGGER.info("Got Twitch access token: {} (type: {})", result.token,
                                    result.authType);
                        }
                    });
        } catch (Exception e) {
            StreamTweaks.LOGGER.error("Failed to get Twitch access token", e);
        }
    }

    public CompletableFuture<String> connectToChannel(String channelLogin) {
        if (channelLogin == null || channelLogin.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("チャンネル名が指定されていません"));
        }

        String normalizedLogin = channelLogin.toLowerCase().trim();
        StreamTweaks.LOGGER.info("Connecting to channel: {}", normalizedLogin);

        ensureAuthenticated();

        return helixClient.getUserByLogin(normalizedLogin)
                .thenCompose(response -> {
                    if (response.isSuccess() && !response.users().isEmpty()) {
                        TwitchUser user = response.users().get(0);
                        String userId = user.id();

                        StreamTweaks.LOGGER.info("Successfully found user: {} (ID: {})", user.displayName(), userId);

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

                        return subscribeToChat(userId)
                                .thenApply(subscription -> {
                                    ConnectionState newState = new ConnectionState(normalizedLogin, user.displayName(),
                                            userId, subscription);
                                    ConnectionState previousState = connectionState.getAndSet(newState);
                                    if (previousState != null && previousState.chatSubscription() != null
                                            && !previousState.chatSubscription().equals(subscription)) {
                                        subscriptionManager.removeDesired(previousState.chatSubscription());
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
                    String errorMsg = "チャンネル接続に失敗しました: " + throwable.getMessage();
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

                        StreamTweaks.LOGGER.info("Authenticated user ID: {} ({})", authenticatedUserId,
                                currentUser.displayName());

                        try {
                            SubscriptionSpec chatSubscription = new SubscriptionSpec(
                                    "channel.chat.message",
                                    "1",
                                    Map.of(
                                            "broadcaster_user_id", normalizedBroadcasterUserId,
                                            "user_id", authenticatedUserId));

                            subscriptionManager.addDesired(chatSubscription);

                            StreamTweaks.LOGGER.info("Chat subscription added: broadcaster={}, user={}",
                                    normalizedBroadcasterUserId, authenticatedUserId);

                            MinecraftClient client = MinecraftClient.getInstance();
                            if (client != null) {
                                MutableText msg = Text.literal("[StreamTweaks] チャット監視を開始しました。")
                                        .formatted(Formatting.GREEN);

                                client.execute(() -> {
                                    if (client.player != null) {
                                        client.player.sendMessage(msg, false);
                                    }
                                });
                            }

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
}
