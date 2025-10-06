package org.etwas.streamtweaks.client.ui;

import java.net.URI;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class MessageTexts {
    private static final String STREAM_TWEAKS_PREFIX = "[StreamTweaks] ";
    private static final Formatting STREAM_TWEAKS_COLOR = Formatting.DARK_AQUA;
    private static final Formatting INFO_COLOR = Formatting.GREEN;
    private static final Formatting PRIMARY_COLOR = Formatting.AQUA;
    private static final Formatting WARNING_COLOR = Formatting.YELLOW;
    private static final Formatting ERROR_COLOR = Formatting.RED;

    private static MutableText streamTweaks() {
        return Text.literal(STREAM_TWEAKS_PREFIX).formatted(STREAM_TWEAKS_COLOR);
    }

    private static MutableText streamTweaks(Text content) {
        MutableText base = streamTweaks();
        if (content != null) {
            base.append(content);
        }
        return base;
    }

    private static MutableText primaryText(String message) {
        return Text.literal(message).formatted(PRIMARY_COLOR);
    }

    public static MutableText promptAuthentication(URI authUri) {
        return streamTweaks().append(Text.translatable("message.stream-tweaks.promptAuthentication",
                Text.translatable("message.streamTweaks.here")
                        .styled(style -> style
                                .withColor(PRIMARY_COLOR)
                                .withUnderline(true)
                                .withClickEvent(new ClickEvent.OpenUrl(authUri))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Text.translatable("message.stream-tweaks.authenticationLinkHover")
                                                .formatted(Formatting.GRAY)))))
                .formatted(WARNING_COLOR));
    }

    public static MutableText authenticated() {
        return streamTweaks(Text.translatable("message.stream-tweaks.authenticationSuccess")
                .formatted(INFO_COLOR));
    }

    public static MutableText channelConnecting(String channelName) {
        return streamTweaks().append(Text.translatable("message.stream-tweaks.connecting", primaryText(channelName))
                .formatted(WARNING_COLOR));
    }

    public static MutableText channelConnecting() {
        return streamTweaks().append(Text.translatable("message.stream-tweaks.connectingSelf")
                .formatted(WARNING_COLOR));
    }

    public static MutableText channelConnected(String channelName) {
        return streamTweaks().append(Text.translatable("message.stream-tweaks.connected", primaryText(channelName))
                .formatted(INFO_COLOR));
    }

    public static MutableText channelConnectionFailed() {
        return streamTweaks().append(Text.translatable("message.stream-tweaks.connectionFailed")
                .formatted(ERROR_COLOR));
    }

    public static MutableText channelNotFound(String channelName) {
        return streamTweaks().append(Text.translatable("message.stream-tweaks.channelNotFound", channelName)
                .formatted(ERROR_COLOR));
    }

    public static MutableText disconnecting() {
        return streamTweaks(Text.translatable("message.stream-tweaks.disconnecting")
                .formatted(WARNING_COLOR));
    }

    public static MutableText disconnected(String channelName) {
        return streamTweaks().append(Text.translatable("message.stream-tweaks.disconnected", primaryText(channelName))
                .formatted(WARNING_COLOR));
    }

    public static MutableText alreadyDisconnected() {
        return streamTweaks().append(Text.translatable("message.stream-tweaks.alreadyDisconnected")
                .formatted(ERROR_COLOR));
    }
}
