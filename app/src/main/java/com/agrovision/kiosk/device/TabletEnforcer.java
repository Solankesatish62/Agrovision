package com.agrovision.kiosk.device;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

import com.agrovision.kiosk.BuildConfig;
import com.agrovision.kiosk.util.LogUtils;

/**
 * TabletEnforcer
 *
 * PURPOSE:
 * - Enforces kiosk (lock task) mode
 * - Detects and recovers from kiosk breakouts
 *
 * DESIGN:
 * - MVP-safe LockTask enforcement
 * - Single source of truth for kiosk state
 * - Throttled recovery to prevent relaunch storms
 */
public final class TabletEnforcer {

    private static final long RECOVERY_COOLDOWN_MS = 2000; // 2 seconds

    private final Context appContext;

    // Throttle repeated recovery attempts
    private volatile long lastRecoveryAttemptMs = 0L;

    public TabletEnforcer(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /* =========================================================
       KIOSK MODE ENTRY
       ========================================================= */

    /**
     * Attempts to enter kiosk (lock task) mode.
     *
     * MUST be called from an Activity.
     */
    public void enableKiosk(Activity activity) {
        try {
            if (activity == null) return;

            if (!isKioskActive()) {
                activity.startLockTask();
                LogUtils.i("Kiosk mode enabled");
            }
        } catch (Exception e) {
            LogUtils.w("Failed to start lock task mode", e);
        }
    }

    /**
     * Exits kiosk mode.
     *
     * ⚠️ BLOCKED IN RELEASE BUILDS
     */
    public void disableKiosk(Activity activity) {
        if (!BuildConfig.DEBUG) {
            LogUtils.w("disableKiosk blocked in release build");
            return;
        }

        try {
            if (activity == null) return;

            activity.stopLockTask();
            LogUtils.i("Kiosk mode disabled");
        } catch (Exception e) {
            LogUtils.w("Failed to stop lock task mode", e);
        }
    }

    /* =========================================================
       KIOSK STATE (SINGLE SOURCE OF TRUTH)
       ========================================================= */

    /**
     * @return true if lock task mode is active
     */
    public boolean isKioskActive() {
        try {
            ActivityManager am =
                    (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);

            if (am == null) return false;

            return am.getLockTaskModeState()
                    != ActivityManager.LOCK_TASK_MODE_NONE;

        } catch (Exception e) {
            LogUtils.w("Unable to determine kiosk state", e);
            return false;
        }
    }

    /* =========================================================
       RECOVERY
       ========================================================= */

    /**
     * Brings the kiosk app back to foreground if it lost focus.
     *
     * Safe to call repeatedly.
     * Throttled to prevent relaunch storms.
     */
    public void recoverIfNeeded() {
        try {
            if (isKioskActive()) return;

            long now = SystemClock.elapsedRealtime();
            if (now - lastRecoveryAttemptMs < RECOVERY_COOLDOWN_MS) {
                return;
            }
            lastRecoveryAttemptMs = now;

            Intent intent = appContext
                    .getPackageManager()
                    .getLaunchIntentForPackage(appContext.getPackageName());

            if (intent == null) return;

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            appContext.startActivity(intent);
            LogUtils.w("Kiosk recovered to foreground");

        } catch (Exception e) {
            LogUtils.w("Failed to recover kiosk", e);
        }
    }

    /* =========================================================
       ADMIN / SETUP
       ========================================================= */

    /**
     * Opens system settings (admin setup only).
     */
    public void openKioskSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(intent);
        } catch (Exception e) {
            LogUtils.w("Failed to open settings", e);
        }
    }
}
