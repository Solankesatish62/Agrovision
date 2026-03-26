package com.agrovision.kiosk.vision.mapping;

import com.agrovision.kiosk.data.model.Medicine;
import com.agrovision.kiosk.util.LogUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * MedicineMatcher
 *
 * Responsibility: Order-independent token-based matching for medicine identification.
 * Optimized for mixed-language names (English + Marathi).
 */
public final class MedicineMatcher {

    private static final float MATCH_THRESHOLD = 0.40f; 

    private MedicineMatcher() {
        // No instances
    }

    /**
     * Matches normalized OCR text against the medicine catalog.
     */
    public static MatchResult match(String normalizedText,
                                    List<Medicine> medicines) {

        if (normalizedText == null || normalizedText.trim().isEmpty()
                || medicines == null || medicines.isEmpty()) {
            return MatchResult.none(normalizedText);
        }

        LogUtils.d("--- START MATCHING ---");
        LogUtils.d("RAW_OCR_TEXT: [" + normalizedText + "]");

        // 1. Tokenize OCR text
        List<String> ocrTokens = tokenize(normalizedText);
        if (ocrTokens.isEmpty()) {
            LogUtils.w("MATCH_FAILED: OCR tokens are empty after cleaning.");
            return MatchResult.none(normalizedText);
        }
        
        Set<String> ocrTokenSet = new HashSet<>(ocrTokens);
        LogUtils.d("OCR_TOKENS: " + ocrTokenSet);

        Medicine bestMedicine = null;
        float bestScore = 0f;

        // 2. Iterate through catalog
        for (Medicine medicine : medicines) {
            String medicineName = medicine.getName();
            if (medicineName == null || medicineName.trim().isEmpty()) continue;

            List<String> medicineTokens = tokenize(medicineName);
            if (medicineTokens.isEmpty()) continue;

            int matchCount = 0;
            List<String> matchedTokensList = new ArrayList<>();
            
            for (String mToken : medicineTokens) {
                if (ocrTokenSet.contains(mToken)) {
                    matchCount++;
                    matchedTokensList.add(mToken);
                }
            }

            if (matchCount == 0) continue;

            // 🚀 BALANCED SCORING LOGIC
            // catalogCoverage: How much of the long catalog name we found.
            // ocrCoverage: How much of what the camera saw was actually a match.
            float catalogCoverage = (float) matchCount / medicineTokens.size();
            float ocrCoverage = (float) matchCount / ocrTokens.size();
            
            // Final score is a weighted average. 
            // We give more weight to ocrCoverage because if the camera only sees "Profex", 
            // and "Profex" is in the catalog, it's a very strong signal.
            float score = (ocrCoverage * 0.7f) + (catalogCoverage * 0.3f);
            
            // 🔍 DIAGNOSTIC LOG
            LogUtils.d(String.format(Locale.US,
                "CHECKING: %s | MATCHED: %s | OCR_COV: %.2f | CAT_COV: %.2f | FINAL_SCORE: %.2f", 
                medicineName, matchedTokensList, ocrCoverage, catalogCoverage, score));

            if (score >= MATCH_THRESHOLD && score > bestScore) {
                bestScore = score;
                bestMedicine = medicine;
            }
        }

        // 3. Final Result
        if (bestMedicine != null) {
            LogUtils.i(String.format(Locale.US, 
                "MATCH_SUCCESS: %s (Score: %.2f)", 
                bestMedicine.getName(), bestScore));
                
            return bestScore >= 0.85f 
                ? MatchResult.exact(bestMedicine, normalizedText) 
                : MatchResult.fuzzy(bestMedicine, bestScore, normalizedText);
        }

        LogUtils.w("MATCH_FAILED: No medicine reached the threshold of " + MATCH_THRESHOLD);
        return MatchResult.none(normalizedText);
    }

    private static List<String> tokenize(String text) {
        if (text == null) return new ArrayList<>();
        
        String cleaned = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\p{M}\\s]", " ");

        String[] parts = cleaned.trim().split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            if (!p.isEmpty()) tokens.add(p);
        }
        return tokens;
    }
}
