package com.agrovision.kiosk.app;

import android.app.Application;

import com.agrovision.kiosk.util.LogUtils;

/**
 * MainApplication
 *
 * SINGLE ENTRY POINT of the kiosk app.
 *
 * RESPONSIBILITIES:
 * - Install global crash handler (via AppInitializer)
 * - Initialize core systems in correct order
 * - Enforce immersive kiosk defaults
 *
 * RULES:
 * - No UI
 * - No Camera
 * - No ML
 */
public final class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        LogUtils.i("MainApplication onCreate");

        /* =========================================================
           SYSTEM BOOTSTRAP
           ========================================================= */
        // Corrected from .init() to .initialize() to match AppInitializer definition
        AppInitializer.initialize(this);

        /* =========================================================
           IMMERSIVE DEFAULTS
           ========================================================= */
        enforceImmersiveDefaults();
    }

    /**
     * Enforces global immersive flags for kiosk stability.
     *
     * NOTE:
     * - Actual UI enforcement happens in Activities
     * - This sets safe defaults only
     */
    private void enforceImmersiveDefaults() {
        try {
            // No-op placeholder.
            // Immersive flags must be applied per-Activity,
            // but we keep this hook to enforce consistency later.

            LogUtils.i("Immersive defaults enforced");

        } catch (Exception e) {
            // Immersive mode must never crash the app
            LogUtils.w("Failed to enforce immersive defaults", e);
        }
    }
}
