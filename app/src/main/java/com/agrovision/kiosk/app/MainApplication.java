package com.agrovision.kiosk.app;

import android.app.Application;

import android.app.Activity;
import android.os.Bundle;

import com.agrovision.kiosk.sync.KioskStatusManager;
import com.agrovision.kiosk.util.LogUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * MainApplication
 *
 * SINGLE ENTRY POINT of the kiosk app.
 */
public final class MainApplication extends Application {

    private int activityCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        LogUtils.i("MainApplication onCreate");

        AppInitializer.initialize(this);
        enforceImmersiveDefaults();
        setupActivityTracking();
    }

    private void setupActivityTracking() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                activityCount++;
                if (activityCount == 1) {
                    // App entered foreground
                    KioskStatusManager.getInstance(MainApplication.this).start();
                }
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                activityCount--;
                if (activityCount == 0) {
                    // App entered background or closed
                    KioskStatusManager.getInstance(MainApplication.this).stop();
                }
            }

            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
            @Override public void onActivityResumed(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });
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
