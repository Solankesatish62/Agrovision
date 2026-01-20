// Package declaration: threading utilities for the kiosk app
package com.agrovision.kiosk.threading;

// Import application-wide constants (single source of truth)
import com.agrovision.kiosk.util.Constants;

// Import centralized logging utility (no printStackTrace in production)
import com.agrovision.kiosk.util.LogUtils;

// Import a bounded blocking queue implementation
import java.util.concurrent.ArrayBlockingQueue;

// Import rejection handler interface for custom drop behavior
import java.util.concurrent.RejectedExecutionHandler;

// Import thread factory for custom thread creation
import java.util.concurrent.ThreadFactory;

// Import core executor implementation (not Executors helper)
import java.util.concurrent.ThreadPoolExecutor;

// Import time unit for keep-alive configuration
import java.util.concurrent.TimeUnit;

// Import atomic counter for thread naming
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DetectionExecutor
 *
 * PURPOSE:
 * - Execute YOLO inference tasks only
 *
 * REAL-TIME GUARANTEE:
 * - Always process the freshest camera frame
 * - Never allow task backlog (no "time travel")
 *
 * HARD RULES:
 * - Single thread only
 * - Queue capacity = 1
 * - Old frame is dropped if YOLO is busy
 */
public final class DetectionExecutor {

    // Core thread count: exactly one YOLO thread
    private static final int CORE_THREADS = 1;

    // Maximum thread count: still one (no parallel YOLO)
    private static final int MAX_THREADS = 1;

    // Keep-alive time: irrelevant since core == max, but required by API
    private static final long KEEP_ALIVE_MS = 0L;

    /**
     * ThreadPoolExecutor configured for REAL-TIME vision:
     *
     * - Single worker thread
     * - Bounded queue (capacity = 1)
     * - Custom rejection policy (drop oldest frame)
     */
    private static final ThreadPoolExecutor EXECUTOR =
            new ThreadPoolExecutor(
                    CORE_THREADS,                  // Minimum threads
                    MAX_THREADS,                   // Maximum threads
                    KEEP_ALIVE_MS,                 // Keep-alive time
                    TimeUnit.MILLISECONDS,         // Time unit
                    new ArrayBlockingQueue<>(1),   // 🔑 Queue size = 1 (NO BACKLOG)
                    new YoloThreadFactory(),       // Custom YOLO thread creation
                    new DropOldestPolicy()         // Drop old frame if busy
            );

    // Static initializer block
    static {
        // Ensure YOLO thread is created immediately
        // Avoids first-frame latency spike
        EXECUTOR.prestartAllCoreThreads();
    }

    // Private constructor to prevent instantiation
    private DetectionExecutor() {
        throw new AssertionError("No instances allowed");
    }

    /**
     * Submit a YOLO inference task.
     *
     * BEHAVIOR:
     * - If YOLO is idle → task executes immediately
     * - If YOLO is busy → previous queued task is dropped
     * - Latest frame always wins
     */
    public static void submit(Runnable task) {

        // Defensive null check
        if (task == null) return;

        try {
            // Execute task using ThreadPoolExecutor
            EXECUTOR.execute(task);

        } catch (Exception e) {
            // Log submission failure (executor shutdown or fatal state)
            LogUtils.e("YOLO task submission failed", e);
        }
    }

    /**
     * Immediately shuts down YOLO execution.
     *
     * Used only during:
     * - Fatal crash recovery
     * - App termination
     */
    public static void shutdownNow() {

        // Interrupt running YOLO task and clear queue
        EXECUTOR.shutdownNow();
    }

    /* =========================================================
       THREAD FACTORY — YOLO THREAD CREATION
       ========================================================= */

    /**
     * Custom thread factory to:
     * - Name YOLO threads
     * - Set correct priority
     * - Prevent silent crashes
     */
    private static final class YoloThreadFactory implements ThreadFactory {

        // Counter for unique thread naming
        private final AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {

            // Create a new thread with the YOLO task
            Thread t = new Thread(r);

            // Assign readable, debuggable name
            t.setName("YOLO-" + count.getAndIncrement());

            // Set priority using Constants (single source of truth)
            t.setPriority(Constants.YOLO_THREAD_PRIORITY);

            // Catch uncaught exceptions so YOLO crashes are visible
            t.setUncaughtExceptionHandler((thread, throwable) ->
                    LogUtils.e("YOLO thread crashed", throwable)
            );

            // Return configured thread to executor
            return t;
        }
    }

    /* =========================================================
       REJECTION POLICY — DROP OLDEST FRAME
       ========================================================= */

    /**
     * Custom rejection handler.
     *
     * Triggered when:
     * - YOLO thread is busy
     * - Queue (capacity = 1) is full
     *
     * STRATEGY:
     * - Drop the old frame
     * - Enqueue the new (freshest) frame
     */
    private static final class DropOldestPolicy implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {

            // Ensure executor is still running
            if (!executor.isShutdown()) {

                // Remove the oldest queued task (stale frame)
                executor.getQueue().poll();

                // Enqueue the latest frame for YOLO
                executor.execute(r);
            }
        }
    }
}
