package com.agrovision.kiosk.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.agrovision.kiosk.BuildConfig;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public final class HeartbeatWorker extends Worker {
    private static final String TAG = "HeartbeatWorker";

    public HeartbeatWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE);
        
        // 1. Get Kiosk ID (This is our document ID in 'kiosks' collection)
        String kioskId = prefs.getString("kiosk_id", null);
        if (kioskId == null) {
            kioskId = prefs.getString("shop_mobile", null); // Legacy fallback
        }

        if (kioskId == null) {
            return Result.success(); // Not registered yet
        }

        // 2. Get Shop ID (The linked account ID)
        String shopId = prefs.getString("shop_id", prefs.getString("shop_mobile", null));
        String shopName = prefs.getString("shop_name", "Unknown");

        Map<String, Object> heartbeat = new HashMap<>();
        heartbeat.put("lastActiveTimestamp", System.currentTimeMillis());
        heartbeat.put("appVersion", BuildConfig.VERSION_NAME);
        heartbeat.put("shopName", shopName);
        if (shopId != null) {
            heartbeat.put("shopId", shopId);
        }
        heartbeat.put("deviceId", android.os.Build.MODEL);

        try {
            FirebaseFirestore.getInstance().collection("kiosks")
                    .document(kioskId)
                    .set(heartbeat, SetOptions.merge());
            Log.d(TAG, "Heartbeat synced for " + kioskId);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Heartbeat failed", e);
            return Result.retry();
        }
    }
}
