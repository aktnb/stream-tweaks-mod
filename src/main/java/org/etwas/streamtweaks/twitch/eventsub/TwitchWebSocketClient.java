package org.etwas.streamtweaks.twitch.eventsub;

import static org.etwas.streamtweaks.StreamTweaks.devLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.etwas.streamtweaks.StreamTweaks;
import org.etwas.streamtweaks.utils.BackoffPolicy;
import org.etwas.streamtweaks.utils.ExponentialBackoffPolicy;
import org.etwas.streamtweaks.utils.GsonUtils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class TwitchWebSocketClient implements WebSocketClient {
    private static final Gson gson = GsonUtils.getBuilder().create();
    private final HttpClient http;
    private final ScheduledExecutorService scheduler;

    private volatile WebSocketClient.Listener appListener;
    private volatile WebSocket ws;
    private volatile String lastUrl;
    private volatile boolean reconnectAllowed = false;
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private BackoffPolicy backoff = new ExponentialBackoffPolicy(
            1000, // base 1s
            30000, // max 30s
            2.0, // x2
            0.2 // ±20% jitter
    );
    private final AtomicReference<ScheduledFuture<?>> reconnectFuture = new AtomicReference<>(null);

    public TwitchWebSocketClient() {
        this(Executors.newCachedThreadPool(), Thread.ofVirtual().factory()); // 任意：仮想スレッドも可
    }

    public TwitchWebSocketClient(Executor httpExecutor, ThreadFactory tf) {
        http = HttpClient.newBuilder()
                .executor(httpExecutor)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = tf.newThread(r);
            t.setName("eventsub-ws-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void setListener(Listener l) {
        appListener = l;
    }

    @Override
    public synchronized void setBackoff(BackoffPolicy policy) {
        if (policy != null) {
            backoff = policy;
        }
    }

    @Override
    public synchronized void allowReconnect(boolean allow) {
        reconnectAllowed = allow;
        if (!allow)
            cancelReconnectJob();
    }

    @Override
    public void connect(String url) {
        Objects.requireNonNull(url, "url");
        lastUrl = url;
        if (!connecting.compareAndSet(false, true)) {
            // 既に接続中
            return;
        }
        cancelReconnectJob();

        try {
            http.newWebSocketBuilder()
                    .buildAsync(URI.create(url), new WsListener())
                    .whenComplete((socket, err) -> {
                        connecting.set(false);
                        if (err != null) {
                            fireError(err);
                            scheduleReconnectIfAllowed("connect failed: " + err.getMessage());
                        } else {
                            ws = socket;
                            backoff.reset();
                        }
                    });
        } catch (Throwable t) {
            connecting.set(false);
            fireError(t);
            scheduleReconnectIfAllowed("connect threw: " + t.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        cancelReconnectJob();
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "Client closed");
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public boolean isOpen() {
        final WebSocket s = ws;
        return s != null && !s.isInputClosed() && !s.isOutputClosed();
    }

    private void scheduleReconnectIfAllowed(String reason) {
        if (!reconnectAllowed || lastUrl == null)
            return;
        long delay = backoff.nextBackoffMillis();
        StreamTweaks.devLogger("[WS] schedule reconnect in %d ms (%s)".formatted(delay, reason));
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                connect(lastUrl);
            } catch (Throwable t) {
                fireError(t);
                scheduleReconnectIfAllowed("reconnect attempt error: " + t.getMessage());
            }
        }, delay, TimeUnit.MILLISECONDS);
        setReconnectJob(future);
    }

    private void cancelReconnectJob() {
        ScheduledFuture<?> f = reconnectFuture.getAndSet(null);
        if (f != null)
            f.cancel(false);
    }

    private void setReconnectJob(ScheduledFuture<?> f) {
        ScheduledFuture<?> old = reconnectFuture.getAndSet(f);
        if (old != null)
            old.cancel(false);
    }

    private final class WsListener implements WebSocket.Listener {
        private final StringBuilder textBuf = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            StreamTweaks.devLogger("[WS] onOpen");
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            StreamTweaks.devLogger("[WS] onText (len=" + data.length() + ", last=" + last + ")");
            textBuf.append(data);
            if (last) {
                String json = textBuf.toString();
                textBuf.setLength(0);
                handleMessage(json);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            StreamTweaks.devLogger("[WS] onBinary (ignored)");
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            StreamTweaks.devLogger("[WS] onPing");
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            StreamTweaks.devLogger("[WS] onClose code=" + statusCode + " reason=" + reason);
            ws = null;
            fireClosed(statusCode, reason);
            scheduleReconnectIfAllowed("closed");
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            StreamTweaks.devLogger("[WS] onError " + error);
            fireError(error);
            scheduleReconnectIfAllowed("error");
        }
    }

    private void handleMessage(String json) {
        try {
            var message = gson.fromJson(json, WebSocketMessage.class);
            devLogger(json);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String type = message.metadata().messageType();
            JsonObject payload = message.payload();

            switch (type) {
                case "session_welcome" -> handleWelcome(payload);
                case "session_keepalive" -> fireKeepalive();
                case "session_reconnect" -> handleReconnect(payload);
                case "notification" -> handleNotification(payload);
                case "revocation" -> handleRevocation(root);
                default -> StreamTweaks.devLogger("[WS] unknown message_type: " + type);
            }
        } catch (Throwable t) {
            fireError(t);
        }
    }

    private void handleWelcome(JsonObject payload) {
        var sessionInfo = gson.fromJson(
                payload.getAsJsonObject("session"),
                SessionInfo.class);
        fireWelcome(sessionInfo);
    }

    private void handleReconnect(JsonObject payload) {
        var sessionInfo = gson.fromJson(
                payload.getAsJsonObject("session"),
                SessionInfo.class);
        fireReconnect(sessionInfo.reconnectUrl());
    }

    // channel.chat.message
    private void handleNotification(JsonObject payload) {
        JsonObject sub = payload.getAsJsonObject("subscription");
        String subType = sub.get("type").getAsString();
        JsonElement event = payload.get("event");
        fireNotification(subType, event.toString());
    }

    private void handleRevocation(JsonObject root) {
        JsonObject payload = root.getAsJsonObject("payload");
        JsonObject sub = payload.getAsJsonObject("subscription");
        String subType = sub.get("type").getAsString();
        String status = optString(sub, "status"); // 例: authorization_revoked, version_removed...
        fireRevocation(subType, status);
    }

    private void fireWelcome(SessionInfo info) {
        var l = appListener;
        if (l != null) {
            try {
                l.onWelcome(info);
            } catch (Throwable ignored) {
            }
        }
    }

    private void fireKeepalive() {
        var l = appListener;
        if (l != null) {
            try {
                l.onKeepalive();
            } catch (Throwable ignored) {
            }
        }
    }

    private void fireReconnect(String url) {
        var l = appListener;
        if (l != null) {
            try {
                l.onReconnect(url);
            } catch (Throwable ignored) {
            }
        }
    }

    private void fireNotification(String type, String event) {
        var l = appListener;
        if (l != null) {
            try {
                l.onNotification(type, event);
            } catch (Throwable ignored) {
            }
        }
    }

    private void fireRevocation(String type, String reason) {
        var l = appListener;
        if (l != null) {
            try {
                l.onRevocation(type, reason);
            } catch (Throwable ignored) {
            }
        }
    }

    private void fireClosed(int code, String reason) {
        var l = appListener;
        if (l != null) {
            try {
                l.onClosed(code, reason);
            } catch (Throwable ignored) {
            }
        }
    }

    private void fireError(Throwable t) {
        var l = appListener;
        if (l != null) {
            try {
                l.onError(t);
            } catch (Throwable ignored) {
            }
        }
    }

    private static String optString(JsonObject o, String k) {
        return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }

    public void shutdown() {
        cancelReconnectJob();
        close();
        scheduler.shutdownNow();
    }
}
