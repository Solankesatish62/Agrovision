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
        String shopId = prefs.getString("shop_mobile", null);
        String shopName = prefs.getString("shop_name", "Unknown");

        if (shopId == null) {
            return Result.success(); // Not registered yet
        }

        Map<String, Object> heartbeat = new HashMap<>();
        heartbeat.put("lastActiveTimestamp", System.currentTimeMillis());
        heartbeat.put("appVersion", BuildConfig.VERSION_NAME);
        heartbeat.put("shopName", shopName);
        // Do NOT set status here, let KioskStatusManager handle the lifecycle-based status.
        // Or set it to ONLINE only if we are absolutely sure the app is "Live".
        // For now, we update the timestamp but don't force ONLINE if it was set to OFFLINE.
        heartbeat.put("deviceId", android.os.Build.MODEL);

        try {
            FirebaseFirestore.getInstance().collection("kiosks")
                    .document(shopId)
                    .set(heartbeat, SetOptions.merge());
            Log.d(TAG, "Heartbeat synced for " + shopId);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Heartbeat failed", e);
            return Result.retry();
        }
    }
}
