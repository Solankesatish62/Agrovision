package com.agrovision.kiosk.vision.mapping;

import com.agrovision.kiosk.data.model.Medicine;
import com.agrovision.kiosk.util.LogUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * MedicineMatcher
 *
 * Responsibility: Advanced token-based and fuzzy matching for medicine identification.
 * Optimized for noisy OCR text from medicine bottles.
 *
 * Implementation focuses on:
 * 1. High-weight numeric identifiers (r303, 505)
 * 2. Penalizing generic tokens (bio, plus, gold)
 * 3. Confidence thresholds to prevent false positives
 */
public final class MedicineMatcher {

    // Thresholds (Step 1)
    private static final float MIN_CONFIDENCE_THRESHOLD = 0.50f;
    private static final float HIGH_CONFIDENCE_THRESHOLD = 0.80f;
    private static final float CLOSE_MATCH_GAP = 0.15f;

    // Weights (Step 2 & 3)
    private static final float BRAND_NAME_WEIGHT = 1.5f;
    private static final float KEYWORD_WEIGHT = 1.0f;
    private static final float NUMERIC_BONUS = 2.5f;
    private static final float GENERIC_PENALTY = 0.4f;

    // Generic words to penalize
    private static final List<String> GENERIC_TOKENS = Arrays.asList(
            "bio", "plus", "gold", "super", "vita", "agro", "ultra", "power", "veta"
    );

    private MedicineMatcher() {}

    /**
     * Internal candidate class for sorting results by score.
     */
    private static class Candidate implements Comparable<Candidate> {
        final Medicine medicine;
        final float score;

        Candidate(Medicine medicine, float score) {
            this.medicine = medicine;
            this.score = score;
        }

        @Override
        public int compareTo(Candidate other) {
            return Float.compare(other.score, this.score); // Descending
        }
    }

    public static MatchResult match(String normalizedText, List<Medicine> medicines) {
        if (normalizedText == null || normalizedText.trim().isEmpty() || medicines == null || medicines.isEmpty()) {
            return MatchResult.none(normalizedText);
        }

        String cleanedOcr = cleanOcrText(normalizedText);
        List<String> ocrTokens = tokenize(cleanedOcr);

        // Debug Logging (Step 6)
        LogUtils.d("OCR_MATCH_INPUT: [" + normalizedText + "]");
        LogUtils.d("OCR_TOKENS: " + ocrTokens);

        if (ocrTokens.isEmpty()) return MatchResult.none(normalizedText);

        List<Candidate> candidates = new ArrayList<>();

        for (Medicine medicine : medicines) {
            float score = calculateScore(ocrTokens, medicine);
            if (score > 0.1f) {
                candidates.add(new Candidate(medicine, score));
            }
        }

        if (candidates.isEmpty()) {
            LogUtils.w("MATCH_FAILED: No candidates found above minimum score.");
            return MatchResult.none(normalizedText);
        }

        // Sort by score
        Collections.sort(candidates);
        
        // Step 6: Log top 5 candidates
        logTopCandidates(candidates);

        Candidate top = candidates.get(0);
        float confidence = top.score;

        // Step 5: Top Candidate Comparison
        if (candidates.size() > 1) {
            Candidate second = candidates.get(1);
            float gap = top.score - second.score;
            if (gap < CLOSE_MATCH_GAP) {
                LogUtils.w(String.format(Locale.US, "AMBIGUOUS_MATCH: Gap between top 2 is too small (%.2f). Penalizing.", gap));
                confidence *= 0.85f; // Reduce confidence if choice is not clear
            }
        }

        LogUtils.i(String.format(Locale.US, "FINAL_MATCH_RESULT: %s (Confidence: %.2f)", 
                top.medicine.getName(), confidence));

        // Step 1: Confidence Thresholding
        if (confidence < MIN_CONFIDENCE_THRESHOLD) {
            LogUtils.w("MATCH_REJECTED: Confidence " + confidence + " is below threshold 0.50");
            return MatchResult.none(normalizedText);
        }

        // Return appropriate MatchResult based on confidence
        if (confidence >= HIGH_CONFIDENCE_THRESHOLD) {
            return MatchResult.exact(top.medicine, normalizedText);
        } else {
            return MatchResult.fuzzy(top.medicine, confidence, normalizedText);
        }
    }

    private static float calculateScore(List<String> ocrTokens, Medicine medicine) {
        float totalMatchScore = 0f;
        Set<String> matchedTargetTokens = new HashSet<>();
        Set<String> matchedOcrTokens = new HashSet<>();

        // 1. Match Brand Name
        List<String> nameTokens = tokenize(medicine.getName());
        totalMatchScore += matchTargetTokens(ocrTokens, nameTokens, BRAND_NAME_WEIGHT, matchedTargetTokens, matchedOcrTokens);

        // 2. Match Search Keywords
        List<String> keywordTokens = new ArrayList<>();
        for (String kw : medicine.getSearchKeywords()) {
            keywordTokens.addAll(tokenize(kw));
        }
        totalMatchScore += matchTargetTokens(ocrTokens, keywordTokens, KEYWORD_WEIGHT, matchedTargetTokens, matchedOcrTokens);

        // Normalize by max possible score
        float maxPossibleScore = 0f;
        for (String t : nameTokens) maxPossibleScore += getTokenWeight(t) * BRAND_NAME_WEIGHT;
        for (String t : keywordTokens) maxPossibleScore += getTokenWeight(t) * KEYWORD_WEIGHT;

        if (maxPossibleScore == 0) return 0;

        float weightedMatchRatio = totalMatchScore / maxPossibleScore;
        float coverage = (float) matchedTargetTokens.size() / nameTokens.size();
        float ocrRelevance = calculateOcrRelevance(ocrTokens, matchedOcrTokens);

        // Final score composite
        // 40% Weighted Match (How much of medicine tokens we found, weighted by importance)
        // 20% Coverage (What percentage of brand name tokens were found)
        // 40% OCR Relevance (How much of the scanned text actually belongs to this medicine)
        float score = (weightedMatchRatio * 0.4f) + (coverage * 0.2f) + (ocrRelevance * 0.4f);
        
        return Math.min(score, 1.0f);
    }

    private static float matchTargetTokens(
            List<String> ocrTokens, 
            List<String> targetTokens, 
            float baseWeight, 
            Set<String> matchedTargetSet,
            Set<String> matchedOcrSet
    ) {
        float score = 0f;
        for (String target : targetTokens) {
            float weight = getTokenWeight(target) * baseWeight;

            for (String ocr : ocrTokens) {
                // Exact Match
                if (ocr.equalsIgnoreCase(target)) {
                    score += weight;
                    matchedTargetSet.add(target);
                    matchedOcrSet.add(ocr);
                    break;
                }

                // Partial Match (only for non-numeric tokens of sufficient length)
                if (target.length() >= 4 && !target.matches(".*\\d.*") && (ocr.contains(target) || target.contains(ocr))) {
                    score += weight * 0.8f;
                    matchedTargetSet.add(target);
                    matchedOcrSet.add(ocr);
                    break;
                }

                // Fuzzy Match
                if (isFuzzyMatch(ocr, target)) {
                    score += weight * 0.6f;
                    matchedTargetSet.add(target);
                    matchedOcrSet.add(ocr);
                    break;
                }
            }
        }
        return score;
    }

    private static float calculateOcrRelevance(List<String> ocrTokens, Set<String> matchedOcrTokens) {
        float totalOcrWeight = 0f;
        float matchedOcrWeight = 0f;

        for (String ocr : ocrTokens) {
            float w = getTokenWeight(ocr);
            totalOcrWeight += w;
            if (matchedOcrTokens.contains(ocr)) {
                matchedOcrWeight += w;
            }
        }

        return totalOcrWeight > 0 ? (matchedOcrWeight / totalOcrWeight) : 0;
    }

    private static float getTokenWeight(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        // Step 3: Penalize generic tokens
        if (GENERIC_TOKENS.contains(lower)) {
            return GENERIC_PENALTY;
        }
        // Step 2: Weight numbers heavily
        if (lower.matches(".*\\d.*")) {
            return NUMERIC_BONUS;
        }
        return 1.0f;
    }

    private static boolean isFuzzyMatch(String s1, String s2) {
        if (s1.length() < 4 || s2.length() < 4) return false;
        if (Math.abs(s1.length() - s2.length()) > 2) return false;
        int distance = levenshteinDistance(s1, s2);
        // Strict fuzzy matching for medicines to avoid false positives
        return distance <= 1; 
    }

    private static int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[s1.length()][s2.length()];
    }

    private static String cleanOcrText(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("(?i)iso\\s?\\d+", " ")
                .replaceAll("(?i)lic\\.\\s?no\\.?", " ")
                .replaceAll("[^a-z0-9]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Step 4: Improve Token Matching
     * Splits text into tokens and extracts numeric parts from alphanumeric strings.
     */
    private static List<String> tokenize(String text) {
        if (text == null) return new ArrayList<>();
        String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", " ");
        String[] parts = cleaned.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            if (p.length() < 2) continue;
            tokens.add(p);
            
            // If alphanumeric, also extract the numeric part as a separate token
            if (p.matches(".*[a-z].*") && p.matches(".*\\d.*")) {
                String numbers = p.replaceAll("[^0-9]", "");
                if (numbers.length() >= 2) {
                    tokens.add(numbers);
                }
            }
        }
        return tokens;
    }

    private static void logTopCandidates(List<Candidate> candidates) {
        int count = Math.min(candidates.size(), 5);
        StringBuilder sb = new StringBuilder("TOP_CANDIDATES:\n");
        for (int i = 0; i < count; i++) {
            Candidate c = candidates.get(i);
            sb.append(String.format(Locale.US, "  %d. %s (Score: %.2f)\n", i + 1, c.medicine.getName(), c.score));
        }
        LogUtils.d(sb.toString());
    }
}
