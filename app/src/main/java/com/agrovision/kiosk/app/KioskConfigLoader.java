package com.agrovision.kiosk.app;

import android.content.Context;

import com.agrovision.kiosk.util.LogUtils;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * KioskConfigLoader
 *
 * PURPOSE:
 * - Load static configuration from assets exactly once
 *
 * RULES:
 * - No business logic
 * - No side effects
 * - Immutable after load
 */
public final class KioskConfigLoader {

    private static final String CONFIG_PATH = "config/thresholds.json";

    private static volatile KioskConfigLoader INSTANCE;

    private final long sessionTimeoutMs;
    private final long idleTimeoutMs;
    private final float matchConfidenceThreshold;

    private KioskConfigLoader(Context appContext) {

        long sessionTimeout = 60_000L;
        long idleTimeout = 15_000L;
        float confidence = 0.6f;

        try (InputStream is = appContext.getAssets().open(CONFIG_PATH);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder jsonBuilder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

            JSONObject root = new JSONObject(jsonBuilder.toString());

            sessionTimeout = root.optLong("session_timeout_ms", sessionTimeout);
            idleTimeout = root.optLong("idle_timeout_ms", idleTimeout);
            confidence = (float) root.optDouble(
                    "match_confidence_threshold", confidence);

            LogUtils.i("KioskConfig loaded");

        } catch (Exception e) {
            LogUtils.w("Failed to load kiosk config, using defaults", e);
        }

        this.sessionTimeoutMs = sessionTimeout;
        this.idleTimeoutMs = idleTimeout;
        this.matchConfidenceThreshold = confidence;
    }

    /**
     * Initializes and returns the singleton.
     *
     * MUST be called from Application.onCreate().
     */
    public static KioskConfigLoader init(Context context) {
        if (INSTANCE == null) {
            synchronized (KioskConfigLoader.class) {
                if (INSTANCE == null) {
                    INSTANCE = new KioskConfigLoader(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Returns already initialized instance.
     */
    public static KioskConfigLoader get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("KioskConfigLoader not initialized");
        }
        return INSTANCE;
    }

    /* =========================================================
       READ-ONLY ACCESSORS
       ========================================================= */

    public long getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    public float getMatchConfidenceThreshold() {
        return matchConfidenceThreshold;
    }
}
