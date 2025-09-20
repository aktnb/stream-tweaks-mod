package org.etwas.streamtweaks.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.etwas.streamtweaks.twitch.service.TwitchService;

public class StreamTweaksClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player == null)
                return;
            TwitchService.getInstance().ensureAuthenticated();
        });
    }
}
