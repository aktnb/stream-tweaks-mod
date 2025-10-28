package org.etwas.streamtweaks.twitch.eventsub;

import org.etwas.streamtweaks.utils.GsonUtils;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

public class WebSocketMessageTest {
    Gson gson = GsonUtils.getBuilder().create();

    @Test
    public void testDeserializeWelcomeMessage() {
        String json = """
                {
                    "metadata": {
                        "message_id": "6aac3773-20a1-43d2-9cd7-c46418e4e828",
                        "message_type": "session_welcome",
                        "message_timestamp": "2025-10-28T16:07:42.572178332Z"
                    },
                    "payload": {
                        "session": {
                            "id": "AgoQCVNdyAI6TUik0Ou0JJ70IBIGY2VsbC1h",
                            "status": "connected",
                            "connected_at": "2025-10-28T16:07:42.568236768Z",
                            "keepalive_timeout_seconds": 10,
                            "reconnect_url": null,
                            "recovery_url": null
                        }
                    }
                }
                """;

        WebSocketMessage message = gson.fromJson(json, WebSocketMessage.class);
        SessionInfo session = gson.fromJson(
                message.payload().getAsJsonObject("session"),
                SessionInfo.class);

        assert message.metadata().messageId().equals("6aac3773-20a1-43d2-9cd7-c46418e4e828");
        assert message.metadata().messageType().equals("session_welcome");
        assert message.metadata().messageTimestamp().toString().equals("2025-10-28T16:07:42.572178332Z");

        assert session.id().equals("AgoQCVNdyAI6TUik0Ou0JJ70IBIGY2VsbC1h");
        assert session.status().equals("connected");
        assert session.connectedAt().toString().equals("2025-10-28T16:07:42.568236768Z");
        assert session.keepaliveTimeoutSeconds() == 10;
        assert session.reconnectUrl() == null;
    }
}
