package com.agrovision.kiosk.ui.register;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.agrovision.kiosk.R;
import com.agrovision.kiosk.ui.home.HomeActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * RegisterActivity
 *
 * Responsibility: Collect shopkeeper details on first launch and save to Firebase.
 * Ensures the device is linked to a specific shop.
 */
public final class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etFullName, etShopName, etMobile;
    private MaterialButton btnRegister;
    private ProgressBar loadingProgress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupListeners();
    }

    private void initViews() {
        etFullName = findViewById(R.id.etFullName);
        etShopName = findViewById(R.id.etShopName);
        etMobile = findViewById(R.id.etMobile);
        btnRegister = findViewById(R.id.btnRegister);
        loadingProgress = findViewById(R.id.loadingProgress);
    }

    private void setupListeners() {
        btnRegister.setOnClickListener(v -> handleRegistration());
    }

    private void handleRegistration() {
        String name = etFullName.getText().toString().trim();
        String shop = etShopName.getText().toString().trim();
        String mobile = etMobile.getText().toString().trim();

        if (name.isEmpty() || shop.isEmpty() || mobile.length() < 10) {
            Toast.makeText(this, "कृपया सर्व माहिती अचूक भरा", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        // 1. Prepare data for Firebase
        Map<String, Object> shopData = new HashMap<>();
        shopData.put("fullName", name);
        shopData.put("shopName", shop);
        shopData.put("mobile", mobile);
        shopData.put("registeredAt", System.currentTimeMillis());
        shopData.put("isActive", true);

        // 2. Save to Firestore (Collection: shops)
        FirebaseFirestore.getInstance().collection("shops")
                .document(mobile)
                .set(shopData)
                .addOnSuccessListener(aVoid -> {
                    saveLocally(name, shop, mobile);
                    navigateToHome();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "नोंदणी अयशस्वी: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void saveLocally(String name, String shop, String mobile) {
        SharedPreferences prefs = getSharedPreferences("kiosk_settings", MODE_PRIVATE);
        prefs.edit()
                .putString("shop_owner_name", name)
                .putString("shop_name", shop)
                .putString("shop_mobile", mobile)
                .putBoolean("is_registered", true)
                .apply();
    }

    private void showLoading(boolean show) {
        loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!show);
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}
