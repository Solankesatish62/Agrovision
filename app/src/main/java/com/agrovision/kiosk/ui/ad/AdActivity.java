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
import com.agrovision.kiosk.state.AppState;
import com.agrovision.kiosk.state.StateEvent;
import com.agrovision.kiosk.state.StateMachine;
import com.agrovision.kiosk.state.StateObserver;
import com.agrovision.kiosk.util.LogUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AdActivity
 *
 * Responsibility: Full-screen display of advertisements during idle time.
 * Optimized for instant display using global AdManager preloading and real-time sync.
 */
public final class AdActivity extends AppCompatActivity implements StateObserver, AdManager.OnAdUpdateListener {

    private static final String TAG = "AdActivity";
    public static final String EXTRA_AD_TYPE = "ad_type";
    public static final String EXTRA_AD_DURATION = "ad_duration";
    
    public enum AdType { SCAN, IDLE }

    private ImageView ivAd;
    private final Handler rotationHandler = new Handler(Looper.getMainLooper());
    private final Handler completionHandler = new Handler(Looper.getMainLooper());
    private final List<String> adUrls = new ArrayList<>();
    private int currentAdIndex = 0;
    private long rotationIntervalMs = 10000;
    private boolean isVisible = false;
    private AdType currentType = AdType.IDLE;
    private String adVersion = "";
    private boolean timerStarted = false;

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
        // 🚀 Neutral branded loading state
        ivAd.setImageResource(R.drawable.logo_agrovision);
        ivAd.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        currentType = (AdType) getIntent().getSerializableExtra(EXTRA_AD_TYPE);
        if (currentType == null) currentType = AdType.IDLE;

        LogUtils.i("AdActivity started. Type: " + currentType);

        StateMachine.getInstance(this).addObserver(this);
        AdManager.getInstance(this).addListener(this);
        CameraController.getInstance(this).setDetectionEnabled(false);
        
        // 🚀 Load catalog from shared AdManager
        loadFromAdManager();
    }

    @Override
    public void onAdsUpdated(List<String> urls, long intervalMs, String version) {
        Log.i(TAG, "Ad catalog updated in real-time. Refreshing queue.");
        runOnUiThread(() -> {
            this.adUrls.clear();
            this.adUrls.addAll(urls);
            this.rotationIntervalMs = intervalMs;
            this.adVersion = version;

            if (adUrls.isEmpty()) {
                Log.w(TAG, "All ads removed or deactivated. Finishing.");
                completeAd();
                return;
            }

            // If current ad index is now invalid, reset
            if (currentAdIndex >= adUrls.size()) {
                currentAdIndex = 0;
            }

            // Force immediate refresh of current display
            showNextAd();
        });
    }

    private void loadFromAdManager() {
        AdManager adManager = AdManager.getInstance(this);
        List<String> urls = adManager.getAdUrls();
        
        if (urls.isEmpty()) {
            Log.w(TAG, "No ads available in AdManager. Finishing.");
            finish();
            return;
        }

        this.adUrls.clear();
        this.adUrls.addAll(urls);
        this.rotationIntervalMs = adManager.getRotationIntervalMs();
        this.adVersion = adManager.getAdVersion();

        this.currentAdIndex = getPersistedAdIndex();
        if (this.currentAdIndex >= adUrls.size()) this.currentAdIndex = 0;

        Log.i(TAG, "Ad catalog loaded from Manager. Total: " + adUrls.size() + " Index: " + currentAdIndex);
    }

    private void completeAd() {
        LogUtils.i("Ad duration reached. Returning to Home Screen.");
        StateMachine.getInstance(this).transition(StateEvent.AD_COMPLETED);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onStart() {
        super.onStart();
        isVisible = true;
        startRotation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppState state = StateMachine.getInstance(this).getCurrentState();
        if (state != AppState.IDLE && state != AppState.IDLE_AD && 
            state != AppState.SCAN_AD && state != AppState.SCANNING) {
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        isVisible = false;
        stopRotation();
        syncAdImpressionsToFirebase();
        CameraController.getInstance(this).setDetectionEnabled(true);
    }

    private int getPersistedAdIndex() {
        return getSharedPreferences("ad_rotation_state", MODE_PRIVATE)
                .getInt("last_ad_index", 0);
    }

    private void savePersistedAdIndex(int index) {
        getSharedPreferences("ad_rotation_state", MODE_PRIVATE)
                .edit()
                .putInt("last_ad_index", index)
                .apply();
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

        if (currentAdIndex >= adUrls.size()) currentAdIndex = 0;

        String url = adUrls.get(currentAdIndex);
        Log.i(TAG, "Displaying Ad [" + (currentAdIndex + 1) + "/" + adUrls.size() + "]: " + url);

        // 🚀 Reset rotation timer
        rotationHandler.removeCallbacksAndMessages(null);

        if (url != null && url.startsWith("file:///android_asset/")) {
            loadFromAssets(url, ivAd);
            onAdImageVisible(url);
        } else if (url != null && (url.startsWith("http") || url.startsWith("https"))) {
            ivAd.setScaleType(ImageView.ScaleType.CENTER_CROP);
            
            Glide.with(this)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .signature(new ObjectKey(adVersion))
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Ad image load failed: " + url);
                            if (adUrls.size() > 1) {
                                currentAdIndex = (currentAdIndex + 1) % adUrls.size();
                                showNextAd();
                            } else {
                                completeAd();
                            }
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, Target<android.graphics.drawable.Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            Log.i(TAG, "Ad image ready: " + url);
                            onAdImageVisible(url);
                            return false;
                        }
                    })
                    .into(ivAd);
        }

        // 🚀 Preload the NEXT ad in background
        if (adUrls.size() > 1) {
            int nextIndex = (currentAdIndex + 1) % adUrls.size();
            String nextUrl = adUrls.get(nextIndex);
            Glide.with(this)
                    .load(nextUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .signature(new ObjectKey(adVersion))
                    .preload();
        }
    }

    private void onAdImageVisible(String url) {
        recordAdImpression(url);

        // 🚀 Session countdown starts ONLY after first image is visible
        if (!timerStarted) {
            timerStarted = true;
            long durationMs = getIntent().getLongExtra(EXTRA_AD_DURATION, 10000);
            completionHandler.postDelayed(this::completeAd, durationMs);
            Log.d(TAG, "Ad session timer started: " + durationMs + "ms");
        }

        // 🚀 Rotation timer starts ONLY after current image is visible
        if (adUrls.size() > 1) {
            rotationHandler.postDelayed(() -> {
                if (!isVisible) return;
                currentAdIndex = (currentAdIndex + 1) % adUrls.size();
                savePersistedAdIndex(currentAdIndex);
                showNextAd();
            }, rotationIntervalMs);
        }
    }

    private void recordAdImpression(String url) {
        if (!isVisible || url == null) return;
        
        String adId = sanitizeAdId(url);
        localAdCounts.put(adId, localAdCounts.getOrDefault(adId, 0L) + 1);
        pendingTotalImpressions++;

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

        String docId = shopId + "_" + today;
        DocumentReference docRef = FirebaseFirestore.getInstance()
                .collection("ad_impressions")
                .document(docId);

        Map<String, Long> impressionsToSync = new HashMap<>(localAdCounts);
        localAdCounts.clear();
        pendingTotalImpressions = 0;

        Map<String, Object> updates = new HashMap<>();
        for (Map.Entry<String, Long> entry : impressionsToSync.entrySet()) {
            updates.put(entry.getKey(), FieldValue.increment(entry.getValue()));
        }
        
        updates.put("shopId", shopId);
        updates.put("date", today);
        updates.put("lastUpdated", FieldValue.serverTimestamp());

        docRef.set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.i(TAG, "Ad impressions synced"))
                .addOnFailureListener(e -> Log.e(TAG, "Impression sync failed", e));
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
    public void onStateChanged(AppState state) {
        if (state != AppState.IDLE && state != AppState.IDLE_AD && state != AppState.SCAN_AD) {
            completionHandler.removeCallbacksAndMessages(null);
            runOnUiThread(() -> {
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
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
        AdManager.getInstance(this).removeListener(this);
        stopRotation();
        completionHandler.removeCallbacksAndMessages(null);
    }
}
