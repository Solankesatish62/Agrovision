package com.agrovision.kiosk.ui.splash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.agrovision.kiosk.ui.home.HomeActivity;
import com.agrovision.kiosk.ui.register.RegisterActivity;

/**
 * SplashActivity
 *
 * Responsibility: Route the user to either RegisterActivity (if new)
 * or HomeActivity (if already registered).
 */
public final class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("kiosk_settings", MODE_PRIVATE);
        boolean isRegistered = prefs.getBoolean("is_registered", false);

        if (isRegistered) {
            startActivity(new Intent(this, HomeActivity.class));
        } else {
            startActivity(new Intent(this, RegisterActivity.class));
        }
        finish();
    }
}
