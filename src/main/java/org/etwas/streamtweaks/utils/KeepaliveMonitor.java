package org.etwas.streamtweaks.utils;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.util.concurrent.ScheduledFuture;

public final class KeepaliveMonitor implements AutoCloseable {
    public interface Handler {
        void onTimeout();
    }

    private final ScheduledExecutorService scheduler;
    private final Handler handler;
    private final long safetyMarginMillis; // 余裕（例: +5秒）
    private final AtomicLong generation = new AtomicLong(0);
    private volatile ScheduledFuture<?> scheduled;
    private volatile Duration timeout = Duration.ofSeconds(30);
    private volatile boolean started = false;

    public KeepaliveMonitor(Handler handler, Duration safetyMargin, ScheduledExecutorService scheduler) {
        this.handler = Objects.requireNonNull(handler);
        this.safetyMarginMillis = Math.max(0, safetyMargin.toMillis());
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    public synchronized void start(Duration keepaliveTimeout) {
        this.timeout = Objects.requireNonNull(keepaliveTimeout);
        started = true;
        resetTimer();
    }

    public synchronized void onKeepalive() {
        if (!started)
            return;
        resetTimer();
    }

    public synchronized void stop() {
        started = false;
        cancelScheduled();
    }

    @Override
    public synchronized void close() {
        stop();
    }

    private void resetTimer() {
        cancelScheduled();
        final long myGen = generation.incrementAndGet();
        long delay = timeout.toMillis() + safetyMarginMillis;
        scheduled = (ScheduledFuture<?>) scheduler.schedule(() -> {
            // 直近で再スケジュールが走っていないかを世代で確認（古いタスクの実行を防ぐ）
            if (generation.get() == myGen) {
                handler.onTimeout();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void cancelScheduled() {
        final ScheduledFuture<?> s = scheduled;
        if (s != null)
            s.cancel(false);
        scheduled = null;
    }

    public Duration getCurrentTimeout() {
        return timeout;
    }
}
