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
 * PURPOSE:
 * - Convert raw OCR tokens into ordered ScanResults
 *
 * RULES:
 * - NO UI
 * - NO Android
 * - NO DB
 * - NO networking
 */
public final class RecognitionPipelineOrchestrator {

    private final List<Medicine> medicineCatalog;

    public RecognitionPipelineOrchestrator(List<Medicine> medicineCatalog) {
        this.medicineCatalog = medicineCatalog;
    }

    /**
     * Resolve raw OCR texts into ScanResult queue.
     */
    public List<ScanResult> resolve(List<String> rawOcrTexts) {

        List<ScanResult> results = new ArrayList<>();

        if (rawOcrTexts == null || rawOcrTexts.isEmpty()) {
            return results;
        }

        for (String rawText : rawOcrTexts) {

            if (rawText == null || rawText.trim().isEmpty()) {
                continue;
            }

            String normalized = rawText.trim().toUpperCase();

            MatchResult match =
                    MedicineMatcher.match(normalized, medicineCatalog);

            ScanResult result;

            if (match.isMatched() && match.getMedicine() != null) {

                Medicine medicine = match.getMedicine();

                List<ResultInfoItem> infoItems =
                        ResultInfoMapper.fromMedicine(medicine);

                result = new ScanResult(
                        ResultType.KNOWN,
                        medicine.getId(),
                        medicine.getName(),
                        Collections.emptyList(),   // images later
                        infoItems,
                        rawText,
                        match.isLowConfidence()
                );

            } else {

                result = new ScanResult(
                        ResultType.UNKNOWN,
                        null,
                        null,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        rawText,
                        false
                );
            }

            results.add(result);
        }

        // ✅ KNOWN first, UNKNOWN last
        results.sort((a, b) ->
                Boolean.compare(
                        a.resultType == ResultType.UNKNOWN,
                        b.resultType == ResultType.UNKNOWN
                )
        );

        return results;
    }
}
