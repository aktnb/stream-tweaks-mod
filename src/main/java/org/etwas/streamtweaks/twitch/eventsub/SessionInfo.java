package org.etwas.streamtweaks.twitch.eventsub;

import java.time.Instant;

public final record SessionInfo(
        String id,
        int keepaliveTimeoutSeconds,
        String status,
        Instant connectedAt,
        String reconnectUrl) {
}