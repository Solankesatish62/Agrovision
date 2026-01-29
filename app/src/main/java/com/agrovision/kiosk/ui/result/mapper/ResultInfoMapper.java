package com.agrovision.kiosk.ui.result.mapper;

import com.agrovision.kiosk.data.model.Medicine;
import com.agrovision.kiosk.ui.result.model.ResultInfoItem;

import java.util.ArrayList;
import java.util.List;

/**
 * ResultInfoMapper
 *
 * PURPOSE:
 * - Convert Medicine domain model into UI-renderable ResultInfoItem list
 *
 * RULES:
 * - Stateless
 * - Pure mapping only
 * - NO Android framework
 * - NO UI logic
 */
public final class ResultInfoMapper {

    private ResultInfoMapper() {
        // No instances
    }

    /**
     * Maps a Medicine into ordered ResultInfoItems.
     */
    public static List<ResultInfoItem> fromMedicine(Medicine medicine) {

        List<ResultInfoItem> items = new ArrayList<>();

        if (medicine == null) {
            return items;
        }

        // 1️⃣ CROPS (Green)
        if (medicine.getSupportedCrops() != null) {
            for (String crop : medicine.getSupportedCrops()) {
                if (isValid(crop)) {
                    items.add(new ResultInfoItem(
                            ResultInfoItem.Type.CROP,
                            crop
                    ));
                }
            }
        }

        // 2️⃣ DISEASES / PESTS (Blue)
        if (medicine.getSupportedDiseases() != null) {
            for (String disease : medicine.getSupportedDiseases()) {
                if (isValid(disease)) {
                    items.add(new ResultInfoItem(
                            ResultInfoItem.Type.PEST,
                            disease
                    ));
                }
            }
        }

        // 3️⃣ USAGE (Light Green)
        if (isValid(medicine.getUsageInstructions())) {
            items.add(new ResultInfoItem(
                    ResultInfoItem.Type.USAGE,
                    medicine.getUsageInstructions()
            ));
        }

        // 4️⃣ WARNINGS / CAUTION (Red)
        if (isValid(medicine.getWarnings())) {
            items.add(new ResultInfoItem(
                    ResultInfoItem.Type.CAUTION,
                    medicine.getWarnings()
            ));
        }

        return items;
    }

    /* =========================================================
       INTERNAL HELPERS
       ========================================================= */

    private static boolean isValid(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
