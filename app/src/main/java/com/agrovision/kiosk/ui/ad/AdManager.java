package com.agrovision.kiosk.ui.ad;

import android.content.Context;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.agrovision.kiosk.data.database.AppDatabase;
import com.agrovision.kiosk.data.database.entity.AdvertisementEntity;
import com.agrovision.kiosk.sync.SyncManager;
import com.agrovision.kiosk.threading.IoExecutor;

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
        public int duration; // in seconds

        public AdModel(String url, String type, int duration) {
            this.url = url;
            this.type = type;
            this.duration = duration;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AdModel adModel = (AdModel) o;
            return duration == adModel.duration && Objects.equals(url, adModel.url) && Objects.equals(type, adModel.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(url, type, duration);
        }
    }

    public interface OnAdUpdateListener {
        void onAdsUpdated(List<AdModel> ads, long intervalMs, String version);
        void onShopBannerUpdated(String bannerUrl);
        void onBrandingUpdated(String type, String name, String url);
    }

    private AdManager(Context context) {
        this.context = context.getApplicationContext();
        loadAdsFromCache();
        SyncManager.getInstance(context).startSync();
    }

    public static synchronized AdManager getInstance(Context context) {
        if (instance == null) {
            instance = new AdManager(context);
        }
        return instance;
    }

    private void loadAdsFromCache() {
        IoExecutor.submit(() -> {
            long now = System.currentTimeMillis();
            List<AdvertisementEntity> entities = AppDatabase.getInstance(context)
                    .advertisementDao()
                    .getActiveAds(now);
            
            List<AdModel> newAds = new ArrayList<>();
            for (AdvertisementEntity e : entities) {
                String url = "video".equals(e.type) ? e.videoUrl : e.imageUrl;
                newAds.add(new AdModel(url, e.type, e.duration));
            }
            
            updateAdListLocal(newAds);
        });
    }

    public void updateAdsLocal() {
        loadAdsFromCache();
    }

    private void updateAdListLocal(List<AdModel> newAds) {
        if (!newAds.equals(ads)) {
            this.ads.clear();
            this.ads.addAll(newAds);
            this.adVersion = String.valueOf(System.currentTimeMillis());
            
            // Preload
            for (AdModel ad : ads) {
                Glide.with(context)
                        .load(ad.url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .signature(new com.bumptech.glide.signature.ObjectKey(adVersion))
                        .preload();
            }

            for (OnAdUpdateListener listener : listeners) {
                listener.onAdsUpdated(new ArrayList<>(ads), rotationIntervalMs, adVersion);
            }
        }
    }

    // Keeping these for legacy compatibility or future shop branding sync
    private void startAdSync() {
        // ... (can be removed if shop branding is moved to SyncManager)
    }

    private void addAdFromMap(Map<String, Object> adMap, List<AdModel> adList) {
        String videoUrl = (String) adMap.get("videoUrl");
        if (videoUrl != null && !videoUrl.isEmpty()) {
            adList.add(new AdModel(videoUrl, "video", 15));
            return;
        }
        
        String imageUrl = (String) adMap.get("imageUrl");
        if (imageUrl != null && !imageUrl.isEmpty()) {
            adList.add(new AdModel(imageUrl, "image", 15));
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

    public void updateBranding(String type, String name, String url, long intervalMs) {
        this.shopBrandingType = type != null ? type : "text";
        this.shopDisplayName = name;
        this.shopBannerUrl = url;
        this.rotationIntervalMs = intervalMs > 0 ? intervalMs : 60000;

        for (OnAdUpdateListener listener : listeners) {
            listener.onBrandingUpdated(shopBrandingType, shopDisplayName, shopBannerUrl);
            listener.onAdsUpdated(new ArrayList<>(ads), rotationIntervalMs, adVersion);
        }
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
