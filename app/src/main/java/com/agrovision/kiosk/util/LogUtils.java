package com.agrovision.kiosk.util;

import android.util.Log;

import com.agrovision.kiosk.BuildConfig;

/**
 * Centralized logging utility.
 *
 * RULES:
 * - No direct Log.d/e/i usage outside this class
 * - Structured logging only
 * - Safe to call during crashes
 * - init() must be called once at app startup
 */
public final class LogUtils {

    private static final String TAG = Constants.APP_TAG;

    private LogUtils() {
        throw new AssertionError("No instances allowed");
    }

    /* =========================================================
       INITIALIZATION
       ========================================================= */

    /**
     * Initializes logging system.
     *
     * Reserved for future use:
     * - File logging
     * - Remote logging
     * - Timber / custom logger setup
     *
     * Must be called from AppInitializer.
     */
    public static void init() {
        // Intentionally empty (future-proof hook)
    }

    /* =========================================================
       DEBUG LOGS (STRIPPED IN RELEASE)
       ========================================================= */

    public static void d(String message) {
        if (isDebug()) {
            Log.d(TAG, safe(message));
        }
    }

    public static void d(String message, Throwable t) {
        if (isDebug()) {
            Log.d(TAG, safe(message), t);
        }
    }

    /* =========================================================
       INFO LOGS
       ========================================================= */

    public static void i(String message) {
        Log.i(TAG, safe(message));
    }

    /* =========================================================
       WARNING LOGS
       ========================================================= */

    public static void w(String message) {
        Log.w(TAG, safe(message));
    }

    public static void w(String message, Throwable t) {
        Log.w(TAG, safe(message), t);
    }

    /* =========================================================
       ERROR LOGS (ALWAYS LOG)
       ========================================================= */

    public static void e(String message) {
        Log.e(TAG, safe(message));
    }

    public static void e(String message, Throwable t) {
        Log.e(TAG, safe(message), t);
    }

    /* =========================================================
       STRUCTURED DEBUG LOGGING
       ========================================================= */

    /**
     * Structured log format:
     * EVENT | key1=value1 | key2=value2
     */
    public static void log(String event, String... keyValues) {
        if (!isDebug()) return;

        StringBuilder sb = new StringBuilder(event);

        if (keyValues != null) {
            for (int i = 0; i < keyValues.length - 1; i += 2) {
                sb.append(" | ")
                        .append(keyValues[i])
                        .append("=")
                        .append(keyValues[i + 1]);
            }
        }

        Log.d(TAG, sb.toString());
    }

    /* =========================================================
       INTERNAL HELPERS
       ========================================================= */

    private static boolean isDebug() {
        return BuildConfig.DEBUG;
    }

    private static String safe(String message) {
        return message == null ? "null" : message;
    }
}
