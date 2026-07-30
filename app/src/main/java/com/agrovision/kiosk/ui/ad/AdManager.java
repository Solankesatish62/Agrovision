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
import java.util.Objects;

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
    private final List<AdModel> ads = new ArrayList<>();
    private String shopBannerUrl = null;
    private String shopDisplayName = null;
    private String shopBrandingType = "text"; // "text" or "poster"
    private long rotationIntervalMs = 60000; // Default 1 minute for photos
    private String adVersion = String.valueOf(System.currentTimeMillis());
    private final List<OnAdUpdateListener> listeners = new ArrayList<>();

    public static class AdModel {
        public String url;
        public String type; // "image" or "video"

        public AdModel(String url, String type) {
            this.url = url;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AdModel adModel = (AdModel) o;
            return Objects.equals(url, adModel.url) && Objects.equals(type, adModel.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(url, type);
        }
    }

    public interface OnAdUpdateListener {
        void onAdsUpdated(List<AdModel> ads, long intervalMs, String version);
        void onShopBannerUpdated(String bannerUrl);
        void onBrandingUpdated(String type, String name, String url);
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
        List<AdModel> newAds = new ArrayList<>();
        long newIntervalMs = 60000; // Default 1 minute
        String newBannerUrl = null;
        String newDisplayName = null;
        String newBrandingType = "text";

        if (doc.exists()) {
            newBannerUrl = doc.getString("shopBannerUrl");
            newDisplayName = doc.getString("shopDisplayName");
            newBrandingType = doc.getString("shopBrandingType");
            if (newBrandingType == null) newBrandingType = "text";

            // 1. Check modern 'ads' field (List of Maps or Map of Maps)
            Object adsObj = doc.get("ads");
            if (adsObj instanceof List) {
                List<Map<String, Object>> adsList = (List<Map<String, Object>>) adsObj;
                for (Map<String, Object> adMap : adsList) {
                    if (isAdActive(adMap)) {
                        addAdFromMap(adMap, newAds);
                    }
                }
            } else if (adsObj instanceof Map) {
                Map<String, Object> adsMap = (Map<String, Object>) adsObj;
                for (Object value : adsMap.values()) {
                    if (value instanceof Map) {
                        Map<String, Object> adMap = (Map<String, Object>) value;
                        if (isAdActive(adMap)) {
                            addAdFromMap(adMap, newAds);
                        }
                    }
                }
            }

            // 2. Check legacy 'ad_list' (List of strings) - assume images
            if (newAds.isEmpty()) {
                Object adListObj = doc.get("ad_list");
                if (adListObj instanceof List) {
                    for (Object item : (List<?>) adListObj) {
                        if (item instanceof String && !((String) item).isEmpty()) {
                            newAds.add(new AdModel((String) item, "image"));
                        }
                    }
                }
            }
            
            // 3. Check 'imageUrl' (Single string)
            if (newAds.isEmpty()) {
                String singleUrl = doc.getString("imageUrl");
                if (singleUrl != null && !singleUrl.isEmpty()) {
                    newAds.add(new AdModel(singleUrl, "image"));
                }
            }

            Long interval = doc.getLong("interval_seconds");
            newIntervalMs = interval != null ? interval * 1000 : 60000;
        } else {
            Log.w(TAG, "Ad document deleted or missing. Clearing local ad queue.");
        }

        // 🚀 Detect changes
        boolean changed = !newAds.equals(ads) || (newIntervalMs != rotationIntervalMs);
        boolean bannerChanged = !Objects.equals(newBannerUrl, shopBannerUrl);

        if (changed) {
            // 🚀 Invalidate Glide cache by changing the signature
            this.adVersion = String.valueOf(System.currentTimeMillis());
            
            Log.i(TAG, "Ad Catalog Sync: " + ads.size() + " -> " + newAds.size() + " ads. Version: " + adVersion);
            
            this.ads.clear();
            this.ads.addAll(newAds);
            this.rotationIntervalMs = newIntervalMs;

            // 🚀 Preload all images with the NEW signature
            for (AdModel ad : ads) {
                if ("image".equals(ad.type)) {
                    Glide.with(context)
                            .load(ad.url)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .signature(new com.bumptech.glide.signature.ObjectKey(adVersion))
                            .preload();
                }
            }

            // 🚀 Notify listeners (AdActivity)
            for (OnAdUpdateListener listener : listeners) {
                listener.onAdsUpdated(new ArrayList<>(ads), rotationIntervalMs, adVersion);
            }
        }

        if (bannerChanged) {
            this.shopBannerUrl = newBannerUrl;
            for (OnAdUpdateListener listener : listeners) {
                listener.onShopBannerUpdated(shopBannerUrl);
            }
        }

        boolean brandingChanged = !Objects.equals(newDisplayName, shopDisplayName) || !Objects.equals(newBrandingType, shopBrandingType);
        if (brandingChanged || bannerChanged) {
            this.shopDisplayName = newDisplayName;
            this.shopBrandingType = newBrandingType;
            for (OnAdUpdateListener listener : listeners) {
                listener.onBrandingUpdated(shopBrandingType, shopDisplayName, shopBannerUrl);
            }
        }
    }

    private void addAdFromMap(Map<String, Object> adMap, List<AdModel> adList) {
        String videoUrl = (String) adMap.get("videoUrl");
        if (videoUrl != null && !videoUrl.isEmpty()) {
            adList.add(new AdModel(videoUrl, "video"));
            return;
        }
        
        String imageUrl = (String) adMap.get("imageUrl");
        if (imageUrl != null && !imageUrl.isEmpty()) {
            adList.add(new AdModel(imageUrl, "image"));
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

    public List<AdModel> getAds() {
        return new ArrayList<>(ads);
    }

    public long getRotationIntervalMs() {
        return rotationIntervalMs;
    }

    public String getShopBannerUrl() {
        return shopBannerUrl;
    }

    public String getShopDisplayName() {
        return shopDisplayName;
    }

    public String getShopBrandingType() {
        return shopBrandingType;
    }

    public String getAdVersion() {
        return adVersion;
    }
}
