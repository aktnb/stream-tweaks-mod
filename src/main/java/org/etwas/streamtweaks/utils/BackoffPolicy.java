package org.etwas.streamtweaks.utils;

public interface BackoffPolicy {
    long nextBackoffMillis();

    void reset();
}
