package org.etwas.streamtweaks.client.commands;

import java.util.concurrent.CompletionException;

import org.etwas.streamtweaks.StreamTweaks;
import org.etwas.streamtweaks.client.ui.MessageTexts;
import org.etwas.streamtweaks.twitch.service.TwitchService;
import org.etwas.streamtweaks.utils.ChatMessages;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

        TwitchService.getInstance()
                .connectToChannel(login)
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

                    ChatMessages.sendMessage(() -> ChatMessages.streamTweaks(
                            Text.literal(errorMsg).formatted(Formatting.RED)));

                    throw new RuntimeException(errorMsg, cause);
                });

        return 1;
    }

    private static int disconnect(CommandContext<FabricClientCommandSource> context) {
        ChatMessages.sendMessage(() -> MessageTexts.disconnecting());

        TwitchService.getInstance().disconnect();

        return 1;
    }
}
