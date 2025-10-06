package org.etwas.streamtweaks.utils;

import java.util.function.Supplier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class ChatMessageUtil {
    private ChatMessageUtil() {
    }

    public static void sendMessage(Supplier<MutableText> supplier) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            Text message = supplier.get();
            if (message != null)
                client.execute(() -> client.player.sendMessage(message, false));
        }
    }

    public static void sendMessage(MutableText message) {
        sendMessage(() -> message);
    }
}
