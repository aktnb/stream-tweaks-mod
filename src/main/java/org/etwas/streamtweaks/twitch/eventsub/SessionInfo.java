package org.etwas.streamtweaks.twitch.eventsub;

import java.time.Duration;
import java.time.Instant;

public final record SessionInfo(String sessionId, Duration keepaliveTimeout, String status, Instant connectedAt,
                String reconnectUrl) {
}