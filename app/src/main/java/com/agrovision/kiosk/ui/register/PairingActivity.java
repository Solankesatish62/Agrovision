package com.agrovision.kiosk.ui.register;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.agrovision.kiosk.R;
import com.agrovision.kiosk.app.KioskIdentity;
import com.agrovision.kiosk.ui.home.HomeActivity;
import com.agrovision.kiosk.util.LogUtils;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public final class PairingActivity extends AppCompatActivity {
    private static final String TAG = "PairingActivity";
    private ListenerRegistration registration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pairing);

        String kioskId = KioskIdentity.getKioskId(this);
        String pairingCode = KioskIdentity.getPairingCode(this);

        ((TextView) findViewById(R.id.tvKioskId)).setText(kioskId);
        ((TextView) findViewById(R.id.tvPairingCode)).setText(pairingCode);

        // 🚀 START HANDSHAKE: Reset cloud document first, then start listening
        uploadPairingInfo(kioskId, pairingCode);
    }

    private void uploadPairingInfo(String kioskId, String pairingCode) {
        Map<String, Object> data = new HashMap<>();
        data.put("kioskId", kioskId);
        data.put("pairingCode", pairingCode);
        data.put("status", "PENDING");
        data.put("lastActiveTimestamp", System.currentTimeMillis());

        Log.i(TAG, "🚀 INITIALIZING PAIRING HANDSHAKE...");
        Log.i(TAG, "Device ID: " + kioskId);
        Log.i(TAG, "Pairing Code: " + pairingCode);

        // 🚀 CRITICAL: Use an absolute overwrite (NOT merge) to ensure no old shopId lingers
        FirebaseFirestore.getInstance().collection("kiosks")
                .document(kioskId)
                .set(data) // 🚩 NO MERGE - Wipe document clean with fresh PENDING state
                .addOnSuccessListener(aVoid -> {
                    Log.i(TAG, "☁️ Cloud state reset to PENDING. Starting listener...");
                    listenForPairing(kioskId);
                })
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to reset cloud state", e));
    }

    private void listenForPairing(String kioskId) {
        DocumentReference docRef = FirebaseFirestore.getInstance().collection("kiosks").document(kioskId);
        registration = docRef.addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                LogUtils.e("Listen failed", e);
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                Map<String, Object> data = snapshot.getData();
                Log.i(TAG, "📡 Cloud Snapshot Received: " + (data != null ? data.toString() : "null"));
                
                String status = snapshot.getString("status");
                String shopId = snapshot.getString("shopId");
                String shopName = snapshot.getString("shopName");
                
                // 🚀 SECURITY: Only proceed if status is 'active', 'ONLINE' or 'PAIRED'
                // This covers all possible success statuses from portal or heartbeats
                boolean isPaired = "active".equals(status) || "ONLINE".equals(status) || "PAIRED".equals(status);

                if (isPaired && shopId != null && !shopId.trim().isEmpty()) {
                    Log.i(TAG, "✅ PAIRING SUCCESSFUL! Moving to Home Screen.");
                    completeRegistration(kioskId, shopId, shopName);
                } else {
                    Log.d(TAG, "⏳ Waiting for Portal link... Current Cloud Status: " + (status != null ? status : "EMPTY"));
                }
            }
        });
    }

    private void completeRegistration(String kioskId, String shopId, String shopName) {
        SharedPreferences prefs = getSharedPreferences("kiosk_settings", MODE_PRIVATE);
        prefs.edit()
                .putString("kiosk_id", kioskId)
                .putString("shop_id", shopId)
                .putString("shop_name", shopName != null ? shopName : "My Shop")
                .putBoolean("is_registered", true)
                .apply();

        if (registration != null) registration.remove();

        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        // 🚀 Lock: Block BACK and HOME keys during pairing
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK || keyCode == android.view.KeyEvent.KEYCODE_HOME) {
                Log.d(TAG, "Critical key pressed. Blocking navigation.");
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (registration != null) registration.remove();
    }
}
