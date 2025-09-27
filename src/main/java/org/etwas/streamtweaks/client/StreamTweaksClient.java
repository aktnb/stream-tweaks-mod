package org.etwas.streamtweaks.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import org.etwas.streamtweaks.client.commands.TwitchCommand;
import org.etwas.streamtweaks.config.StreamTweaksConfig;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

public class StreamTweaksClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AutoConfig.register(StreamTweaksConfig.class, GsonConfigSerializer::new);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player == null)
                return;
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            TwitchCommand.register(dispatcher);
        });
    }
}
