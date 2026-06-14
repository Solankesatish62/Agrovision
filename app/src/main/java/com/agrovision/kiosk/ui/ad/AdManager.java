package com.agrovision.kiosk.ui.ad;

import android.content.Context;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AdManager
 *
 * Responsibility: Manages the global state of advertisements, 
 * including fetching from Firebase and preloading images.
 */
public final class AdManager {
    private static final String TAG = "AdManager";
    private static AdManager instance;

    private final Context context;
    private final List<String> adUrls = new ArrayList<>();
    private long rotationIntervalMs = 10000;
    private String adVersion = String.valueOf(System.currentTimeMillis());
    private final List<OnAdUpdateListener> listeners = new ArrayList<>();

    public interface OnAdUpdateListener {
        void onAdsUpdated(List<String> urls, long intervalMs, String version);
    }

    private AdManager(Context context) {
        this.context = context.getApplicationContext();
        startAdSync();
    }

    public static synchronized AdManager getInstance(Context context) {
        if (instance == null) {
            instance = new AdManager(context);
        }
        return instance;
    }

    private void startAdSync() {
        String shopId = context.getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE)
                .getString("shop_mobile", "910000000000");

        Log.i(TAG, "Starting Ad Sync for shop: " + shopId);

        FirebaseFirestore.getInstance().collection("kiosk_ads")
                .document(shopId)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Ad Sync Error: " + error.getMessage());
                        return;
                    }
                    // 🚀 Always process the snapshot (to handle deletion/clearance)
                    if (value != null) {
                        updateAdList(value);
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private void updateAdList(DocumentSnapshot doc) {
        List<String> newUrls = new ArrayList<>();
        long newIntervalMs = 10000;

        if (doc.exists()) {
            // 1. Check modern 'ads' field (List of Maps or Map of Maps)
            Object adsObj = doc.get("ads");
            if (adsObj instanceof List) {
                List<Map<String, Object>> adsList = (List<Map<String, Object>>) adsObj;
                for (Map<String, Object> adMap : adsList) {
                    if (isAdActive(adMap)) {
                        String url = (String) adMap.get("imageUrl");
                        if (url != null && !url.isEmpty() && !newUrls.contains(url)) {
                            newUrls.add(url);
                        }
                    }
                }
            } else if (adsObj instanceof Map) {
                Map<String, Object> adsMap = (Map<String, Object>) adsObj;
                // Support both indexed map "0", "1"... and arbitrary keys
                for (Object value : adsMap.values()) {
                    if (value instanceof Map) {
                        Map<String, Object> adMap = (Map<String, Object>) value;
                        if (isAdActive(adMap)) {
                            String url = (String) adMap.get("imageUrl");
                            if (url != null && !url.isEmpty() && !newUrls.contains(url)) {
                                newUrls.add(url);
                            }
                        }
                    }
                }
            }

            // 2. Check legacy 'ad_list' (List of strings)
            // ONLY if modern ads are empty to avoid "zombie" ads that were deleted from one but exist in other
            if (newUrls.isEmpty()) {
                Object adListObj = doc.get("ad_list");
                if (adListObj instanceof List) {
                    for (Object item : (List<?>) adListObj) {
                        if (item instanceof String && !((String) item).isEmpty() && !newUrls.contains(item)) {
                            newUrls.add((String) item);
                        }
                    }
                }
            }
            
            // 3. Check 'imageUrl' (Single string)
            if (newUrls.isEmpty()) {
                String singleUrl = doc.getString("imageUrl");
                if (singleUrl != null && !singleUrl.isEmpty()) {
                    newUrls.add(singleUrl);
                }
            }

            Long interval = doc.getLong("interval_seconds");
            newIntervalMs = interval != null ? interval * 1000 : 10000;
        } else {
            Log.w(TAG, "Ad document deleted or missing. Clearing local ad queue.");
        }

        // 🚀 Detect changes
        boolean changed = !newUrls.equals(adUrls) || (newIntervalMs != rotationIntervalMs);

        if (changed) {
            // 🚀 Invalidate Glide cache by changing the signature
            this.adVersion = String.valueOf(System.currentTimeMillis());
            
            Log.i(TAG, "Ad Catalog Sync: " + adUrls.size() + " -> " + newUrls.size() + " ads. Version: " + adVersion);
            
            this.adUrls.clear();
            this.adUrls.addAll(newUrls);
            this.rotationIntervalMs = newIntervalMs;

            // 🚀 Preload all with the NEW signature
            for (String url : adUrls) {
                Glide.with(context)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .signature(new com.bumptech.glide.signature.ObjectKey(adVersion))
                        .preload();
            }

            // 🚀 Notify listeners (AdActivity)
            for (OnAdUpdateListener listener : listeners) {
                listener.onAdsUpdated(new ArrayList<>(adUrls), rotationIntervalMs, adVersion);
            }
        }
    }

    private boolean isAdActive(Map<String, Object> adMap) {
        Object activeVal = adMap.get("active");
        if (activeVal == null) return true;
        if (activeVal instanceof Boolean) return (Boolean) activeVal;
        if (activeVal instanceof String) return Boolean.parseBoolean((String) activeVal);
        return true;
    }

    public void addListener(OnAdUpdateListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(OnAdUpdateListener listener) {
        listeners.remove(listener);
    }

    public List<String> getAdUrls() {
        return new ArrayList<>(adUrls);
    }

    public long getRotationIntervalMs() {
        return rotationIntervalMs;
    }

    public String getAdVersion() {
        return adVersion;
    }
}
