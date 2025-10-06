package org.etwas.streamtweaks.utils;

import java.net.URI;
import java.util.function.Supplier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ChatMessages {
    private static final String STREAM_TWEAKS_PREFIX = "[StreamTweaks] ";
    private static final Formatting STREAM_TWEAKS_COLOR = Formatting.DARK_AQUA;

    private ChatMessages() {
    }

    public static MutableText streamTweaks() {
        return Text.literal(STREAM_TWEAKS_PREFIX).formatted(STREAM_TWEAKS_COLOR);
    }

    public static MutableText streamTweaks(String message) {
        return streamTweaks(Text.literal(message));
    }

    public static MutableText streamTweaks(Text content) {
        MutableText base = streamTweaks();
        if (content != null) {
            base.append(content);
        }
        return base;
    }

    public static MutableText textWithLink(String text, URI link, String hoverText) {
        return Text.literal(text)
                .styled(style -> style
                        .withColor(Formatting.AQUA)
                        .withUnderline(true)
                        .withClickEvent(new ClickEvent.OpenUrl(link))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal(hoverText))));
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
