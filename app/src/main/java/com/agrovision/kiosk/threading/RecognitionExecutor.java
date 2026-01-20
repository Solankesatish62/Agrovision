// Package containing all executor implementations
package com.agrovision.kiosk.threading;

// Import centralized logging utility (never use printStackTrace)
import com.agrovision.kiosk.util.LogUtils;

// Import constants (single source of truth)
import com.agrovision.kiosk.util.Constants;

// Import bounded queue implementation
import java.util.concurrent.ArrayBlockingQueue;

// Import rejection handler interface
import java.util.concurrent.RejectedExecutionHandler;

// Import thread factory interface
import java.util.concurrent.ThreadFactory;

// Import core ThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor;

// Import time unit enum
import java.util.concurrent.TimeUnit;

// Import atomic integer for thread naming
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RecognitionExecutor
 *
 * PURPOSE:
 * - Execute OCR and text recognition tasks only
 *
 * REAL-TIME GUARANTEE:
 * - Always process the freshest cropped image
 * - Never accumulate OCR backlog
 *
 * HARD RULES:
 * - Single thread only
 * - Queue capacity = 1
 * - Old OCR task is dropped if busy
 *
 * DIFFERENCE FROM YOLO:
 * - Lower thread priority
 * - OCR must yield to detection
 */
public final class RecognitionExecutor {

    // Exactly one OCR thread
    private static final int CORE_THREADS = 1;

    // Never allow parallel OCR
    private static final int MAX_THREADS = 1;

    // No keep-alive needed (core == max)
    private static final long KEEP_ALIVE_MS = 0L;

    /**
     * ThreadPoolExecutor configured for real-time OCR:
     *
     * - Single worker thread
     * - Bounded queue (capacity = 1)
     * - Drop-oldest strategy
     */
    private static final ThreadPoolExecutor EXECUTOR =
            new ThreadPoolExecutor(
                    CORE_THREADS,                    // Core threads
                    MAX_THREADS,                     // Max threads
                    KEEP_ALIVE_MS,                   // Keep-alive
                    TimeUnit.MILLISECONDS,           // Time unit
                    new ArrayBlockingQueue<>(1),     // 🔑 Queue = 1 (no backlog)
                    new OcrThreadFactory(),           // OCR-specific thread
                    new DropOldestPolicy()            // Drop stale OCR task
            );

    // Static initializer
    static {
        // Start OCR thread eagerly to avoid first-use latency
        EXECUTOR.prestartAllCoreThreads();
    }

    // Private constructor to prevent instantiation
    private RecognitionExecutor() {
        throw new AssertionError("No instances allowed");
    }

    /**
     * Submit an OCR task.
     *
     * BEHAVIOR:
     * - If OCR is idle → execute immediately
     * - If OCR is busy → drop previous task
     * - Latest image always wins
     */
    public static void submit(Runnable task) {

        // Defensive null check
        if (task == null) return;

        try {
            // Submit task to executor
            EXECUTOR.execute(task);

        } catch (Exception e) {
            // Log failure without crashing system
            LogUtils.e("OCR task submission failed", e);
        }
    }

    /**
     * Immediately shuts down OCR execution.
     *
     * Used only during:
     * - App termination
     * - Fatal crash recovery
     */
    public static void shutdownNow() {

        // Interrupt running OCR task and clear queue
        EXECUTOR.shutdownNow();
    }

    /* =========================================================
       THREAD FACTORY — OCR THREAD
       ========================================================= */

    /**
     * Custom thread factory for OCR work.
     *
     * DESIGN:
     * - Lower priority than YOLO
     * - Clearly named for debugging
     * - Crashes must be visible
     */
    private static final class OcrThreadFactory implements ThreadFactory {

        // Counter for unique thread naming
        private final AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {

            // Create new thread for OCR task
            Thread t = new Thread(r);

            // Assign descriptive name
            t.setName("OCR-" + count.getAndIncrement());

            // OCR must yield to YOLO detection
            t.setPriority(Constants.OCR_THREAD_PRIORITY);

            // Prevent silent OCR thread crashes
            t.setUncaughtExceptionHandler((thread, throwable) ->
                    LogUtils.e("OCR thread crashed", throwable)
            );

            // Return configured thread
            return t;
        }
    }

    /* =========================================================
       REJECTION POLICY — DROP OLDEST OCR TASK
       ========================================================= */

    /**
     * Rejection handler for OCR overload.
     *
     * STRATEGY:
     * - Drop stale OCR task
     * - Enqueue the latest one
     *
     * REASON:
     * - OCR results must match current detection
     * - Old text is worse than no text
     */
    private static final class DropOldestPolicy implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {

            // Only act if executor is active
            if (!executor.isShutdown()) {

                // Remove stale OCR task
                executor.getQueue().poll();

                // Enqueue the latest OCR task
                executor.execute(r);
            }
        }
    }
}
