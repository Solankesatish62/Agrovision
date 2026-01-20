package com.agrovision.kiosk.watchdog;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.agrovision.kiosk.util.LogUtils;

/**
 * KioskExceptionHandler
 *
 * PURPOSE:
 * - Catch ALL uncaught exceptions
 * - Log crash details
 * - Reliably restart kiosk app via OS (AlarmManager)
 *
 * CRITICAL:
 * - Must survive process death
 * - Must not rely on in-memory handlers
 */
public final class KioskExceptionHandler
        implements Thread.UncaughtExceptionHandler {

    private static final long RESTART_DELAY_MS = 500;

    private final Context appContext;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public KioskExceptionHandler(Context context) {
        this.appContext = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    /**
     * Installs this handler as the global crash handler.
     */
    public void install() {
        Thread.setDefaultUncaughtExceptionHandler(this);
        LogUtils.i("KioskExceptionHandler installed");
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {

        try {
            LogUtils.e("UNCAUGHT EXCEPTION — kiosk restarting", throwable);

            scheduleRestart();

        } catch (Exception ignored) {
            // Nothing must escape this handler
        } finally {
            // Allow Android to kill the process cleanly
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        }
    }

    /**
     * Schedules a restart using AlarmManager.
     * This survives process death.
     */
    private void scheduleRestart() {
        Intent intent = appContext
                .getPackageManager()
                .getLaunchIntentForPackage(appContext.getPackageName());

        if (intent == null) return;

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        PendingIntent pendingIntent = PendingIntent.getActivity(
                appContext,
                0,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager =
                (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);

        if (alarmManager == null) return;

        alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + RESTART_DELAY_MS,
                pendingIntent
        );
    }
}
