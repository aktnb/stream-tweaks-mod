package org.etwas.streamtweaks.twitch.eventsub;

import org.etwas.streamtweaks.utils.BackoffPolicy;

public interface WebSocketClient {
    void connect(String url);

    void close();

    boolean isOpen();

    void setListener(Listener l);

    void allowReconnect(boolean allow);

    void setBackoff(BackoffPolicy policy);

    interface Listener {
        void onWelcome(SessionInfo info);

        void onKeepalive();

        void onReconnect(String reconnectUrl);

        void onNotification(String type, String json);

        void onRevocation(String type, String reason);

        void onClosed(int code, String reason);

        void onError(Throwable t);
    }
}
