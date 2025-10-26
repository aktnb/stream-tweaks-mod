package org.etwas.streamtweaks.twitch.eventsub;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import org.etwas.streamtweaks.StreamTweaks;
import org.etwas.streamtweaks.twitch.api.HelixClient;
import org.etwas.streamtweaks.utils.KeepaliveMonitor;
import org.etwas.streamtweaks.utils.ThreadPools;

public final class EventSubManager implements WebSocketClient.Listener, KeepaliveMonitor.Handler {
    private static final String DEFAULT_EVENTSUB_URL = "wss://eventsub.wss.twitch.tv/ws";
    private final String eventSubUrl;

    private final WebSocketClient ws = new TwitchWebSocketClient();
    private final Set<SubscriptionSpec> desired = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = ThreadPools.singleScheduler("eventsub-scheduler");
    private final KeepaliveMonitor keepalive = new KeepaliveMonitor(this, java.time.Duration.ofSeconds(5), scheduler);
    private final Map<SubscriptionSpec, String> subscriptionIds = new ConcurrentHashMap<>();
    private final HelixClient helix;
    private volatile Consumer<EventNotification> notificationHandler;

    private volatile String sessionId;

    public EventSubManager(HelixClient helix) {
        this(helix, DEFAULT_EVENTSUB_URL);
    }

    public EventSubManager(HelixClient helix, String eventSubUrl) {
        this.helix = helix;
        this.eventSubUrl = Objects.requireNonNull(eventSubUrl, "eventSubUrl");
        this.ws.setListener(this);
    }

    public void setNotificationHandler(Consumer<EventNotification> handler) {
        this.notificationHandler = handler;
    }

    public void addDesired(SubscriptionSpec spec) {
        desired.add(spec);
        ensureConnected();
        if (sessionId != null) {
            helix.createEventSubSubscription(spec, sessionId)
                    .thenAccept(response -> {
                        if (response != null && response.isSuccess()) {
                            var result = response.data();
                            if (result != null && !result.subscriptions().isEmpty()) {
                                var subscription = result.subscriptions().get(0);
                                subscriptionIds.put(spec, subscription.id());
                            }
                        }
                    });
        }
    }

    public void removeDesired(SubscriptionSpec spec) {
        desired.remove(spec);

        String subscriptionId = subscriptionIds.remove(spec);
        if (subscriptionId != null) {
            helix.deleteEventSubSubscription(subscriptionId);
        }

        if (desired.isEmpty()) {
            ws.allowReconnect(false);
            ws.close();
        }
    }

    public void ensureConnected() {
        if (desired.isEmpty()) {
            ws.allowReconnect(false);
            ws.close();
            keepalive.stop();
            return;
        }
        if (!ws.isOpen()) {
            ws.allowReconnect(true);
            ws.connect(eventSubUrl);
        }
    }

    @Override
    public void onTimeout() {
        ws.close();
        ws.allowReconnect(true);
        ws.connect(eventSubUrl);
    }

    @Override
    public void onWelcome(SessionInfo info) {
        StreamTweaks.LOGGER.info("EventSub WebSocket connected, sessionId={}, keepaliveTimeout={}s",
                info.sessionId(), info.keepaliveTimeout());
        this.sessionId = info.sessionId();
        keepalive.start(info.keepaliveTimeout());
        subscriptionIds.clear();

        for (SubscriptionSpec spec : desired) {
            helix.createEventSubSubscription(spec, sessionId)
                    .thenAccept(response -> {
                        if (response != null && response.isSuccess()) {
                            var result = response.data();
                            if (result != null && !result.subscriptions().isEmpty()) {
                                var subscription = result.subscriptions().get(0);
                                subscriptionIds.put(spec, subscription.id());
                            }
                        }
                    });
        }
    }

    @Override
    public void onKeepalive() {
        keepalive.onKeepalive();
    }

    @Override
    public void onReconnect(String reconnectUrl) {
        ws.connect(reconnectUrl);
    }

    @Override
    public void onNotification(String type, String json) {
        StreamTweaks.devLogger("Received EventSub notification: type=%s, json=%s".formatted(type, json));
        keepalive.onKeepalive();

        Consumer<EventNotification> handler = notificationHandler;
        if (handler != null) {
            try {
                handler.accept(new EventNotification(type, json));
            } catch (Exception e) {
                StreamTweaks.LOGGER.error("Failed to handle EventSub notification", e);
            }
        }
    }

    @Override
    public void onRevocation(String type, String reason) {
    }

    @Override
    public void onClosed(int code, String reason) {
        sessionId = null;
        subscriptionIds.clear();
        // 通常切断は keepalive 停止（死活監視の対象外になる）
        keepalive.stop();
        if (!desired.isEmpty()) {
            // ポリシー：購読が必要なら再接続を促す
            ensureConnected();
        }
    }

    @Override
    public void onError(Throwable t) {
    }

    public void shutdown() {
        keepalive.close();
        scheduler.shutdownNow();
        ws.close();
    }

    public record EventNotification(String type, String json) {
    }
}
