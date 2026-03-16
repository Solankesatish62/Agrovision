package com.agrovision.kiosk.data.repository;

import android.content.Context;
import com.agrovision.kiosk.data.database.AppDatabase;
import com.agrovision.kiosk.data.database.dao.MedicineDao;
import com.agrovision.kiosk.data.database.entity.MedicineEntity;
import com.agrovision.kiosk.data.mapper.MedicineMapper;
import com.agrovision.kiosk.data.model.Medicine;
import com.agrovision.kiosk.threading.IoExecutor;
import com.agrovision.kiosk.util.LogUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MedicineRepository {
    private static volatile MedicineRepository INSTANCE;
    private final Context appContext;
    private final MedicineDao medicineDao;
    private List<Medicine> cachedCatalog = new ArrayList<>();

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
        this.medicineDao = AppDatabase.getInstance(appContext).medicineDao();
        syncJsonToDatabase();
    }

    private void syncJsonToDatabase() {
        IoExecutor.submit(() -> {
            try {
                InputStream is = appContext.getAssets().open("data/medicines.json");
                String json = readFully(is);
                JSONArray array = new JSONArray(json);
                List<MedicineEntity> entities = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    Medicine domain = parseMedicine(obj);
                    entities.add(MedicineMapper.toEntity(domain));
                }
                medicineDao.insertAll(entities);
                refreshCache();
                LogUtils.i("Database synced with " + entities.size() + " items.");
            } catch (Exception e) {
                LogUtils.e("Sync failed", e);
            }
        });
    }

    private void refreshCache() {
        List<MedicineEntity> entities = medicineDao.getAll();
        List<Medicine> domains = new ArrayList<>();
        for (MedicineEntity entity : entities) {
            domains.add(MedicineMapper.toDomain(entity));
        }
        this.cachedCatalog = Collections.unmodifiableList(domains);
    }

    public List<Medicine> getAll() {
        return cachedCatalog;
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
                obj.optString("warnings")
        );
    }

    private List<String> jsonArrayToList(JSONArray arr) {
        if (arr == null) return Collections.emptyList();
        List<String> list = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) list.add(arr.optString(i));
        return list;
    }
}
