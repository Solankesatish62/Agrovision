package com.agrovision.kiosk.vision.mapping;

import com.agrovision.kiosk.data.model.Medicine;

import java.util.List;

/**
 * FuzzyMatcher
 *
 * Performs approximate string matching between
 * normalized OCR text and known medicines.
 */
public final class FuzzyMatcher {

    private static final float MIN_CONFIDENCE = 0.60f;
    private static final float EXACT_MATCH_THRESHOLD = 0.999f;

    private FuzzyMatcher() {}

    public static MatchResult match(String normalizedText,
                                    List<Medicine> medicines) {

        if (normalizedText == null || normalizedText.isEmpty()
                || medicines == null || medicines.isEmpty()) {

            return MatchResult.none(normalizedText);
        }

        Medicine bestMedicine = null;
        float bestScore = 0f;

        for (Medicine medicine : medicines) {

            // 🔧 FIX: Use existing API
            String candidate = medicine.getName();
            if (candidate == null || candidate.isEmpty()) continue;

            float score = similarity(normalizedText, candidate);

            if (score > bestScore) {
                bestScore = score;
                bestMedicine = medicine;
            }
        }

        if (bestMedicine == null || bestScore < MIN_CONFIDENCE) {
            return MatchResult.none(normalizedText);
        }

        // Perfect (or near-perfect) match → EXACT
        if (bestScore >= EXACT_MATCH_THRESHOLD) {
            return MatchResult.exact(bestMedicine, normalizedText);
        }

        return MatchResult.fuzzy(bestMedicine, bestScore, normalizedText);
    }

    /* =========================================================
       STRING SIMILARITY
       ========================================================= */

    private static float similarity(String a, String b) {

        int distance = levenshtein(a, b);
        int maxLen = Math.max(a.length(), b.length());

        if (maxLen == 0) return 1f;

        return 1f - ((float) distance / maxLen);
    }

    private static int levenshtein(String a, String b) {

        int lenA = a.length();
        int lenB = b.length();

        int[] prev = new int[lenB + 1];
        int[] curr = new int[lenB + 1];

        for (int j = 0; j <= lenB; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= lenA; i++) {

            curr[0] = i;
            char ca = a.charAt(i - 1);

            for (int j = 1; j <= lenB; j++) {
                char cb = b.charAt(j - 1);

                int cost = (ca == cb) ? 0 : 1;

                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }

            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[lenB];
    }
}
