package com.agrovision.kiosk.app;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.agrovision.kiosk.analytics.EventTracker;
import com.agrovision.kiosk.sync.SyncWorker;
import com.agrovision.kiosk.threading.IoExecutor;
import com.agrovision.kiosk.util.LogUtils;
import com.agrovision.kiosk.watchdog.KioskExceptionHandler;

import java.util.concurrent.TimeUnit;

/**
 * AppInitializer performs ordered, one-time initialization
 * of global application infrastructure.
 *
 * STRICT RULES:
 * - No StateMachine access
 * - No UI access
 * - No camera / vision / session logic
 * - No heavy work on main thread
 */
public final class AppInitializer {

    // Guard against duplicate initialization
    private static volatile boolean initialized = false;

    // Prevent instantiation
    private AppInitializer() {}

    /**
     * Initializes core infrastructure.
     * MUST be called from MainApplication.onCreate().
     */
    public static synchronized void initialize(Context context) {

        // -------------------------------------------------
        // 0️⃣ ALWAYS install crash handler FIRST
        // -------------------------------------------------
        // Even if initialization fails halfway, crashes must be caught
        new KioskExceptionHandler(context).install();

        // Prevent duplicate initialization
        if (initialized) {
            return;
        }

        // Always use application context to avoid leaks
        Context appContext = context.getApplicationContext();

        // -------------------------------------------------
        // 1️⃣ Load immutable kiosk configuration (FAIL-FAST)
        // -------------------------------------------------
        KioskConfigLoader.init(appContext);

        // -------------------------------------------------
        // 2️⃣ Initialize logging
        // -------------------------------------------------
        LogUtils.init();
        LogUtils.i("AppInitializer: infrastructure boot started");

        // -------------------------------------------------
        // 3️⃣ Kick off async heavy initializations
        // -------------------------------------------------
        IoExecutor.submit(() -> {
            EventTracker.getInstance(appContext).initAsync();
            scheduleBackgroundSync(appContext);
            LogUtils.i("AppInitializer: background services ready");
        });

        initialized = true;
        LogUtils.i("AppInitializer: infrastructure boot complete");
    }

    /**
     * Schedules periodic background sync for unknown detections.
     * Interval: 15 minutes (Android WorkManager minimum).
     */
    private static void scheduleBackgroundSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest syncRequest = new PeriodicWorkRequest.Builder(
                SyncWorker.class,
                15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "MedicineSync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
        );
        LogUtils.i("AppInitializer: Background sync scheduled");
    }
}
