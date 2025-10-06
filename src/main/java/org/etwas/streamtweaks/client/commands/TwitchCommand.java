package org.etwas.streamtweaks.client.commands;

import org.etwas.streamtweaks.twitch.service.TwitchService;
import org.etwas.streamtweaks.utils.ChatMessages;

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
                                .executes(TwitchCommand::connect)
                                .then(ClientCommandManager.argument("login", StringArgumentType.word())
                                        .executes(TwitchCommand::connectWithLogin)))
                        .then(ClientCommandManager.literal("disconnect")
                                .executes(ctx -> TwitchCommand.disconnect(ctx))));
    }

    private static int connect(CommandContext<FabricClientCommandSource> context) {
        return connect(context, null);
    }

    private static int connectWithLogin(CommandContext<FabricClientCommandSource> context) {
        String login = StringArgumentType.getString(context, "login");
        return connect(context, login);
    }

    private static int connect(CommandContext<FabricClientCommandSource> context, String login) {
        ChatMessages.sendMessage(() -> {
            Text message;
            if (login == null || login.isBlank()) {
                message = ChatMessages.streamTweaks("認証済みユーザーのチャンネルへの接続を試みます。");
            } else {
                message = ChatMessages.streamTweaks("チャンネル「" + login + "」への接続を試みます。");
            }
            return message.copy();
        });

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
