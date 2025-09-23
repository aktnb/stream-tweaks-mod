package org.etwas.streamtweaks.client.chat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.etwas.streamtweaks.mixin.ChatHudAccessor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
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
    private static final float BACKGROUND_ALPHA_MULTIPLIER = 0.5F;
    private static final float VANILLA_ALPHA_CUTOFF = 1.0E-5F;

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

        int vanillaRenderedLineCount = computeVanillaRenderedLineCount(chatHud, ticks, focused);
        int scaledHeight = context.getScaledWindowHeight();
        int vanillaBottomY = MathHelper.floor((scaledHeight - 40) / scale);
        double overlayBottomY = vanillaRenderedLineCount > 0
                ? vanillaBottomY - vanillaRenderedLineCount * lineAdvance
                : vanillaBottomY;
        double baseOffset = overlayBottomY - 9.0;

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(4.0F, 0.0F);

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
                int y = MathHelper.floor(-renderedLineCount * lineAdvance + baseOffset);
                int backgroundAlpha = MathHelper.ceil(MathHelper.clamp(lineAlpha, 0.0F, 1.0F)
                        * 255.0F * BACKGROUND_ALPHA_MULTIPLIER);
                int backgroundColor = backgroundAlpha << 24;
                context.fill(-4, y - 0, chatWidth + 4, y + 9, backgroundColor);
                context.drawTextWithShadow(textRenderer, orderedText, 0, y,
                        ((int) (lineAlpha * 255.0F) << 24) | 0xFFFFFF);

                renderedLineCount++;
            }
        }

        context.getMatrices().popMatrix();
    }

    private int computeVanillaRenderedLineCount(ChatHud chatHud, int currentTick, boolean focused) {
        if (!(chatHud instanceof ChatHudAccessor accessor)) {
            return 0;
        }

        List<ChatHudLine.Visible> visibleMessages = accessor.streamTweaks$getVisibleMessages();
        if (visibleMessages.isEmpty()) {
            return 0;
        }

        int clampedScroll = MathHelper.clamp(accessor.streamTweaks$getScrolledLines(), 0, visibleMessages.size());
        int visibleLineCap = Math.min(chatHud.getVisibleLineCount(), visibleMessages.size());
        int consideredLines = Math.min(visibleMessages.size() - clampedScroll, visibleLineCap);
        if (consideredLines <= 0) {
            return 0;
        }

        int renderedLines = 0;
        for (int index = consideredLines - 1; index >= 0; index--) {
            ChatHudLine.Visible line = visibleMessages.get(index + clampedScroll);
            int age = currentTick - line.addedTime();
            float lineAlpha = focused ? 1.0F : (float) getVanillaMessageOpacityMultiplier(age);
            if (lineAlpha > VANILLA_ALPHA_CUTOFF) {
                renderedLines++;
            }
        }

        return renderedLines;
    }

    private static double getVanillaMessageOpacityMultiplier(int age) {
        double value = age / 200.0;
        value = 1.0 - value;
        value *= 10.0;
        value = MathHelper.clamp(value, 0.0, 1.0);
        return value * value;
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
