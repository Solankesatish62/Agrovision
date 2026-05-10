package com.agrovision.kiosk.ui.ad;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.agrovision.kiosk.R;
import com.agrovision.kiosk.camera.CameraController;
import com.agrovision.kiosk.camera.ScanResultCallback;
import com.agrovision.kiosk.state.AppState;
import com.agrovision.kiosk.state.StateEvent;
import com.agrovision.kiosk.state.StateMachine;
import com.agrovision.kiosk.state.StateObserver;
import com.agrovision.kiosk.util.LogUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.DocumentSnapshot;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * AdActivity
 *
 * Responsibility: Full-screen display of advertisements during idle time.
 */
public final class AdActivity extends AppCompatActivity implements ScanResultCallback, StateObserver {

    private static final String TAG = "AdActivity";
    
    private ImageView ivAd;
    private final Handler rotationHandler = new Handler(Looper.getMainLooper());
    private final List<String> adUrls = new ArrayList<>();
    private int currentAdIndex = 0;
    private long rotationIntervalMs = 10000;
    private boolean isVisible = false;

    // 🚀 Ad Impression Tracking
    private final Map<String, Long> localAdCounts = new HashMap<>();
    private int pendingTotalImpressions = 0;
    private static final int SYNC_THRESHOLD = 5;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ad);
        hideSystemUI();

        ivAd = findViewById(R.id.ivAd);

        // Register as observer immediately to catch transitions
        StateMachine.getInstance(this).addObserver(this);

        setupSilentCamera();
        startAdSync();
    }

    @Override
    protected void onStart() {
        super.onStart();
        isVisible = true;
        startRotation(); // Start rotation only when activity becomes visible
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (StateMachine.getInstance(this).getCurrentState() != AppState.IDLE) {
            Log.i(TAG, "Already in " + StateMachine.getInstance(this).getCurrentState() + " state. Finishing AdActivity.");
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        isVisible = false;
        stopRotation(); // Stop rotation when activity is hidden
        syncAdImpressionsToFirebase(); // Final sync of any pending counts
    }

    private void setupSilentCamera() {
        CameraController controller = CameraController.getInstance(getApplicationContext());
        controller.setScanResultCallback(this);
        controller.startSilentAnalysis(this);
    }

    private void startAdSync() {
        String shopId = getSharedPreferences("kiosk_settings", MODE_PRIVATE)
                .getString("shop_mobile", "910000000000");

        FirebaseFirestore.getInstance().collection("kiosk_ads")
                .document(shopId)
                .addSnapshotListener((value, error) -> {
                    if (error == null && value != null && value.exists()) {
                        updateAdList(value);
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private void updateAdList(DocumentSnapshot doc) {
        List<String> newUrls = (List<String>) doc.get("ad_list");
        Long interval = doc.getLong("interval_seconds");
        
        if (newUrls != null && !newUrls.isEmpty()) {
            // Only restart if the list or interval has actually changed
            if (!newUrls.equals(adUrls) || (interval != null && interval * 1000 != rotationIntervalMs)) {
                adUrls.clear();
                adUrls.addAll(newUrls);
                currentAdIndex = 0;
                if (interval != null) rotationIntervalMs = interval * 1000;
                
                if (isVisible) {
                    startRotation();
                }
            }
        }
    }

    private void startRotation() {
        stopRotation();
        showNextAd();
    }

    private void stopRotation() {
        rotationHandler.removeCallbacksAndMessages(null);
    }

    private void showNextAd() {
        if (!isVisible || adUrls.isEmpty()) return;

        String url = adUrls.get(currentAdIndex);

        if (url != null && url.startsWith("file:///android_asset/")) {
            loadFromAssets(url, ivAd);
        } else {
            Glide.with(this)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.stat_notify_error)
                    .into(ivAd);
        }

        // 🚀 Track Impression ONLY if visible
        recordAdImpression(url);

        currentAdIndex = (currentAdIndex + 1) % adUrls.size();
        rotationHandler.postDelayed(this::showNextAd, rotationIntervalMs);
    }

    private void recordAdImpression(String url) {
        if (!isVisible || url == null) return;
        
        String adId = sanitizeAdId(url);
        localAdCounts.put(adId, localAdCounts.getOrDefault(adId, 0L) + 1);
        pendingTotalImpressions++;

        Log.d(TAG, "Ad impression recorded: " + adId);

        if (pendingTotalImpressions >= SYNC_THRESHOLD) {
            syncAdImpressionsToFirebase();
        }
    }

    private String sanitizeAdId(String url) {
        String adId = url;
        if (url.contains("/")) {
            adId = url.substring(url.lastIndexOf("/") + 1);
        }
        return adId.replace(".", "_").replace("#", "_").replace("$", "_")
                   .replace("[", "_").replace("]", "_").replace("/", "_");
    }

    private void syncAdImpressionsToFirebase() {
        if (localAdCounts.isEmpty()) return;

        String shopId = getSharedPreferences("kiosk_settings", MODE_PRIVATE)
                .getString("shop_mobile", "910000000000");
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        DocumentReference docRef = FirebaseFirestore.getInstance()
                .collection("shops")
                .document(shopId)
                .collection("ad_impressions")
                .document(today);

        Map<String, Long> impressionsToSync = new HashMap<>(localAdCounts);
        localAdCounts.clear();
        pendingTotalImpressions = 0;

        Map<String, Object> updates = new HashMap<>();
        for (Map.Entry<String, Long> entry : impressionsToSync.entrySet()) {
            updates.put(entry.getKey(), FieldValue.increment(entry.getValue()));
        }

        docRef.set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.i(TAG, "Ad impressions synced successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Sync failed", e));
    }

    private void loadFromAssets(String path, ImageView imageView) {
        try {
            String assetPath = path.replace("file:///android_asset/", "");
            InputStream inputStream = getAssets().open(assetPath);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            imageView.setImageBitmap(bitmap);
            inputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Error loading asset image", e);
        }
    }

    @Override
    public void onScanCompleted(List<String> normalizedTexts) {
        Log.i(TAG, "Bottle detected in AdActivity! Waking up...");
        StateMachine.getInstance(this).transition(StateEvent.ACTIVITY_DETECTED);
        runOnUiThread(this::finish);
    }

    @Override
    public void onStateChanged(AppState state) {
        if (state != AppState.IDLE) {
            Log.i(TAG, "State changed to " + state + ". Finishing AdActivity.");
            runOnUiThread(this::finish);
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        StateMachine.getInstance(this).removeObserver(this);
        stopRotation();
    }
}
