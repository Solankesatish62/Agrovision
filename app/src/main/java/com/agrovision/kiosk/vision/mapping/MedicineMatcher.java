package com.agrovision.kiosk.vision.mapping;

import com.agrovision.kiosk.data.model.Medicine;

import java.util.List;

/**
 * MedicineMatcher
 *
 * FINAL decision layer for medicine identification.
 *
 * RESPONSIBILITIES:
 * - Decide EXACT vs FUZZY vs NONE
 * - Prefer specific products over generic ones
 * - Be deterministic and order-independent
 *
 * PIPELINE:
 * 1. Greedy EXACT match (longest token sequence wins)
 * 2. FUZZY match fallback
 * 3. NONE if no reliable match
 */
public final class MedicineMatcher {

    private MedicineMatcher() {
        // No instances
    }

    /**
     * Matches normalized OCR text against the medicine catalog.
     *
     * @param normalizedText Lowercased, cleaned OCR text
     * @param medicines      Medicine catalog
     * @return MatchResult (never null)
     */
    public static MatchResult match(String normalizedText,
                                    List<Medicine> medicines) {

        if (normalizedText == null || normalizedText.isEmpty()
                || medicines == null || medicines.isEmpty()) {

            return MatchResult.none(normalizedText);
        }

        // 1️⃣ EXACT match (greedy, most specific wins)
        MatchResult exact = exactMatchGreedy(normalizedText, medicines);
        if (exact.isMatched()) {
            return exact;
        }

        // 2️⃣ FUZZY fallback
        return FuzzyMatcher.match(normalizedText, medicines);
    }

    /* =========================================================
       EXACT MATCHING (GREEDY & SAFE)
       ========================================================= */

    private static MatchResult exactMatchGreedy(String text,
                                                List<Medicine> medicines) {

        // Tokenize OCR text once
        String[] ocrTokens = text.split("\\s+");

        Medicine bestMedicine = null;
        int bestTokenLength = 0;

        for (Medicine medicine : medicines) {

            String name = medicine.getName().toUpperCase().trim();
            if (name == null || name.isEmpty()) {
                continue;
            }

            // Split medicine name into tokens
            String[] nameTokens = name.split("\\s+");

            // Optimization: skip if it cannot beat current best
            if (nameTokens.length <= bestTokenLength) {
                continue;
            }

            if (containsTokenSequence(ocrTokens, nameTokens)) {
                bestMedicine = medicine;
                bestTokenLength = nameTokens.length;
            }
        }

        if (bestMedicine != null) {
            return MatchResult.exact(bestMedicine, text);
        }

        return MatchResult.none(text);
    }

    /**
     * Checks whether OCR tokens contain the medicine name
     * as a full, order-preserving token sequence.
     *
     * COMPARISON:
     * - Case-insensitive (OCR is normalized, DB names are display-formatted)
     */
    private static boolean containsTokenSequence(String[] ocrTokens,
                                                 String[] nameTokens) {

        if (nameTokens.length > ocrTokens.length) return false;

        for (int i = 0; i <= ocrTokens.length - nameTokens.length; i++) {

            boolean match = true;

            for (int j = 0; j < nameTokens.length; j++) {
                // 🔒 Case-insensitive comparison (CRITICAL FIX)
                if (!ocrTokens[i + j].equalsIgnoreCase(nameTokens[j])) {
                    match = false;
                    break;
                }
            }

            if (match) return true;
        }

        return false;
    }
}
