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
 */
public final class MedicineMatcher {

    private static final float MATCH_THRESHOLD = 0.45f; // Threshold to handle partial matches (e.g. English only)

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

            float score = (float) matchCount / medicineTokens.size();
            
            // 🔍 DIAGNOSTIC LOG
            LogUtils.d(String.format(Locale.US,
                "CHECKING: %s | TOKENS: %s | MATCHED: %s | SCORE: %.2f", 
                medicineName, medicineTokens, matchedTokensList, score));

            if (score >= MATCH_THRESHOLD && score > bestScore) {
                bestScore = score;
                bestMedicine = medicine;
            } else if (score == bestScore && bestScore >= MATCH_THRESHOLD && bestMedicine != null) {
                if (medicineTokens.size() > tokenize(bestMedicine.getName()).size()) {
                    bestMedicine = medicine;
                }
            }
        }

        // 3. Final Result
        if (bestMedicine != null) {
            LogUtils.i(String.format(Locale.US, 
                "MATCH_SUCCESS: %s (Score: %.2f, OCR: %s)", 
                bestMedicine.getName(), bestScore, ocrTokenSet));
                
            // Return exact if score is very high, otherwise fuzzy
            return bestScore >= 0.9f 
                ? MatchResult.exact(bestMedicine, normalizedText) 
                : MatchResult.fuzzy(bestMedicine, bestScore, normalizedText);
        }

        LogUtils.w("MATCH_FAILED: No medicine reached the threshold of " + MATCH_THRESHOLD);
        return MatchResult.none(normalizedText);
    }

    /**
     * Normalizes and tokenizes text.
     * 
     * IMPORTANT: 
     * - Uses \p{L} for letters
     * - Uses \p{N} for numbers
     * - Uses \p{M} for combining marks (CRITICAL for Marathi/Devanagari vowel signs)
     */
    private static List<String> tokenize(String text) {
        if (text == null) return new ArrayList<>();
        
        // Lowercase and replace non-content symbols with spaces.
        // We added \p{M} to preserve Devanagari vowel signs (Matras).
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
