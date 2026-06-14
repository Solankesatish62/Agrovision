package com.agrovision.kiosk.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.agrovision.kiosk.BuildConfig;
import com.agrovision.kiosk.util.LogUtils;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * KioskStatusManager
 *
 * Responsibility: Manages real-time online/offline status in Firestore.
 * Sends frequent heartbeats while the app is active and an OFFLINE signal on shutdown.
 */
public final class KioskStatusManager {
    private static final String TAG = "KioskStatusManager";
    private static final long HEARTBEAT_INTERVAL_MS = 30_000; // 30 seconds

    private static volatile KioskStatusManager INSTANCE;
    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;

    private KioskStatusManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static KioskStatusManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (KioskStatusManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new KioskStatusManager(context);
                }
            }
        }
        return INSTANCE;
    }

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            sendUpdate("ONLINE");
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };

    /**
     * Start frequent heartbeats. Call this from Activity.onStart() or similar.
     */
    public void start() {
        if (!isRunning) {
            isRunning = true;
            handler.post(heartbeatRunnable);
            Log.d(TAG, "Foreground heartbeat started");
        }
    }

    /**
     * Stop frequent heartbeats and signal OFFLINE.
     */
    public void stop() {
        isRunning = false;
        handler.removeCallbacks(heartbeatRunnable);
        sendUpdate("OFFLINE");
        Log.d(TAG, "Foreground heartbeat stopped, signaled OFFLINE");
    }

    private void sendUpdate(String status) {
        SharedPreferences prefs = appContext.getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE);
        String shopId = prefs.getString("shop_mobile", null);

        if (shopId == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("lastActiveTimestamp", System.currentTimeMillis());
        data.put("status", status);
        data.put("appVersion", BuildConfig.VERSION_NAME);
        data.put("deviceId", android.os.Build.MODEL);

        FirebaseFirestore.getInstance().collection("kiosks")
                .document(shopId)
                .set(data, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "Failed to update status to " + status, e));
    }
}
