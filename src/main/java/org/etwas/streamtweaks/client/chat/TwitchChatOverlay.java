package org.etwas.streamtweaks.client.chat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

public final class TwitchChatOverlay {
    private static final int MAX_VISIBLE_LINES = 100;
    private static final long FADE_START_MILLIS = 10_000L;
    private static final long FADE_END_MILLIS = 12_000L;

    private static final TwitchChatOverlay INSTANCE = new TwitchChatOverlay();

    private TwitchChatOverlay() {
    }

    public static TwitchChatOverlay getInstance() {
        return INSTANCE;
    }

    public void render(ChatHud chatHud, DrawContext context, int ticks, boolean focused) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        if (client.options.hudHidden) {
            return;
        }

        Object visibility = client.options.getChatVisibility().getValue();
        if (visibility instanceof Enum<?> enumVisibility && "HIDDEN".equals(enumVisibility.name())) {
            return;
        }

        List<ChatMessage> messages = ChatMessageLog.getInstance().snapshot();
        if (messages.isEmpty()) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        float scale = (float) chatHud.getChatScale();
        double lineSpacing = client.options.getChatLineSpacing().getValue();
        int chatWidth = MathHelper.ceil(chatHud.getWidth() / scale);
        int chatHeight = MathHelper.ceil(chatHud.getHeight() / scale);
        double lineAdvance = 9.0 + lineSpacing * 9.0;
        float opacity = client.options.getChatOpacity().getValue().floatValue();

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(4.0F, 8.0F);
        context.getMatrices().scale(scale, scale);

        int renderedLineCount = 0;
        int maxLines = Math.min(MAX_VISIBLE_LINES, (int) (chatHeight / Math.max(lineAdvance, 1.0)));

        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (renderedLineCount >= maxLines) {
                break;
            }

            float lineAlpha = computeAlpha(message.receivedAt(), opacity, focused);
            if (lineAlpha <= 0.01F) {
                continue;
            }

            MutableText line = buildLine(message);
            List<OrderedText> wrapped = textRenderer.wrapLines(line, chatWidth);
            for (int j = wrapped.size() - 1; j >= 0; j--) {
                if (renderedLineCount >= maxLines) {
                    break;
                }

                OrderedText orderedText = wrapped.get(j);
                int y = MathHelper.floor(-renderedLineCount * lineAdvance);
                int backgroundColor = (int) (lineAlpha * 255.0F) << 24;
                context.fill(-4, y - 2, chatWidth + 4, y + 9, backgroundColor);
                context.drawTextWithShadow(textRenderer, orderedText, 0, y,
                        ((int) (lineAlpha * 255.0F) << 24) | 0xFFFFFF);

                renderedLineCount++;
            }
        }

        context.getMatrices().popMatrix();
    }

    private MutableText buildLine(ChatMessage message) {
        MutableText prefix = Text.literal("[Twitch] ").formatted(Formatting.LIGHT_PURPLE);

        String displayName = message.chatterDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = message.chatterLogin() != null && !message.chatterLogin().isBlank()
                    ? message.chatterLogin()
                    : "Unknown";
        }

        MutableText nameComponent = Text.literal(displayName);
        TextColor color = message.color();
        if (color != null) {
            nameComponent = nameComponent.styled(style -> style.withColor(color));
        } else {
            nameComponent = nameComponent.formatted(Formatting.GOLD);
        }

        prefix.append(nameComponent);

        MutableText separator = Text.literal(message.action() ? " " : ": ").formatted(Formatting.GRAY);
        prefix.append(separator);

        String bodyText = message.body() != null ? message.body() : "";
        MutableText body = Text.literal(bodyText).formatted(Formatting.WHITE);
        if (message.action()) {
            body = body.formatted(Formatting.ITALIC);
            if (color != null) {
                body = body.styled(style -> style.withColor(color));
            }
        }

        prefix.append(body);
        return prefix;
    }

    private float computeAlpha(Instant timestamp, float opacity, boolean focused) {
        if (timestamp == null) {
            return opacity;
        }

        long ageMillis = Duration.between(timestamp, Instant.now()).toMillis();
        if (focused) {
            return opacity;
        }

        if (ageMillis >= FADE_END_MILLIS) {
            return 0.0F;
        }

        if (ageMillis <= FADE_START_MILLIS) {
            return opacity;
        }

        float fadeRange = (float) (FADE_END_MILLIS - FADE_START_MILLIS);
        float remaining = (float) (FADE_END_MILLIS - ageMillis);
        float alpha = opacity * MathHelper.clamp(remaining / fadeRange, 0.0F, 1.0F);
        return alpha;
    }
}
