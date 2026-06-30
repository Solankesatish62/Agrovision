package com.agrovision.kiosk.ui.result;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.agrovision.kiosk.R;

/**
 * UnknownActivity
 *
 * Responsibility: Display information for medicines not found in the catalog.
 */
public final class UnknownActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_unknown);

        hideSystemUI();

        String scrapedName = getIntent().getStringExtra("SCRAPED_NAME");
        TextView tvScrapedName = findViewById(R.id.tvScrapedName);
        if (scrapedName != null && !scrapedName.isEmpty()) {
            tvScrapedName.setText("ओळखले: " + scrapedName);
        } else {
            tvScrapedName.setVisibility(View.GONE);
        }

        findViewById(R.id.btnBackToScan).setOnClickListener(v -> finish());

        // Auto-close after 10 seconds
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 10000);
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }
}
