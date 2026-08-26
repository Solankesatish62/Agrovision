package com.agrovision.kiosk.ui.splash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.agrovision.kiosk.ui.home.HomeActivity;
import com.agrovision.kiosk.ui.register.PairingActivity;

/**
 * SplashActivity
 *
 * Responsibility: Route the user to either HomeActivity (if already registered)
 * or go to PairingActivity.
 */
public final class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("kiosk_settings", MODE_PRIVATE);
        boolean isRegistered = prefs.getBoolean("is_registered", false);
        String shopId = prefs.getString("shop_id", null);

        Log.d(TAG, "Checking registration: isRegistered=" + isRegistered + ", shopId=" + shopId);

        if (isRegistered && shopId != null && !shopId.trim().isEmpty() && !shopId.equals("null")) {
            Log.i(TAG, "Kiosk paired. Launching Home.");
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        } else {
            Log.w(TAG, "Kiosk NOT paired. Forcing Pairing screen.");
            // Clean up potentially inconsistent state
            prefs.edit()
                    .putBoolean("is_registered", false)
                    .remove("shop_id")
                    .apply();
            
            Intent intent = new Intent(this, PairingActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }
}
