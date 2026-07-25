package com.boothyeah.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared thread pool for background operations.
 * Uses daemon threads so the pool doesn't prevent JVM shutdown.
 * All services use this pool for async work (uploads, compositing, etc.).
 */
public final class ThreadPool {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true); // Don't block JVM shutdown
        t.setName("boothyeah-worker-" + t.getId());
        return t;
    });

    private ThreadPool() {}

    /** Get the shared executor for submitting background tasks */
    public static ExecutorService getExecutor() {
        return EXECUTOR;
    }

    /** Graceful shutdown — call from App.stop() */
    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }
}
