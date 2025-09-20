package org.etwas.streamtweaks.twitch.service;

import java.net.URI;

import org.etwas.streamtweaks.StreamTweaks;
import org.etwas.streamtweaks.twitch.api.HelixClient;
import org.etwas.streamtweaks.twitch.auth.AuthResult.AuthType;
import org.etwas.streamtweaks.twitch.auth.TwitchOAuthClient;
import org.etwas.streamtweaks.twitch.eventsub.EventSubManager;

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
}
