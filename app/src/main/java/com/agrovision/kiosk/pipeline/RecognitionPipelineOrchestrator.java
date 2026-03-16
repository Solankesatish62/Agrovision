package com.agrovision.kiosk.pipeline;

import com.agrovision.kiosk.data.model.Medicine;
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
 * It uses a pre-loaded medicine catalog for matching to ensure performance.
 */
public final class RecognitionPipelineOrchestrator {

    private final List<Medicine> medicineCatalog;

    public RecognitionPipelineOrchestrator(List<Medicine> medicineCatalog) {
        this.medicineCatalog = medicineCatalog != null ? medicineCatalog : Collections.emptyList();
    }

    /**
     * Resolves a list of normalized OCR strings into ScanResults.
     * This method is strictly for matching and does not access the database or repository.
     */
    public List<ScanResult> resolve(List<String> normalizedTexts) {
        List<ScanResult> results = new ArrayList<>();

        if (normalizedTexts == null || normalizedTexts.isEmpty()) {
            return results;
        }

        for (String text : normalizedTexts) {
            if (text == null || text.trim().isEmpty()) continue;

            // Perform matching logic using the pre-loaded catalog
            MatchResult match = MedicineMatcher.match(text, medicineCatalog);

            if (match.isMatched() && match.getMedicine() != null) {
                Medicine medicine = match.getMedicine();
                List<ResultInfoItem> infoItems = ResultInfoMapper.fromMedicine(medicine);

                results.add(new ScanResult(
                        ResultType.KNOWN,
                        medicine.getId(),
                        medicine.getName(),
                        Collections.emptyList(), // Images can be added here if available
                        infoItems,
                        text,
                        match.isLowConfidence()
                ));
            } else {
                // If no match found, create an UNKNOWN result
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
