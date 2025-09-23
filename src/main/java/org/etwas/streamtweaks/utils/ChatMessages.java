package org.etwas.streamtweaks.utils;

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
}
