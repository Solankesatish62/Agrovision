package com.agrovision.kiosk.session;

import com.agrovision.kiosk.util.TimeUtils;

/**
 * SessionTimer
 *
 * Pure, monotonic time-rule evaluator for a ScanSession.
 *
 * IMPORTANT CONTRACT:
 * - TimeUtils.nowMs() MUST use SystemClock.elapsedRealtime()
 * - This class assumes monotonic time
 *
 * DESIGN:
 * - Session-scoped (one instance per ScanSession)
 * - Passive calculator (polled, never schedules)
 * - No Android framework dependencies
 */
public final class SessionTimer {

    /* =========================================================
       CONFIGURATION (MILLISECONDS)
       ========================================================= */

    private final long maxSessionDurationMs;
    private final long idleTimeoutMs;

    /* =========================================================
       MONOTONIC TIME STATE
       ========================================================= */

    private final long sessionStartMs;
    private long lastActivityMs;

    /* =========================================================
       CONSTRUCTOR
       ========================================================= */

    public SessionTimer(long maxSessionDurationMs,
                        long idleTimeoutMs) {

        if (maxSessionDurationMs <= 0) {
            throw new IllegalArgumentException("maxSessionDurationMs must be > 0");
        }

        if (idleTimeoutMs <= 0) {
            throw new IllegalArgumentException("idleTimeoutMs must be > 0");
        }

        this.maxSessionDurationMs = maxSessionDurationMs;
        this.idleTimeoutMs = idleTimeoutMs;

        this.sessionStartMs = TimeUtils.nowMs();
        this.lastActivityMs = this.sessionStartMs;
    }

    /* =========================================================
       ACTIVITY TRACKING
       ========================================================= */

    /**
     * Marks activity within the session.
     * Called when:
     * - medicine added
     * - user interaction occurs
     * - explanation progresses
     */
    public synchronized void markActivity() {
        lastActivityMs = TimeUtils.nowMs();
    }

    /* =========================================================
       TIME EVALUATION
       ========================================================= */

    /**
     * Returns true if total session lifetime exceeded.
     */
    public synchronized boolean isSessionExpired() {
        return (TimeUtils.nowMs() - sessionStartMs) >= maxSessionDurationMs;
    }

    /**
     * Returns true if idle timeout exceeded.
     */
    public synchronized boolean isIdleTimedOut() {
        return (TimeUtils.nowMs() - lastActivityMs) >= idleTimeoutMs;
    }

    /* =========================================================
       READ-ONLY METRICS (FOR UI / DEBUG)
       ========================================================= */

    public synchronized long getElapsedSessionMs() {
        return TimeUtils.nowMs() - sessionStartMs;
    }

    public synchronized long getIdleDurationMs() {
        return TimeUtils.nowMs() - lastActivityMs;
    }

    public long getMaxSessionDurationMs() {
        return maxSessionDurationMs;
    }

    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }
}
