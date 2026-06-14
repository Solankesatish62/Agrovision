package com.agrovision.kiosk.data.repository;

import android.content.Context;
import android.util.Log;

import com.agrovision.kiosk.data.database.AppDatabase;
import com.agrovision.kiosk.data.database.dao.MedicineDao;
import com.agrovision.kiosk.data.database.entity.MedicineEntity;
import com.agrovision.kiosk.data.database.entity.UnknownDetectionEntity;
import com.agrovision.kiosk.data.mapper.MedicineMapper;
import com.agrovision.kiosk.data.model.Medicine;
import com.agrovision.kiosk.threading.IoExecutor;
import com.agrovision.kiosk.util.LogUtils;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MedicineRepository {
    private static final String TAG = "MedicineRepository";
    private static volatile MedicineRepository INSTANCE;
    
    private final Context appContext;
    private final MedicineDao medicineDao;
    private final FirebaseFirestore firestore;
    
    private volatile List<Medicine> cachedCatalog = new ArrayList<>();
    private OnCatalogUpdateListener updateListener;

    public interface OnCatalogUpdateListener {
        void onCatalogUpdated(List<Medicine> newCatalog);
    }

    public static MedicineRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (MedicineRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MedicineRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    private MedicineRepository(Context appContext) {
        this.appContext = appContext;
        AppDatabase db = AppDatabase.getInstance(appContext);
        this.medicineDao = db.medicineDao();
        
        this.firestore = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build();
        this.firestore.setFirestoreSettings(settings);

        // 🚀 SYNC STRATEGY:
        // 1. Load what we have in DB immediately (warm cache)
        loadCatalogFromRoom();

        // 2. Always sync with JSON assets (source of truth for static data)
        // This ensures deleted medicines in JSON are removed from DB
        loadCatalogFromAssets();

        // 3. Start background sync with Firebase
        startRealtimeSync();
    }

    public void setOnCatalogUpdateListener(OnCatalogUpdateListener listener) {
        this.updateListener = listener;
    }

    private void loadCatalogFromRoom() {
        IoExecutor.submit(() -> {
            try {
                List<MedicineEntity> entities = medicineDao.getAll();
                updateCacheFromEntities(entities);
                Log.i(TAG, "Database: Initialized. Loaded " + cachedCatalog.size() + " total items from Room.");

                if (updateListener != null) {
                    updateListener.onCatalogUpdated(cachedCatalog);
                }
            } catch (Exception e) {
                Log.e(TAG, "Database: Failed to load from Room.", e);
            }
        });
    }

    private void loadCatalogFromAssets() {
        IoExecutor.submit(() -> {
            try {
                Log.d(TAG, "Assets: Loading medicines.json...");
                InputStream is = appContext.getAssets().open("data/medicines.json");
                String json = readFully(is);
                JSONArray array = new JSONArray(json);
                List<Medicine> medicines = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    medicines.add(parseMedicine(obj));
                }
                Log.i(TAG, "Assets: Loaded " + medicines.size() + " items from JSON.");
                syncToDatabase(medicines, false); // false = LOCAL source
            } catch (Exception e) {
                Log.e(TAG, "Assets: Failed to load/sync fallback data.", e);
            }
        });
    }

    private void startRealtimeSync() {
        Log.i(TAG, "Firebase: Starting sync with 'approved_medicines'...");
        
        firestore.collection("approved_medicines")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Firebase: Sync Listener Error: " + error.getMessage());
                        return;
                    }

                    if (value != null) {
                        List<Medicine> remoteMedicines = new ArrayList<>();
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Medicine m = docToMedicine(doc);
                            if (m != null) remoteMedicines.add(m);
                        }

                        Log.i(TAG, "Firebase: Syncing " + remoteMedicines.size() + " remote items.");
                        syncToDatabase(remoteMedicines, true); // true = REMOTE source
                    }
                });
    }

    private void syncToDatabase(List<Medicine> incomingMedicines, boolean isRemoteSource) {
        IoExecutor.submit(() -> {
            try {
                String sourceName = isRemoteSource ? "Remote (Firebase)" : "Local (Assets)";
                Log.d(TAG, "Database: Syncing " + incomingMedicines.size() + " items from " + sourceName);

                // 🚀 DELETE SYNCHRONIZATION:
                // Find items in DB from THIS source that are NOT in the incoming list
                List<MedicineEntity> allEntities = medicineDao.getAll();
                List<String> incomingIds = new ArrayList<>();
                for (Medicine m : incomingMedicines) incomingIds.add(m.getId());

                List<String> idsToDelete = new ArrayList<>();
                for (MedicineEntity entity : allEntities) {
                    if (entity.isRemote == isRemoteSource) {
                        if (!incomingIds.contains(entity.id)) {
                            idsToDelete.add(entity.id);
                        }
                    }
                }

                if (!idsToDelete.isEmpty()) {
                    Log.i(TAG, "Database: Removing " + idsToDelete.size() + " stale items from " + sourceName + ": " + idsToDelete);
                    medicineDao.deleteByIds(idsToDelete);
                }

                // Insert/Update new items
                List<MedicineEntity> entitiesToInsert = new ArrayList<>();
                for (Medicine m : incomingMedicines) {
                    entitiesToInsert.add(MedicineMapper.toEntity(m));
                }
                medicineDao.insertAll(entitiesToInsert);

                // Refresh final searchable catalog
                List<MedicineEntity> finalEntities = medicineDao.getAll();
                updateCacheFromEntities(finalEntities);

                Log.i(TAG, "Database: Sync complete for " + sourceName + ". Final searchable items: " + cachedCatalog.size());

                if (updateListener != null) {
                    updateListener.onCatalogUpdated(cachedCatalog);
                }
            } catch (Exception e) {
                Log.e(TAG, "Database: Sync operation failed.", e);
            }
        });
    }

    private void updateCacheFromEntities(List<MedicineEntity> entities) {
        List<Medicine> domains = new ArrayList<>();
        for (MedicineEntity entity : entities) {
            domains.add(MedicineMapper.toDomain(entity));
        }
        this.cachedCatalog = Collections.unmodifiableList(domains);
    }

    public List<Medicine> getAll() {
        return cachedCatalog;
    }

    public void logUnknownDetection(String rawOcrText, String imagePath) {
        long timestamp = System.currentTimeMillis();
        IoExecutor.submit(() -> {
            Map<String, Object> data = new HashMap<>();
            data.put("ocrText", rawOcrText);
            data.put("timestamp", timestamp);
            data.put("status", "pending_review");
            data.put("deviceId", android.os.Build.MODEL);
            data.put("imagePath", imagePath);

            firestore.collection("unknown_medicines")
                    .add(data)
                    .addOnSuccessListener(documentReference -> {
                        Log.i(TAG, "Cloud: Logged unknown medicine for review.");
                    });
        });
    }

    private String readFully(InputStream is) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) out.write(buffer, 0, read);
        is.close();
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private Medicine parseMedicine(JSONObject obj) throws Exception {
        String id = obj.getString("id");
        String name = obj.optString("name", obj.optString("medicineName"));

        // 🚀 UNIFIED SCHEMA VALIDATION
        if (name.isEmpty()) {
            throw new IllegalArgumentException("JSON Medicine missing required field: name (ID: " + id + ")");
        }

        List<String> keywords = jsonArrayToList(obj.optJSONArray("searchKeywords"));
        if (keywords.isEmpty()) {
            keywords = jsonArrayToList(obj.optJSONArray("ocrKeywords"));
        }
        // Fallback: if no keywords, use name as a keyword
        if (keywords.isEmpty()) {
            keywords = new ArrayList<>();
            keywords.add(name);
        }

        return new Medicine(
                id,
                name,
                obj.optString("company", "Unknown"),
                jsonArrayToList(obj.optJSONArray("supportedCrops")),
                jsonArrayToList(obj.optJSONArray("supportedDiseases")),
                obj.optString("usageInstructions"),
                obj.optString("warnings"),
                keywords,
                jsonArrayToList(obj.optJSONArray("imageUrls")),
                jsonArrayToList(obj.optJSONArray("audioUrls")),
                obj.optLong("updatedAt", 0)
        );
    }

    private Medicine docToMedicine(DocumentSnapshot doc) {
        try {
            // 🚀 UNIFIED MAPPING (Firebase -> Domain)
            // Firebase uses 'medicineName', Domain uses 'name'
            String name = doc.getString("medicineName");
            if (name == null) name = doc.getString("name");

            if (name == null || name.isEmpty()) {
                Log.w(TAG, "Firebase: Skipping document " + doc.getId() + " - missing 'name' or 'medicineName'");
                return null;
            }

            // Firebase uses 'ocrKeywords' or 'ocrText', Domain uses 'searchKeywords'
            List<String> keywords = safeGetList(doc, "ocrKeywords");
            if (keywords.isEmpty()) keywords = safeGetList(doc, "ocrText");
            if (keywords.isEmpty()) keywords = safeGetList(doc, "searchKeywords");
            if (keywords.isEmpty()) keywords = safeGetList(doc, "keywords");

            // Validation: Ensure we have at least one keyword for matching
            if (keywords.isEmpty()) {
                keywords = new ArrayList<>();
                keywords.add(name);
            }

            // Firebase uses 'imageurls' (lowercase), Domain uses 'imageUrls'
            List<String> images = safeGetList(doc, "imageurls");
            if (images.isEmpty()) images = safeGetList(doc, "imageUrls");

            // Firebase uses 'audiourls' (lowercase), Domain uses 'audioUrls'
            List<String> audios = safeGetList(doc, "audiourls");
            if (audios.isEmpty()) audios = safeGetList(doc, "audioUrls");

            // Firebase uses 'crop'/'disease' (singular strings), Domain uses 'supportedCrops'/'supportedDiseases' (lists)
            List<String> crops = safeGetList(doc, "crop");
            if (crops.isEmpty()) crops = safeGetList(doc, "supportedCrops");

            List<String> diseases = safeGetList(doc, "disease");
            if (diseases.isEmpty()) diseases = safeGetList(doc, "supportedDiseases");

            // Firebase uses 'marathiInfo' or 'warnings'
            String warnings = doc.getString("marathiInfo");
            if (warnings == null || warnings.isEmpty()) warnings = doc.getString("warnings");

            // Firebase uses 'usage', 'usageInstructions' or common typo 'usuage'
            String usage = doc.getString("usage");
            if (usage == null || usage.isEmpty()) usage = doc.getString("usageInstructions");
            if (usage == null || usage.isEmpty()) usage = doc.getString("usuage"); // 🚀 Support common typo seen in Firestore

            // Handle updatedAt: support both Long (ms) and Firebase Timestamp
            long updatedAt = 0;
            if (doc.contains("updatedAt")) {
                Object val = doc.get("updatedAt");
                if (val instanceof Long) {
                    updatedAt = (Long) val;
                } else if (val instanceof com.google.firebase.Timestamp) {
                    updatedAt = ((com.google.firebase.Timestamp) val).toDate().getTime();
                }
            }

            Medicine m = new Medicine(
                    doc.getId(), // Use Firestore Document ID as the unique Medicine ID
                    name,
                    doc.getString("company") != null ? doc.getString("company") : "Unknown",
                    crops,
                    diseases,
                    usage,
                    warnings,
                    keywords,
                    images,
                    audios,
                    updatedAt,
                    true // 🚀 isRemote = true
            );

            Log.i(TAG, "Firebase Parsed -> Name: " + m.getName() + " Usage: " + m.getUsageInstructions() + " Warnings: " + m.getWarnings());
            return m;
        } catch (Exception e) {
            Log.e(TAG, "Firebase: Failed to unify document " + doc.getId(), e);
            return null;
        }
    }

    private List<String> safeGetList(DocumentSnapshot doc, String field) {
        Object val = doc.get(field);
        if (val instanceof List) {
            List<?> rawList = (List<?>) val;
            List<String> result = new ArrayList<>();
            for (Object item : rawList) {
                if (item != null) result.add(item.toString());
            }
            return result;
        } else if (val instanceof String) {
            // Handle cases where a single string is provided instead of a list
            List<String> result = new ArrayList<>();
            result.add((String) val);
            return result;
        }
        return Collections.emptyList();
    }

    private List<String> jsonArrayToList(JSONArray arr) {
        if (arr == null) return Collections.emptyList();
        List<String> list = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            String val = arr.optString(i);
            if (val != null) list.add(val);
        }
        return list;
    }
}
