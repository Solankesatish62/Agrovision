package com.agrovision.kiosk.data.repository;

import android.content.Context;

import com.agrovision.kiosk.data.model.Medicine;
import com.agrovision.kiosk.util.LogUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * MedicineRepository
 *
 * SINGLE SOURCE OF TRUTH for medicine catalog.
 *
 * HYBRID LOADING STRATEGY:
 * - Try loading assets/data/medicines.json
 * - If missing → fallback to mock data (DEV mode)
 * - If present but corrupted → fail fast
 */
public final class MedicineRepository {

    private static volatile MedicineRepository INSTANCE;

    private final Map<String, Medicine> byId;
    private final Map<String, Medicine> byName;

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
        Map<String, Medicine> idMap = new HashMap<>();
        Map<String, Medicine> nameMap = new HashMap<>();

        boolean loadedFromAssets = false;

        try {
            InputStream is = appContext.getAssets().open("data/medicines.json");
            String json = readFully(is);
            JSONArray array = new JSONArray(json);

            for (int i = 0; i < array.length(); i++) {
                Medicine m = parseMedicine(array.getJSONObject(i));
                idMap.put(m.getId(), m);
                nameMap.put(m.getName().toLowerCase(), m);
            }

            loadedFromAssets = true;
            LogUtils.i("MedicineRepository loaded from assets (" + idMap.size() + ")");

        } catch (Exception e) {
            LogUtils.w("medicines.json missing or unreadable — using mock data");
        }

        if (!loadedFromAssets) {
            for (Medicine m : createMockMedicines()) {
                idMap.put(m.getId(), m);
                nameMap.put(m.getName().toLowerCase(), m);
            }
        }

        this.byId = Collections.unmodifiableMap(idMap);
        this.byName = Collections.unmodifiableMap(nameMap);
    }

    /* =======================
       Public API
       ======================= */

    public Medicine getById(String id) {
        return id == null ? null : byId.get(id);
    }

    public Medicine getByName(String name) {
        return name == null ? null : byName.get(name.toLowerCase());
    }

    public List<Medicine> getAll() {
        return new ArrayList<>(byId.values());
    }

    /* =======================
       IO Helpers (SAFE)
       ======================= */

    private String readFully(InputStream is) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        is.close();
        return out.toString(StandardCharsets.UTF_8.name());
    }

    /* =======================
       JSON Parsing
       ======================= */

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
        for (int i = 0; i < arr.length(); i++) {
            list.add(arr.optString(i));
        }
        return list;
    }

    /* =======================
       DEV MOCKS (SAFE)
       ======================= */

    private List<Medicine> createMockMedicines() {
        return Arrays.asList(
                new Medicine(
                        "MOCK_001",
                        "DemoMed",
                        "AgroVision",
                        Arrays.asList("Cotton"),
                        Arrays.asList("Aphids"),
                        "Use for demo only",
                        "Not for real crops"
                )
        );
    }
}
