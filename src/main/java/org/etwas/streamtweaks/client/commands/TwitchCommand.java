package org.etwas.streamtweaks.client.commands;

import org.etwas.streamtweaks.twitch.service.TwitchService;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class TwitchCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                ClientCommandManager.literal("twitch")
                        .then(ClientCommandManager.literal("connect")
                                .then(ClientCommandManager.argument("login", StringArgumentType.word())
                                        .executes(ctx -> TwitchCommand.connect(ctx))))
                        .then(ClientCommandManager.literal("disconnect")
                                .executes(ctx -> TwitchCommand.disconnect(ctx))));
    }

    private static int connect(CommandContext<FabricClientCommandSource> context) {
        String login = StringArgumentType.getString(context, "login");

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal("Trying to connect Twitch channel: " + login), false);
        }

        TwitchService.getInstance().connectToChannel(login);

        return 1;
    }

    private static int disconnect(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal("Disconnecting Twitch channel"), false);
        }

        TwitchService.getInstance().disconnect();

        return 1;
    }
}
