package org.etwas.streamtweaks.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public final class ThreadPools {
    public static ScheduledExecutorService singleScheduler(String threadName) {
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        };
        return Executors.newSingleThreadScheduledExecutor(tf);
    }
}