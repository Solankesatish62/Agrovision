package com.agrovision.kiosk.analytics;

import com.agrovision.kiosk.threading.IoExecutor;
import com.agrovision.kiosk.util.LogUtils;
import com.agrovision.kiosk.util.TimeUtils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * PerformanceLogger
 *
 * PURPOSE:
 * - Measure and expose system performance metrics
 *
 * DESIGN RULES:
 * - Measurement only (no decisions)
 * - No analytics emission
 * - No AppState knowledge
 * - No UI interaction
 * - No thread creation
 *
 * All interpretation of these metrics
 * MUST happen in StateMachine.
 */
public final class PerformanceLogger {

    // Singleton instance
    private static final PerformanceLogger INSTANCE = new PerformanceLogger();

    // Last YOLO inference time (ms)
    private final AtomicLong lastYoloLatencyMs = new AtomicLong(0);

    // Last OCR processing time (ms)
    private final AtomicLong lastOcrLatencyMs = new AtomicLong(0);

    // Last end-to-end scan time (ms)
    private final AtomicLong lastEndToEndLatencyMs = new AtomicLong(0);

    // Private constructor
    private PerformanceLogger() {}

    /**
     * Returns singleton instance.
     */
    public static PerformanceLogger getInstance() {
        return INSTANCE;
    }

    /* =========================================================
       YOLO MEASUREMENT
       ========================================================= */

    /**
     * Marks start of YOLO inference.
     *
     * @return monotonic start timestamp (nano)
     */
    public long markYoloStart() {
        return TimeUtils.nowNano();
    }

    /**
     * Marks end of YOLO inference.
     */
    public void markYoloEnd(long startNano) {
        long durationMs = TimeUtils.elapsedMs(startNano);
        lastYoloLatencyMs.set(durationMs);

        // Debug logging only (non-blocking)
        LogUtils.d("YOLO latency = " + durationMs + " ms");
    }

    /* =========================================================
       OCR MEASUREMENT
       ========================================================= */

    /**
     * Marks start of OCR processing.
     */
    public long markOcrStart() {
        return TimeUtils.nowNano();
    }

    /**
     * Marks end of OCR processing.
     */
    public void markOcrEnd(long startNano) {
        long durationMs = TimeUtils.elapsedMs(startNano);
        lastOcrLatencyMs.set(durationMs);

        LogUtils.d("OCR latency = " + durationMs + " ms");
    }

    /* =========================================================
       END-TO-END MEASUREMENT
       ========================================================= */

    /**
     * Marks start of full scan session.
     */
    public long markScanStart() {
        return TimeUtils.nowNano();
    }

    /**
     * Marks end of full scan session.
     */
    public void markScanEnd(long startNano) {
        long durationMs = TimeUtils.elapsedMs(startNano);
        lastEndToEndLatencyMs.set(durationMs);

        // Persist raw metric asynchronously if needed
        IoExecutor.submit(() ->
                LogUtils.d("Scan end-to-end latency = " + durationMs + " ms")
        );
    }

    /* =========================================================
       READ-ONLY ACCESSORS
       ========================================================= */

    public long getLastYoloLatencyMs() {
        return lastYoloLatencyMs.get();
    }

    public long getLastOcrLatencyMs() {
        return lastOcrLatencyMs.get();
    }

    public long getLastEndToEndLatencyMs() {
        return lastEndToEndLatencyMs.get();
    }
}
