package org.etwas.streamtweaks.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import org.etwas.streamtweaks.client.commands.TwitchCommand;
import org.etwas.streamtweaks.config.StreamTweaksConfig;
import org.etwas.streamtweaks.twitch.service.TwitchService;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

public class StreamTweaksClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AutoConfig.register(StreamTweaksConfig.class, GsonConfigSerializer::new);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player == null)
                return;
            
            // Check for auto-authentication when joining world
            try {
                StreamTweaksConfig config = AutoConfig.getConfigHolder(StreamTweaksConfig.class).getConfig();
                if (config.autoAuthOnWorldJoin) {
                    TwitchService twitchService = TwitchService.getInstance();
                    if (!twitchService.isAuthenticated()) {
                        // Start authentication process automatically
                        twitchService.ensureAuthenticated();
                    }
                }
            } catch (Exception e) {
                // Silently ignore errors to prevent crashes on world join
                // Logging would be helpful for debugging but not critical for the user experience
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            TwitchCommand.register(dispatcher);
        });
    }
}
