package com.agrovision.kiosk.device;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import com.agrovision.kiosk.util.LogUtils;

/**
 * BrightnessManager
 *
 * PURPOSE:
 * - Control screen brightness safely in kiosk mode.
 * - Prevent unreadable screen and OLED burn-in.
 *
 * REQUIREMENTS:
 * - App must have WRITE_SETTINGS permission granted via ADB or Settings.
 */
public final class BrightnessManager {

    // Safe brightness bounds (0–255)
    private static final int MIN_BRIGHTNESS = 40;   // Prevent black screen
    private static final int MAX_BRIGHTNESS = 220;  // Prevent OLED burn-in (Max usually 255)

    // Idle brightness (dim, but visible)
    private static final int IDLE_BRIGHTNESS = 70;

    private final Context context;
    private final ContentResolver resolver;

    public BrightnessManager(Context context) {
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
    }

    /* =========================================================
       MODE CONTROL
       ========================================================= */

    /**
     * Sets brightness for ACTIVE usage (scan / interaction).
     */
    public void setActiveBrightness() {
        setBrightness(MAX_BRIGHTNESS);
    }

    /**
     * Sets brightness for IDLE mode (burn-in protection).
     */
    public void setIdleBrightness() {
        setBrightness(IDLE_BRIGHTNESS);
    }

    /* =========================================================
       CORE LOGIC
       ========================================================= */

    private void setBrightness(int value) {
        // Optimization: Check permission first to avoid exception spam in logs
        if (!hasPermission()) {
            LogUtils.w("Missing WRITE_SETTINGS permission; brightness unchanged.");
            return;
        }

        try {
            int safeValue = clamp(value, MIN_BRIGHTNESS, MAX_BRIGHTNESS);

            // Force manual brightness mode (disable auto-brightness)
            Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            );

            // Apply value
            Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    safeValue
            );

            LogUtils.i("Brightness set to " + safeValue);

        } catch (Exception e) {
            // Permission denied or OEM restriction
            LogUtils.w("Unable to set brightness", e);
        }
    }

    /**
     * Checks if the app is allowed to modify system settings.
     */
    public boolean hasPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return Settings.System.canWrite(context);
        }
        return true; // Older versions granted this via Manifest
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}