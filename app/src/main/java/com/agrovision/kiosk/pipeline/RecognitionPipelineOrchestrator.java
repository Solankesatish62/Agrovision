package com.agrovision.kiosk.pipeline;

import com.agrovision.kiosk.data.mapper.MedicineMapper;
import com.agrovision.kiosk.ui.result.model.ResultType;
import com.agrovision.kiosk.ui.result.model.ScanResult;
import com.agrovision.kiosk.vision.mapping.MedicineMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RecognitionPipelineOrchestrator
 *
 * SINGLE responsibility:
 * Convert raw recognition tokens into ordered ScanResults.
 */
public final class RecognitionPipelineOrchestrator {

    private final MedicineMatcher matcher;
    private final MedicineMapper mapper;
    // Note: Assuming these support classes/interfaces will be defined as per pattern
    // private final UnknownMedicineReporter reporter; 

    public RecognitionPipelineOrchestrator(
            MedicineMatcher matcher,
            MedicineMapper mapper
    ) {
        this.matcher = matcher;
        this.mapper = mapper;
    }

    /**
     * Resolves a list of detected raw names into a structured ScanResult queue.
     */
    public List<ScanResult> resolve(List<String> detectedNames) {

        List<ScanResult> results = new ArrayList<>();

        if (detectedNames == null || detectedNames.isEmpty()) {
            return results;
        }

        for (String rawName : detectedNames) {
            // Logic to match and map would go here
            // This is a structural skeleton as requested
        }

        // 🟢 PRIORITIZE KNOWN RESULTS
        Collections.sort(results, (a, b) ->
                Boolean.compare(
                        a.resultType == ResultType.UNKNOWN,
                        b.resultType == ResultType.UNKNOWN
                )
        );

        return results;
    }
}
