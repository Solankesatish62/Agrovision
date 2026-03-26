package com.agrovision.kiosk.data.repository;

import android.content.Context;
import android.util.Log;

import com.agrovision.kiosk.data.database.AppDatabase;
import com.agrovision.kiosk.data.database.dao.MedicineDao;
import com.agrovision.kiosk.data.database.dao.UnknownDetectionDao;
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
    private final UnknownDetectionDao unknownDetectionDao;
    private final FirebaseFirestore firestore;
    
    private List<Medicine> cachedCatalog = new ArrayList<>();
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
        this.unknownDetectionDao = db.unknownDetectionDao();
        
        this.firestore = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build();
        this.firestore.setFirestoreSettings(settings);

        loadCatalogFromRoom();
        
        if (cachedCatalog.isEmpty()) {
            loadCatalogFromAssets();
        }

        startRealtimeSync();
    }

    public void setOnCatalogUpdateListener(OnCatalogUpdateListener listener) {
        this.updateListener = listener;
    }

    private void loadCatalogFromRoom() {
        IoExecutor.submit(() -> {
            List<MedicineEntity> entities = medicineDao.getAll();
            updateCacheFromEntities(entities);
            Log.d(TAG, "Database: Loaded " + cachedCatalog.size() + " items from local storage.");
        });
    }

    private void loadCatalogFromAssets() {
        try {
            InputStream is = appContext.getAssets().open("data/medicines.json");
            String json = readFully(is);
            JSONArray array = new JSONArray(json);
            List<Medicine> medicines = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                medicines.add(parseMedicine(obj));
            }
            this.cachedCatalog = Collections.unmodifiableList(medicines);
            syncToDatabase(cachedCatalog);
        } catch (Exception e) {
            Log.e(TAG, "Assets: Failed to load fallback data.", e);
        }
    }

    private void startRealtimeSync() {
        Log.i(TAG, "Firebase: Attempting to connect to collection 'medicine'...");
        
        firestore.collection("medicine")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Firebase: CONNECTION ERROR! Details: " + error.getMessage());
                        return;
                    }

                    if (value != null) {
                        int count = value.size();
                        Log.i(TAG, "Firebase: Data received! Document count in cloud: " + count);
                        
                        if (count == 0) {
                            Log.w(TAG, "Firebase: Collection 'medicine' exists but is EMPTY. Check your Firestore console.");
                            return;
                        }

                        List<Medicine> remoteMedicines = new ArrayList<>();
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Medicine m = docToMedicine(doc);
                            if (m != null) remoteMedicines.add(m);
                        }

                        if (!remoteMedicines.isEmpty()) {
                            Log.i(TAG, "Firebase: Successfully parsed " + remoteMedicines.size() + " medicines.");
                            syncToDatabase(remoteMedicines);
                        }
                    }
                });
    }

    private void syncToDatabase(List<Medicine> medicines) {
        IoExecutor.submit(() -> {
            try {
                List<MedicineEntity> entities = new ArrayList<>();
                for (Medicine medicine : medicines) {
                    entities.add(MedicineMapper.toEntity(medicine));
                }
                medicineDao.insertAll(entities);
                updateCacheFromEntities(medicineDao.getAll());
                if (updateListener != null) {
                    updateListener.onCatalogUpdated(cachedCatalog);
                }
            } catch (Exception e) {
                LogUtils.e("Database: Upsert failed.", e);
            }
        });
    }

    private void updateCacheFromEntities(List<MedicineEntity> entities) {
        List<Medicine> domains = new ArrayList<>();
        for (MedicineEntity entity : entities) {
            domains.add(MedicineMapper.toDomain(entity));
        }
        this.cachedCatalog = Collections.unmodifiableList(domains);
        
        StringBuilder names = new StringBuilder("LIVE CATALOG: ");
        for (Medicine m : cachedCatalog) {
            names.append("[").append(m.getName()).append("] ");
        }
        Log.i(TAG, names.toString());
    }

    public List<Medicine> getAll() {
        return cachedCatalog;
    }

    public void logUnknownDetection(String rawOcrText, String imagePath) {
        long timestamp = System.currentTimeMillis();
        IoExecutor.submit(() -> {
            UnknownDetectionEntity entity = new UnknownDetectionEntity(rawOcrText, imagePath, timestamp);
            long rowId = unknownDetectionDao.insert(entity);
            entity.id = rowId;

            Map<String, Object> data = new HashMap<>();
            data.put("ocrText", rawOcrText);
            data.put("timestamp", timestamp);
            data.put("deviceId", android.os.Build.MODEL);

            firestore.collection("unknown_detections")
                    .add(data)
                    .addOnSuccessListener(documentReference -> {
                        Log.i(TAG, "Cloud: Logged unknown detection successfully.");
                        IoExecutor.submit(() -> {
                            entity.isSynced = true;
                            unknownDetectionDao.update(entity);
                        });
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
        return new Medicine(
                obj.getString("id"),
                obj.getString("name"),
                obj.optString("company"),
                jsonArrayToList(obj.optJSONArray("supportedCrops")),
                jsonArrayToList(obj.optJSONArray("supportedDiseases")),
                obj.optString("usageInstructions"),
                obj.optString("warnings"),
                jsonArrayToList(obj.optJSONArray("imageUrls"))
        );
    }

    private Medicine docToMedicine(DocumentSnapshot doc) {
        try {
            String id = doc.getId();
            String name = doc.getString("name");
            String company = doc.getString("company");
            
            if (name == null) {
                Log.w(TAG, "Firebase: Skipping document '" + id + "' because the 'name' field is missing or empty.");
                return null;
            }

            return new Medicine(
                    id,
                    name,
                    company != null ? company : "Unknown",
                    safeGetList(doc, "supportedCrops"),
                    safeGetList(doc, "supportedDiseases"),
                    doc.getString("usageInstructions"),
                    doc.getString("warnings"),
                    safeGetList(doc, "imageUrls")
            );
        } catch (Exception e) {
            Log.e(TAG, "Firebase: Critical error parsing document " + doc.getId(), e);
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
