package com.agrovision.kiosk.pipeline;

import android.content.Context;
import android.util.Log;

import com.agrovision.kiosk.data.model.Medicine;
import com.agrovision.kiosk.data.repository.MedicineRepository;
import com.agrovision.kiosk.network.PortalScraper;
import com.agrovision.kiosk.ui.result.mapper.ResultInfoMapper;
import com.agrovision.kiosk.ui.result.model.ResultInfoItem;
import com.agrovision.kiosk.ui.result.model.ResultType;
import com.agrovision.kiosk.ui.result.model.ScanResult;
import com.agrovision.kiosk.util.BarcodeParser;
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
 */
public final class RecognitionPipelineOrchestrator {

    private static final String TAG = "RecognitionPipelineOrchestrator";
    private final MedicineRepository repository;
    private List<Medicine> medicineCatalog;

    public interface BarcodeCallback {
        void onResult(List<ScanResult> results);
        void onUnknown(String scrapedName, String rawCode);
        void onFailure();
    }

    public RecognitionPipelineOrchestrator(Context context) {
        this.repository = MedicineRepository.getInstance(context);
        this.medicineCatalog = repository.getAll();
        
        this.repository.setOnCatalogUpdateListener(newCatalog -> {
            Log.i(TAG, "Catalog updated. New size: " + newCatalog.size());
            this.medicineCatalog = newCatalog;
        });
    }

    /**
     * Priority-based Barcode Recognition.
     */
    public void handleBarcodeScan(String rawInput, BarcodeCallback callback) {
        String cleanId = BarcodeParser.getCleanIdentity(rawInput);
        String prefix = BarcodeParser.getPrefix(cleanId);

        // 🚀 PRIORITY 1: LOCAL MATCH (Instant)
        Medicine localMatch = repository.getByBarcodePrefix(prefix);
        if (localMatch != null) {
            Log.i(TAG, "Barcode: Found exact local match: " + localMatch.getName());
            callback.onResult(wrapInResults(localMatch, rawInput));
            return;
        }

        // 🚀 PRIORITY 2: INTERNET SCRAPE (if URL or potential UID)
        if (cleanId.startsWith("http")) {
            PortalScraper.fetchProductName(cleanId, new PortalScraper.Callback() {
                @Override
                public void onResult(String scrapedName) {
                    if (scrapedName == null || scrapedName.isEmpty()) {
                        callback.onFailure();
                        return;
                    }

                    // 🚀 SMART MATCH: Match scraped name against local catalog
                    MatchResult match = MedicineMatcher.match(scrapedName, medicineCatalog);
                    if (match.isMatched() && match.getMedicine() != null) {
                        Medicine m = match.getMedicine();
                        Log.i(TAG, "Barcode: Web match identified as: " + m.getName());
                        
                        // LEARN the prefix for next time
                        if (!prefix.isEmpty()) {
                            repository.addBarcodePrefix(m.getId(), prefix);
                        }
                        
                        callback.onResult(wrapInResults(m, rawInput));
                    } else {
                        // UNKNOWN Discovery
                        Log.w(TAG, "Barcode: Identified product '" + scrapedName + "' but not in local catalog.");
                        repository.logUnknownDiscovery(scrapedName, rawInput);
                        callback.onUnknown(scrapedName, rawInput);
                    }
                }

                @Override
                public void onError(Exception e) {
                    callback.onFailure();
                }
            });
        } else {
            // Not a URL and no prefix match found
            callback.onFailure();
        }
    }

    private List<ScanResult> wrapInResults(Medicine m, String raw) {
        List<ResultInfoItem> infoItems = ResultInfoMapper.fromMedicine(m);
        ScanResult res = new ScanResult(
                ResultType.KNOWN,
                m.getId(),
                m.getName(),
                m.getImageUrls(),
                m.getAudioUrls(),
                infoItems,
                raw,
                false
        );
        return Collections.singletonList(res);
    }

    /**
     * Resolves OCR strings into ScanResults.
     * 100% Offline Matching.
     */
    public List<ScanResult> resolve(List<String> normalizedTexts) {
        Log.d("PIPELINE_TRACE", "9a. Orchestrator resolving " + (normalizedTexts != null ? normalizedTexts.size() : 0) + " items");
        
        List<ScanResult> results = new ArrayList<>();
        if (normalizedTexts == null || normalizedTexts.isEmpty()) return results;

        for (String text : normalizedTexts) {
            if (text == null || text.trim().isEmpty()) continue;

            MatchResult match = MedicineMatcher.match(text, medicineCatalog);
            if (match.isMatched() && match.getMedicine() != null) {
                Medicine medicine = match.getMedicine();
                List<ResultInfoItem> infoItems = ResultInfoMapper.fromMedicine(medicine);

                results.add(new ScanResult(
                        ResultType.KNOWN,
                        medicine.getId(),
                        medicine.getName(),
                        medicine.getImageUrls(),
                        medicine.getAudioUrls(),
                        infoItems,
                        text,
                        match.isLowConfidence()
                ));
            } else {
                repository.logUnknownDetection(text, null);
                results.add(new ScanResult(
                        ResultType.UNKNOWN,
                        null,
                        "Unknown Medicine",
                        Collections.emptyList(),
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
