package org.etwas.streamtweaks.twitch.eventsub;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import org.etwas.streamtweaks.StreamTweaks;
import org.etwas.streamtweaks.twitch.HelixClient;
import org.etwas.streamtweaks.utils.KeepaliveMonitor;
import org.etwas.streamtweaks.utils.ThreadPools;

public final class EventSubManager implements WebSocketClient.Listener, KeepaliveMonitor.Handler {
    private static final String DEFAULT_EVENTSUB_URL = "wss://eventsub.wss.twitch.tv/ws";

    private final WebSocketClient ws;
    private final HelixClient helix;
    private final Set<SubscriptionSpec> desired;
    private final ScheduledExecutorService scheduler;
    private final KeepaliveMonitor keepalive;
    private final String eventSubUrl;
    private final Map<SubscriptionSpec, String> subscriptionIds;

    private volatile String sessionId;

    public EventSubManager(WebSocketClient ws, HelixClient helix, Set<SubscriptionSpec> desired) {
        this(ws, helix, desired, DEFAULT_EVENTSUB_URL);
    }

    public EventSubManager(WebSocketClient ws, HelixClient helix, Set<SubscriptionSpec> desired, String eventSubUrl) {
        this.ws = ws;
        this.helix = helix;
        this.desired = desired;
        this.eventSubUrl = Objects.requireNonNull(eventSubUrl, "eventSubUrl");
        this.scheduler = ThreadPools.singleScheduler("eventsub-scheduler");
        this.keepalive = new KeepaliveMonitor(this, java.time.Duration.ofSeconds(5), scheduler);
        this.subscriptionIds = new ConcurrentHashMap<>();
    }

    public void addDesired(SubscriptionSpec spec) {
        desired.add(spec);
        ensureConnected();
        if (sessionId != null) {
            helix.createEventSubSubscription(spec, sessionId)
                    .thenAccept(response -> {
                        if (response != null && response.isSuccess() && response.subscriptionId() != null) {
                            subscriptionIds.put(spec, response.subscriptionId());
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

        if (desired.isEmpty())
            ws.close();
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
        this.sessionId = info.sessionId();
        keepalive.start(info.keepaliveTimeout());
        subscriptionIds.clear();

        for (SubscriptionSpec spec : desired) {
            helix.createEventSubSubscription(spec, sessionId)
                    .thenAccept(response -> {
                        if (response != null && response.isSuccess() && response.subscriptionId() != null) {
                            subscriptionIds.put(spec, response.subscriptionId());
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
        StreamTweaks.LOGGER.info("Received EventSub notification: type={}, json={}", type, json);
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
}
