package org.etwas.streamtweaks.utils;

import java.util.concurrent.ThreadLocalRandom;

public class ExponentialBackoffPolicy implements BackoffPolicy {
    private final long baseMillis;
    private final long maxMillis;
    private final double multiplier;
    private final double jitter;

    private int attempts = 0;

    public ExponentialBackoffPolicy(long baseMillis, long maxMillis, double multiplier, double jitter) {
        this.baseMillis = baseMillis;
        this.maxMillis = maxMillis;
        this.multiplier = multiplier;
        this.jitter = jitter;
    }

    @Override
    public long nextBackoffMillis() {
        // 計算: base * multiplier^attempts
        double exp = baseMillis * Math.pow(multiplier, attempts++);
        long capped = (long) Math.min(exp, maxMillis);

        // ジッタ適用
        double delta = capped * jitter;
        double min = capped - delta;
        double max = capped + delta;
        return (long) ThreadLocalRandom.current().nextDouble(min, max);
    }

    @Override
    public void reset() {
        attempts = 0;
    }
}
