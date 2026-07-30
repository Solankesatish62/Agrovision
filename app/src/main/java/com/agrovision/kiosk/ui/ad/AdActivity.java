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
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.agrovision.kiosk.R;
import com.agrovision.kiosk.camera.CameraController;
import com.agrovision.kiosk.state.AppState;
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
 * Responsibility: Full-screen display of advertisements.
 * Now supports both images (1 min) and videos (duration) in a slideshow.
 */
public final class AdActivity extends AppCompatActivity implements StateObserver, AdManager.OnAdUpdateListener {

    private static final String TAG = "AdActivity";
    public static final String EXTRA_AD_TYPE = "ad_type";
    
    public enum AdType { SCAN, IDLE, SLIDESHOW }

    private ImageView ivAd;
    private VideoView vvAd;
    private View btnPrevAd;
    private View btnNextAd;
    private final Handler rotationHandler = new Handler(Looper.getMainLooper());
    private final List<AdManager.AdModel> ads = new ArrayList<>();
    private int currentAdIndex = 0;
    private long rotationIntervalMs = 60000; // 1 minute default for photos
    private boolean isVisible = false;
    private AdType currentType = AdType.SLIDESHOW;
    private String adVersion = "";

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
        vvAd = findViewById(R.id.vvAd);
        btnPrevAd = findViewById(R.id.btnPrevAd);
        btnNextAd = findViewById(R.id.btnNextAd);
        
        // 🚀 Neutral branded loading state
        ivAd.setImageResource(R.drawable.logo_agrovision);
        ivAd.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        currentType = (AdType) getIntent().getSerializableExtra(EXTRA_AD_TYPE);
        if (currentType == null) currentType = AdType.SLIDESHOW;

        LogUtils.i("AdActivity started. Type: " + currentType);

        StateMachine.getInstance(this).addObserver(this);
        AdManager.getInstance(this).addListener(this);
        CameraController.getInstance(this).setDetectionEnabled(false);
        
        findViewById(R.id.tvAdBadge).setOnClickListener(v -> finish()); // Allow closing slideshow

        btnNextAd.setOnClickListener(v -> moveToNextAd());
        btnPrevAd.setOnClickListener(v -> moveToPrevAd());

        // 🚀 Load catalog from shared AdManager
        loadFromAdManager();
    }

    @Override
    public void onAdsUpdated(List<AdManager.AdModel> updatedAds, long intervalMs, String version) {
        Log.i(TAG, "Ad catalog updated in real-time. Refreshing queue.");
        runOnUiThread(() -> {
            this.ads.clear();
            this.ads.addAll(updatedAds);
            this.rotationIntervalMs = intervalMs;
            this.adVersion = version;

            if (ads.isEmpty()) {
                Log.w(TAG, "All ads removed or deactivated. Finishing.");
                finish();
                return;
            }

            if (currentAdIndex >= ads.size()) {
                currentAdIndex = 0;
            }

            // Update arrow visibility
            int navVisibility = ads.size() > 1 ? View.VISIBLE : View.GONE;
            btnNextAd.setVisibility(navVisibility);
            btnPrevAd.setVisibility(navVisibility);

            showNextAd();
        });
    }

    @Override
    public void onShopBannerUpdated(String bannerUrl) {
        // No-op: Full-screen ad slideshow doesn't show the shop banner.
        // The banner is handled by HomeActivity.
    }

    @Override
    public void onBrandingUpdated(String type, String name, String url) {
        // No-op: Full-screen ad slideshow doesn't show the branding header.
    }

    private void loadFromAdManager() {
        AdManager adManager = AdManager.getInstance(this);
        List<AdManager.AdModel> availableAds = adManager.getAds();
        
        if (availableAds.isEmpty()) {
            Log.w(TAG, "No ads available in AdManager. Finishing.");
            finish();
            return;
        }

        this.ads.clear();
        this.ads.addAll(availableAds);
        this.rotationIntervalMs = adManager.getRotationIntervalMs();
        this.adVersion = adManager.getAdVersion();

        this.currentAdIndex = 0;

        // Hide navigation if only one ad exists
        int navVisibility = ads.size() > 1 ? View.VISIBLE : View.GONE;
        btnNextAd.setVisibility(navVisibility);
        btnPrevAd.setVisibility(navVisibility);

        Log.i(TAG, "Ad catalog loaded from Manager. Total: " + ads.size());
    }

    @Override
    protected void onStart() {
        super.onStart();
        isVisible = true;
        startRotation();
    }

    @Override
    protected void onStop() {
        super.onStop();
        isVisible = false;
        stopRotation();
        syncAdImpressionsToFirebase();
        CameraController.getInstance(this).setDetectionEnabled(true);
    }

    private void startRotation() {
        stopRotation();
        showNextAd();
    }

    private void stopRotation() {
        rotationHandler.removeCallbacksAndMessages(null);
        if (vvAd != null) {
            vvAd.stopPlayback();
        }
    }

    private void showNextAd() {
        if (!isVisible || ads.isEmpty()) return;

        if (currentAdIndex >= ads.size()) currentAdIndex = 0;

        AdManager.AdModel ad = ads.get(currentAdIndex);
        Log.i(TAG, "Displaying Ad [" + (currentAdIndex + 1) + "/" + ads.size() + "]: " + ad.url + " (" + ad.type + ")");

        rotationHandler.removeCallbacksAndMessages(null);

        if ("video".equals(ad.type)) {
            playVideoAd(ad);
        } else {
            showImageAd(ad);
        }
    }

    private void showImageAd(AdManager.AdModel ad) {
        vvAd.setVisibility(View.GONE);
        ivAd.setVisibility(View.VISIBLE);
        ivAd.setScaleType(ImageView.ScaleType.CENTER_CROP);

        Glide.with(this)
                .load(ad.url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .signature(new ObjectKey(adVersion))
                .transition(DrawableTransitionOptions.withCrossFade())
                .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                        Log.e(TAG, "Ad image load failed: " + ad.url);
                        moveToNextAd();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, Target<android.graphics.drawable.Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        Log.i(TAG, "Ad image ready: " + ad.url);
                        recordAdImpression(ad.url);
                        
                        // 🚀 Rotation timer for images (1 minute or as configured)
                        rotationHandler.postDelayed(() -> moveToNextAd(), rotationIntervalMs);
                        return false;
                    }
                })
                .into(ivAd);
    }

    private void playVideoAd(AdManager.AdModel ad) {
        ivAd.setVisibility(View.GONE);
        vvAd.setVisibility(View.VISIBLE);

        vvAd.setVideoPath(ad.url);
        vvAd.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            vvAd.start();
            recordAdImpression(ad.url);
        });

        vvAd.setOnCompletionListener(mp -> {
            Log.i(TAG, "Video ad completed: " + ad.url);
            moveToNextAd();
        });

        vvAd.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "Video ad error: " + ad.url + " what=" + what + " extra=" + extra);
            moveToNextAd();
            return true;
        });
    }

    private void moveToNextAd() {
        if (!isVisible || ads.isEmpty()) return;
        currentAdIndex = (currentAdIndex + 1) % ads.size();
        showNextAd();
    }

    private void moveToPrevAd() {
        if (!isVisible || ads.isEmpty()) return;
        currentAdIndex = (currentAdIndex - 1 + ads.size()) % ads.size();
        showNextAd();
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

    @Override
    public void onStateChanged(AppState state) {
        // No-op: Slideshow remains visible until manually closed by shopkeeper
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
    }
}
