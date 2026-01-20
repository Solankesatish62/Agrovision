package com.agrovision.kiosk.util;

/**
 * Time utility helpers.
 *
 * RULES:
 * - Monotonic time (nanoTime) for durations & performance
 * - Wall-clock time ONLY for human-readable timestamps
 * - Never mix the two
 */
public final class TimeUtils {

    private TimeUtils() {
        throw new AssertionError("No instances allowed");
    }

    /* =========================================================
       MONOTONIC TIME (SAFE FOR DURATIONS)
       ========================================================= */

    /**
     * @return monotonic time in nanoseconds.
     * Use ONLY for elapsed time measurements.
     */
    public static long nowNano() {
        return System.nanoTime();
    }

    /**
     * @return monotonic time in milliseconds.
     * Derived from nanoTime (safe).
     */
    public static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    /* =========================================================
       WALL CLOCK TIME (HUMAN / AUDIT)
       ========================================================= */

    /**
     * @return current wall-clock time in milliseconds since epoch.
     *
     * WARNING:
     * - Do NOT use for durations
     * - Subject to system clock changes
     * - Intended for timestamps, logs, UI display only
     */
    public static long nowWallClockMs() {
        return System.currentTimeMillis();
    }

    /* =========================================================
       ELAPSED TIME
       ========================================================= */

    public static long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }

    public static long elapsedNano(long startNano) {
        return System.nanoTime() - startNano;
    }

    /* =========================================================
       TIME CONVERSIONS
       ========================================================= */

    public static long secondsToMillis(long seconds) {
        return seconds * Constants.ONE_SECOND_MS;
    }

    public static long minutesToMillis(long minutes) {
        return minutes * Constants.ONE_MINUTE_MS;
    }

    /* =========================================================
       FORMATTING (DEBUG / UI)
       ========================================================= */

    public static String formatDurationMs(long millis) {
        if (millis < 1_000) {
            return millis + " ms";
        }
        return String.format("%.2f s", millis / 1000.0);
    }

    public static String formatDurationNano(long nanos) {
        return formatDurationMs(nanos / 1_000_000L);
    }
}
