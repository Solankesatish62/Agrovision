// Package for all executor definitions
package com.agrovision.kiosk.threading;

// Import centralized logging utility
import com.agrovision.kiosk.util.LogUtils;

// Import application constants
import com.agrovision.kiosk.util.Constants;

// Import bounded blocking queue
import java.util.concurrent.ArrayBlockingQueue;

// Import rejection handler for overload situations
import java.util.concurrent.RejectedExecutionHandler;

// Import thread factory interface
import java.util.concurrent.ThreadFactory;

// Import core thread pool executor
import java.util.concurrent.ThreadPoolExecutor;

// Import time unit enum
import java.util.concurrent.TimeUnit;

// Import atomic integer for thread naming
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IoExecutor
 *
 * PURPOSE:
 * - Execute ALL disk and persistence operations
 *
 * EXAMPLES:
 * - Database writes
 * - JSON reads
 * - Analytics persistence
 * - File logging
 *
 * HARD RULES:
 * - MUST NEVER run ML
 * - MUST NEVER touch UI
 * - MUST NEVER block YOLO or OCR
 *
 * DESIGN PHILOSOPHY:
 * - IO is slow by nature
 * - IO correctness > IO speed
 * - IO backlog is acceptable (within limits)
 */
public final class IoExecutor {

    // Core thread count: exactly one IO thread
    private static final int CORE_THREADS = 1;

    // Max thread count: still one (no parallel IO)
    private static final int MAX_THREADS = 1;

    // Keep-alive time: irrelevant here but required by API
    private static final long KEEP_ALIVE_MS = 0L;

    /**
     * ThreadPoolExecutor for IO operations.
     *
     * DIFFERENCE FROM YOLO EXECUTOR:
     * - IO tasks are NOT real-time
     * - We ALLOW a small backlog
     * - We DROP tasks only when system is overloaded
     */
    private static final ThreadPoolExecutor EXECUTOR =
            new ThreadPoolExecutor(
                    CORE_THREADS,                     // One IO thread
                    MAX_THREADS,                      // Never scale up
                    KEEP_ALIVE_MS,                    // No keep-alive needed
                    TimeUnit.MILLISECONDS,            // Time unit
                    new ArrayBlockingQueue<>(20),     // Small bounded backlog
                    new IoThreadFactory(),             // Low-priority IO thread
                    new DropIfFullPolicy()             // Drop if overwhelmed
            );

    // Static initializer
    static {
        // Start IO thread eagerly
        // Avoid first-write latency
        EXECUTOR.prestartAllCoreThreads();
    }

    // Private constructor to prevent instantiation
    private IoExecutor() {
        throw new AssertionError("No instances allowed");
    }

    /**
     * Submit an IO task.
     *
     * BEHAVIOR:
     * - Executes serially
     * - If backlog is full → task is dropped
     * - System stability is preferred over persistence
     */
    public static void submit(Runnable task) {

        // Defensive null check
        if (task == null) return;

        try {
            // Enqueue IO task
            EXECUTOR.execute(task);

        } catch (Exception e) {
            // Log failure without crashing system
            LogUtils.e("IO task submission failed", e);
        }
    }

    /**
     * Immediately shuts down IO execution.
     *
     * Used during:
     * - App shutdown
     * - Fatal crash recovery
     */
    public static void shutdownNow() {

        // Interrupt running IO task and clear backlog
        EXECUTOR.shutdownNow();
    }

    /* =========================================================
       THREAD FACTORY — IO THREAD
       ========================================================= */

    /**
     * Custom thread factory for IO work.
     *
     * DESIGN:
     * - Low priority
     * - Clearly named
     * - Crashes must be visible
     */
    private static final class IoThreadFactory implements ThreadFactory {

        // Counter for unique thread naming
        private final AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {

            // Create new thread for IO task
            Thread t = new Thread(r);

            // Assign descriptive name
            t.setName("IO-" + count.getAndIncrement());

            // IO must yield to ML and UI
            t.setPriority(Thread.MIN_PRIORITY);

            // Prevent silent IO thread crashes
            t.setUncaughtExceptionHandler((thread, throwable) ->
                    LogUtils.e("IO thread crashed", throwable)
            );

            // Return configured thread
            return t;
        }
    }

    /* =========================================================
       REJECTION POLICY — DROP IF FULL
       ========================================================= */

    /**
     * Rejection policy for IO overload.
     *
     * STRATEGY:
     * - Drop task silently when queue is full
     *
     * REASON:
     * - Analytics loss is acceptable
     * - App freeze or OOM is NOT
     */
    private static final class DropIfFullPolicy implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {

            // Only act if executor is still active
            if (!executor.isShutdown()) {

                // Log once per rejection path
                LogUtils.w("IO task dropped due to full queue");
            }
        }
    }
}
