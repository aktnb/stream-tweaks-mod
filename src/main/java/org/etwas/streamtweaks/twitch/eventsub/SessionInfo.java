package org.etwas.streamtweaks.twitch.eventsub;

import java.time.Instant;

public final record SessionInfo(
                String id,
                String status,
                int keepaliveTimeoutSeconds,
                String reconnectUrl,
                Instant connectedAt) {
}