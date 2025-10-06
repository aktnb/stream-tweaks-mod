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

    private static MutableText infoText(String message) {
        return Text.literal(message).formatted(INFO_COLOR);
    }

    private static MutableText primaryText(String message) {
        return Text.literal(message).formatted(PRIMARY_COLOR);
    }

    private static MutableText warningText(String message) {
        return Text.literal(message).formatted(WARNING_COLOR);
    }

    private static MutableText errorText(String message) {
        return Text.literal(message).formatted(ERROR_COLOR);
    }

    private static MutableText textWithLink(String text, URI link, String hoverText) {
        return primaryText(text)
                .styled(style -> style
                        .withUnderline(true)
                        .withClickEvent(new ClickEvent.OpenUrl(link))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal(hoverText))));
    }

    public static MutableText disconnecting() {
        return streamTweaks(warningText("切断中..."));
    }

    public static MutableText promptAuthentication(URI authUri) {
        return streamTweaks(
                warningText("認証が必要です．"))
                .append(MessageTexts.textWithLink("ここ", authUri, "クリックしてブラウザで開く"))
                .append(warningText("をクリックして，ブラウザで認証を行ってください．"));
    }

    public static MutableText authenticated() {
        return streamTweaks(infoText("認証に成功しました！"));
    }

    public static MutableText channelConnecting(String channelName) {
        return streamTweaks(
                warningText("チャンネル「"))
                .append(primaryText(channelName))
                .append(warningText("」に接続中..."));
    }

    public static MutableText channelConnecting() {
        return streamTweaks(
                warningText("認証済みユーザーのチャンネルに接続中..."));
    }

    public static MutableText channelConnected(String channelName) {
        return streamTweaks(
                infoText("チャンネル「")
                        .append(primaryText(channelName))
                        .append(infoText("」に接続しました。")));
    }

    public static MutableText channelConnectionFailed() {
        return streamTweaks(errorText("チャンネルへの接続に失敗しました。"));
    }

    public static MutableText channelNotFound(String channelName) {
        return streamTweaks(
                errorText("チャンネル「" + channelName + "が見つかりません。"));
    }

    public static MutableText disconnected(String channelName) {
        return streamTweaks(
                warningText("チャンネル「"))
                .append(primaryText(channelName))
                .append(warningText("」から切断しました。"));
    }

    public static MutableText alreadyDisconnected() {
        return streamTweaks(
                warningText("既に切断されています。"));
    }
}
