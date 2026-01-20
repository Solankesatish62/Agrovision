package com.agrovision.kiosk.idle;

import android.os.SystemClock;

/**
 * IdleTimer
 *
 * PURPOSE:
 * - Tracks user/system inactivity using a monotonic clock
 *
 * THREAD-SAFETY:
 * - lastActivityMs is volatile to ensure visibility across threads
 *
 * DESIGN:
 * - No scheduling
 * - No callbacks
 * - No Android lifecycle references
 */
public final class IdleTimer {

    // Idle threshold (milliseconds)
    private final long idleTimeoutMs;

    // Last activity timestamp (monotonic, thread-visible)
    private volatile long lastActivityMs;

    /**
     * @param idleTimeoutMs Duration after which system is considered idle
     */
    public IdleTimer(long idleTimeoutMs) {
        if (idleTimeoutMs <= 0) {
            throw new IllegalArgumentException("Idle timeout must be > 0");
        }
        this.idleTimeoutMs = idleTimeoutMs;
        this.lastActivityMs = SystemClock.elapsedRealtime();
    }

    /**
     * Marks system activity (touch, scan, USB input, network wake, etc.)
     *
     * Safe to call from ANY thread.
     */
    public void reset() {
        lastActivityMs = SystemClock.elapsedRealtime();
    }

    /**
     * @return true if idle timeout has elapsed
     *
     * Safe to call from ANY thread.
     */
    public boolean isIdle() {
        long now = SystemClock.elapsedRealtime();
        return (now - lastActivityMs) >= idleTimeoutMs;
    }

    /**
     * @return elapsed idle time in milliseconds
     */
    public long getIdleDurationMs() {
        return SystemClock.elapsedRealtime() - lastActivityMs;
    }

    /**
     * @return configured idle timeout
     */
    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }
}
