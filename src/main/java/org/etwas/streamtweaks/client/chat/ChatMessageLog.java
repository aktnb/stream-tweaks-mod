package org.etwas.streamtweaks.client.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ChatMessageLog {
    private static final int DEFAULT_CAPACITY = 200;
    private static final ChatMessageLog INSTANCE = new ChatMessageLog(DEFAULT_CAPACITY);

    private final Deque<ChatMessage> messages;
    private final Map<String, ChatMessage> messagesById;
    private int capacity;

    public static ChatMessageLog getInstance() {
        return INSTANCE;
    }

    private ChatMessageLog(int capacity) {
        this.capacity = capacity;
        this.messages = new ArrayDeque<>(capacity);
        this.messagesById = new HashMap<>();
    }

    public synchronized void setCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        trimToCapacity();
    }

    public synchronized void add(ChatMessage message) {
        Objects.requireNonNull(message, "message");

        if (message.messageId() != null) {
            ChatMessage previous = messagesById.remove(message.messageId());
            if (previous != null) {
                messages.remove(previous);
            }
        }

        messages.addLast(message);

        if (message.messageId() != null) {
            messagesById.put(message.messageId(), message);
        }

        trimToCapacity();
    }

    public synchronized boolean removeById(String messageId) {
        if (messageId == null) {
            return false;
        }
        ChatMessage removed = messagesById.remove(messageId);
        if (removed == null) {
            return false;
        }
        boolean updated = messages.remove(removed);
        return updated;
    }

    public synchronized void clearSource(ChatMessage.Source source) {
        if (source == null) {
            return;
        }

        messages.removeIf(message -> {
            if (message.source() != source) {
                return false;
            }
            if (message.messageId() != null) {
                messagesById.remove(message.messageId());
            }
            return true;
        });
    }

    public synchronized List<ChatMessage> snapshot() {
        return new ArrayList<>(messages);
    }

    private void trimToCapacity() {
        while (messages.size() > capacity) {
            ChatMessage removed = messages.removeFirst();
            if (removed.messageId() != null) {
                messagesById.remove(removed.messageId());
            }
        }
    }
}
