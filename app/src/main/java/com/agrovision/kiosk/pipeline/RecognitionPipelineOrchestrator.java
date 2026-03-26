package com.agrovision.kiosk.pipeline;

import android.content.Context;
import android.util.Log;

import com.agrovision.kiosk.data.model.Medicine;
import com.agrovision.kiosk.data.repository.MedicineRepository;
import com.agrovision.kiosk.ui.result.mapper.ResultInfoMapper;
import com.agrovision.kiosk.ui.result.model.ResultInfoItem;
import com.agrovision.kiosk.ui.result.model.ResultType;
import com.agrovision.kiosk.ui.result.model.ScanResult;
import com.agrovision.kiosk.vision.mapping.MatchResult;
import com.agrovision.kiosk.vision.mapping.MedicineMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RecognitionPipelineOrchestrator
 *
 * Responsibility: Bridge between OCR text and Business Models (Medicine/ScanResult).
 * Runs in the Application Layer.
 *
 * Fully Offline Operation:
 * - Matches against in-memory catalog (loaded from Room).
 * - Triggers Firebase reporting asynchronously via Repository.
 */
public final class RecognitionPipelineOrchestrator {

    private static final String TAG = "RecognitionPipelineOrchestrator";
    private final MedicineRepository repository;
    private List<Medicine> medicineCatalog;

    public RecognitionPipelineOrchestrator(Context context) {
        this.repository = MedicineRepository.getInstance(context);
        this.medicineCatalog = repository.getAll();
        
        // Listen for updates from Repository (which manages the hybrid Firebase/Room sync)
        this.repository.setOnCatalogUpdateListener(newCatalog -> {
            Log.i(TAG, "Catalog updated. New size: " + newCatalog.size());
            this.medicineCatalog = newCatalog;
        });
    }

    /**
     * Resolves OCR strings into ScanResults.
     * 100% Offline Matching.
     */
    public List<ScanResult> resolve(List<String> normalizedTexts) {
        List<ScanResult> results = new ArrayList<>();

        if (normalizedTexts == null || normalizedTexts.isEmpty()) {
            return results;
        }

        for (String text : normalizedTexts) {
            if (text == null || text.trim().isEmpty()) continue;

            // Offline matching using local catalog
            MatchResult match = MedicineMatcher.match(text, medicineCatalog);

            if (match.isMatched() && match.getMedicine() != null) {
                Medicine medicine = match.getMedicine();
                List<ResultInfoItem> infoItems = ResultInfoMapper.fromMedicine(medicine);

                results.add(new ScanResult(
                        ResultType.KNOWN,
                        medicine.getId(),
                        medicine.getName(),
                        medicine.getImageUrls(),
                        infoItems,
                        text,
                        match.isLowConfidence()
                ));
            } else {
                // 🚀 NON-BLOCKING FEEDBACK: Log unknown medicine
                // Repository handles the logic: Save to Room instantly, sync to Firebase if/when online.
                repository.logUnknownDetection(text, null);

                results.add(new ScanResult(
                        ResultType.UNKNOWN,
                        null,
                        "Unknown Medicine",
                        Collections.emptyList(),
                        Collections.emptyList(),
                        text,
                        false
                ));
            }
        }
        return results;
    }
}
