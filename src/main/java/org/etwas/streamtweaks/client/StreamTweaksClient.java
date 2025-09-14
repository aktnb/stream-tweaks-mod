package org.etwas.streamtweaks.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.etwas.streamtweaks.auth.AuthResult;
import org.etwas.streamtweaks.auth.TwitchOAuthClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static org.etwas.streamtweaks.StreamTweaks.MOD_ID;

public class StreamTweaksClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player == null) return;
            checkToken(client);
        });
    }

    private void checkToken(MinecraftClient client) {
        try {
            var oauth = new TwitchOAuthClient();
            oauth.getAccessToken(new String[]{"user:read:chat"}, (url) -> {
                MutableText msg = Text.literal("[StreamTweaks] 認証が必要です．")
                        .formatted(Formatting.YELLOW)
                        .append(
                                Text.literal("ここをクリックして認証を行ってください．")
                                        .styled(style -> style
                                                .withColor(Formatting.AQUA)
                                                .withUnderline(true)
                                                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                                                .withHoverEvent(new HoverEvent.ShowText(Text.literal("クリックしてブラウザで開く")))
                                        )
                        );
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.sendMessage(msg, false);
                    }
                });
            }).thenAccept(result -> {
                if (result != null && result.token != null) {
                    if (result.authType == AuthResult.AuthType.NEW_AUTHORIZATION) {
                        MutableText msg = Text.literal("[StreamTweaks] 認証が完了しました．")
                                .formatted(Formatting.GREEN);
                        client.execute(() -> {
                            if (client.player != null) {
                                client.player.sendMessage(msg, false);
                            }
                        });
                    }
                    LOGGER.info("Got Twitch access token: {} (type: {})", result.token, result.authType);
                } else {
                    LOGGER.warn("Twitch authorization was cancelled or failed.");
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error during Twitch authorization", e);
        }
    }
}
