package com.agrovision.kiosk.watchdog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.agrovision.kiosk.util.LogUtils;

/**
 * BootReceiver
 *
 * PURPOSE:
 * - Auto-start kiosk app after device reboot
 *
 * DESIGN:
 * - Minimal logic
 * - No blocking
 * - No heavy work
 *
 * CRITICAL:
 * - Requires RECEIVE_BOOT_COMPLETED permission
 * - App must be whitelisted from battery optimizations
 */
public final class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            return;
        }

        try {
            LogUtils.i("Boot completed — launching kiosk");

            Intent launchIntent = context
                    .getPackageManager()
                    .getLaunchIntentForPackage(context.getPackageName());

            if (launchIntent == null) {
                LogUtils.w("Launch intent not found after boot");
                return;
            }

            launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            context.startActivity(launchIntent);

        } catch (Exception e) {
            LogUtils.e("Failed to launch kiosk after boot", e);
        }
    }
}
