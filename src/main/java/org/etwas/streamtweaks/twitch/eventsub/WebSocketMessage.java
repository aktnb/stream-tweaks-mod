package org.etwas.streamtweaks.twitch.eventsub;

import java.time.Instant;

import com.google.gson.JsonObject;

public record WebSocketMessage(Metadata metadata, JsonObject payload) {
    public record Metadata(String messageId, String messageType, Instant messageTimestamp) {
    }
}
