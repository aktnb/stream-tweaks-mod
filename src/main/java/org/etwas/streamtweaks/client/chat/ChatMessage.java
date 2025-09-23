package org.etwas.streamtweaks.client.chat;

import java.time.Instant;
import java.util.Objects;

import net.minecraft.text.TextColor;

public record ChatMessage(
        String messageId,
        String chatterUserId,
        String chatterLogin,
        String chatterDisplayName,
        String body,
        boolean action,
        TextColor color,
        Instant receivedAt,
        Source source) {

    public ChatMessage {
        Objects.requireNonNull(source, "source");
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

    public enum Source {
        TWITCH
    }
}
