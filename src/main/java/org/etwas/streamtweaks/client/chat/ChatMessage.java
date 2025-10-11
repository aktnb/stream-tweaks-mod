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
        Fragment[] fragments,
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

    public sealed interface Fragment permits TextFragment, EmoteFragment, MentionFragment, CheermoteFragment {
        public String getString();
    }

    public record TextFragment(String text) implements Fragment {

        @Override
        public String getString() {
            return text();
        }
    }

    public record EmoteFragment(String emoteId, String emoteSetId) implements Fragment {

        @Override
        public String getString() {
            return emoteId();
        }
    }

    public record MentionFragment(String name, int startIndex, int endIndex) implements Fragment {

        @Override
        public String getString() {
            return name();
        }
    }

    public record CheermoteFragment(String prefix, int amount, int startIndex, int endIndex) implements Fragment {

        @Override
        public String getString() {
            return prefix() + amount();
        }
    }
}
