package com.agrovision.kiosk.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.agrovision.kiosk.data.database.AppDatabase;
import com.agrovision.kiosk.data.database.dao.AdvertisementDao;
import com.agrovision.kiosk.data.database.entity.AdvertisementEntity;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SyncManager {
    private static final String TAG = "SyncManager";
    private static SyncManager instance;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private com.google.firebase.firestore.ListenerRegistration shopListener;

    private SyncManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized SyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new SyncManager(context);
        }
        return instance;
    }

    public void startSync() {
        executor.execute(this::performSync);
    }

    private void performSync() {
        SharedPreferences prefs = context.getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE);
        String kioskId = prefs.getString("kiosk_id", null);
        
        if (kioskId == null) {
            Log.w(TAG, "Sync skipped: Kiosk not paired (no kiosk_id).");
            return;
        }

        // 1. Refresh Shop ID and Configuration
        FirebaseFirestore.getInstance()
                .collection("kiosks")
                .document(kioskId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String shopId = doc.getString("shopId");
                        if (shopId != null) {
                            prefs.edit().putString("shop_id", shopId).apply();
                            Log.i(TAG, "Syncing data for Shop: " + shopId);
                            setupRealtimeSync(shopId);
                            syncShopData(shopId);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Kiosk config fetch failed", e));
    }

    private void setupRealtimeSync(String shopId) {
        if (shopListener != null) return; // Already listening

        Log.i(TAG, "Setting up real-time sync for shop: " + shopId);
        shopListener = FirebaseFirestore.getInstance()
                .collection("shops")
                .document(shopId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Shop listen failed", e);
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        Log.i(TAG, "Shop data changed (Real-time). Triggering sync...");
                        syncShopData(shopId);
                    }
                });
    }

    private void syncShopData(String shopId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // 1. Fetch Shop Metadata (for branding)
        db.collection("shops").document(shopId).get().addOnSuccessListener(shopDoc -> {
            if (!shopDoc.exists()) {
                Log.w(TAG, "Shop " + shopId + " no longer exists. Triggering kiosk reset.");
                resetKiosk();
                return;
            }

            // 2. Fetch Portal Ads
            db.collection("shops").document(shopId).collection("advertisements")
                    .whereEqualTo("deleted", false)
                    .get()
                    .addOnSuccessListener(portalSnap -> {
                        // 3. Fetch Admin Ads (and legacy branding)
                        db.collection("kiosk_ads").document(shopId).get()
                                .addOnSuccessListener(adminDoc -> {
                                    executor.execute(() -> processUnifiedSync(portalSnap, adminDoc, shopDoc));
                                });
                    });
        }).addOnFailureListener(e -> updateSyncError(e.getMessage()));
    }

    private void resetKiosk() {
        Log.e(TAG, "CRITICAL: Kiosk de-pairing requested. Clearing all local data.");
        
        // 1. Clear SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE);
        prefs.edit()
                .remove("kiosk_id")
                .remove("shop_id")
                .remove("shop_mobile")
                .remove("shop_name")
                .remove("is_registered")
                .apply();

        // 2. Clear Local Database (Ads, Medicines)
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                db.advertisementDao().deleteAll();
                db.medicineDao().deleteAll();
                Log.i(TAG, "Local database wiped successfully.");
            } catch (Exception e) {
                Log.e(TAG, "Database wipe failed", e);
            }
        });

        // 3. Close the Shop Snapshot Listener
        if (shopListener != null) {
            shopListener.remove();
            shopListener = null;
        }

        // Note: The UI should ideally react to the missing kiosk_id and show the pairing screen.
        // For now, this ensures security.
    }

    private void processUnifiedSync(QuerySnapshot portalSnap, DocumentSnapshot adminDoc, DocumentSnapshot shopDoc) {
        try {
            List<AdvertisementEntity> entities = new ArrayList<>();
            List<String> activeIds = new ArrayList<>();

            // 1. Process Branding (Prioritize Shopkeeper Settings from shopDoc)
            long interval = 60000;
            String type = null;
            String name = null;
            String url = null;

            if (shopDoc.exists()) {
                type = shopDoc.getString("shopBrandingType");
                name = shopDoc.getString("shopDisplayName");
                url = shopDoc.getString("shopBannerUrl");
            }

            // Fallback to Admin Doc for branding if Shopkeeper hasn't set it, and get rotation interval
            if (adminDoc.exists()) {
                Long intervalSec = adminDoc.getLong("interval_seconds");
                if (intervalSec != null) interval = intervalSec * 1000;
                
                if (type == null) type = adminDoc.getString("shopBrandingType");
                if (name == null) name = adminDoc.getString("shopDisplayName");
                if (url == null) url = adminDoc.getString("shopBannerUrl");
            }
            
            com.agrovision.kiosk.ui.ad.AdManager.getInstance(context)
                    .updateBranding(type, name, url, interval);

            // 2. Process Admin Ads Array
            if (adminDoc.exists()) {
                List<java.util.Map<String, Object>> adminAds = (List<java.util.Map<String, Object>>) adminDoc.get("ads");
                if (adminAds != null) {
                    for (int i = 0; i < adminAds.size(); i++) {
                        java.util.Map<String, Object> adMap = adminAds.get(i);
                        Boolean active = (Boolean) adMap.get("active");
                        if (active == null) active = true;
                        if (!active) continue;

                        String imageUrl = (String) adMap.get("imageUrl");
                        if (imageUrl == null || imageUrl.isEmpty()) continue;

                        String adId = "admin_" + i + "_" + Math.abs(imageUrl.hashCode());
                        activeIds.add(adId);
                        entities.add(new AdvertisementEntity(
                                adId,
                                (String) adMap.get("title"),
                                "Admin",
                                imageUrl,
                                "", // No video for admin ads yet
                                "image",
                                0, // Start now
                                System.currentTimeMillis() + 31536000000L, // End in 1 year
                                1, // Priority
                                15, // Default duration
                                true
                        ));
                    }
                }
            }

            // 2. Process Portal Ads
            for (DocumentSnapshot doc : portalSnap.getDocuments()) {
                String id = doc.getId();
                activeIds.add(id);
                
                Boolean enabled = doc.getBoolean("enabled");
                if (enabled == null) enabled = true;

                String adType = doc.getString("type");
                if (adType == null) adType = "image";

                Long durationLong = doc.getLong("duration");
                int duration = (durationLong != null) ? durationLong.intValue() : 15;

                Long priorityLong = doc.getLong("priority");
                int priority = (priorityLong != null) ? priorityLong.intValue() : 2;

                Timestamp startTs = doc.getTimestamp("startAt");
                long startAt = (startTs != null) ? startTs.toDate().getTime() : System.currentTimeMillis();

                Timestamp endTs = doc.getTimestamp("endAt");
                long endAt = (endTs != null) ? endTs.toDate().getTime() : System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000);

                entities.add(new AdvertisementEntity(
                        id,
                        doc.getString("name"),
                        doc.getString("advertiserName"),
                        doc.getString("imageUrl"),
                        doc.getString("videoUrl"),
                        adType,
                        startAt,
                        endAt,
                        priority,
                        duration,
                        enabled
                ));
            }

            AdvertisementDao dao = AppDatabase.getInstance(context).advertisementDao();
            dao.insertAll(entities);
            dao.keepOnly(activeIds);

            // 🚀 Force AdManager to refresh its in-memory list from the updated database
            com.agrovision.kiosk.ui.ad.AdManager.getInstance(context).updateAdsLocal();
            
            Log.i(TAG, "Unified ad synchronization complete. " + entities.size() + " ads active.");
            updateLastSyncTimestamp("SUCCESS", null);
        } catch (Exception e) {
            Log.e(TAG, "Unified sync failed", e);
            updateSyncError(e.getMessage());
        }
    }

    private void updateSyncError(String error) {
        updateLastSyncTimestamp("FAILED", error);
    }

    private void updateLastSyncTimestamp(String status, String error) {
        SharedPreferences prefs = context.getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE);
        String kioskId = prefs.getString("kiosk_id", null);
        if (kioskId == null) return;

        com.google.firebase.Timestamp now = com.google.firebase.Timestamp.now();
        
        java.util.Map<String, Object> update = new java.util.HashMap<>();
        update.put("lastSyncAt", now);
        update.put("lastSyncStatus", status);
        update.put("lastSyncError", error);

        FirebaseFirestore.getInstance()
                .collection("kiosks")
                .document(kioskId)
                .update(update);
    }
}
