package com.agrovision.kiosk.vision.mapping;

import com.agrovision.kiosk.data.model.Medicine;
import com.agrovision.kiosk.util.LogUtils;
import com.agrovision.kiosk.util.PerformanceProfiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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



    // Step 11: Confidence Thresholds (STRICKER FOR TRUST)
    private static final float MIN_CONFIDENCE_THRESHOLD = 0.68f; // Reduced from 0.75 for better recall
    private static final float HIGH_CONFIDENCE_THRESHOLD = 0.82f; // Reduced from 0.85
    private static final float CLOSE_MATCH_GAP = 0.12f; // Reduced from 0.20

    // Step 4 & 5: Weights
    private static final float BRAND_NAME_WEIGHT = 3.5f; 
    private static final float KEYWORD_WEIGHT = 2.5f; // Increased from 1.2f to make keywords high priority
    private static final float NUMERIC_BONUS = 5.0f;
    private static final float GENERIC_PENALTY = 0.2f;  
    private static final float COMPANY_BONUS_WEIGHT = 1.8f; 

    // Step 5: Generic words to penalize
    private static final List<String> GENERIC_TOKENS = Arrays.asList(
            "bio", "plus", "gold", "super", "vita", "agro", "ultra", "power", "veta",
            "max", "premium", "extra", "active", "advance", "shakti", "krishi",
            "insecticide", "fungicide", "herbicide", "pesticide", "liquid", "powder"
    );

    // Step 14: Search Index Optimization (Cache)
    private static final Map<String, List<String>> TOKEN_CACHE = new HashMap<>();

    // NEW: Inverted Index for massive performance boost
    private static List<Medicine> lastIndexedCatalog = null;
    private static final Map<String, Set<Medicine>> INVERTED_INDEX = new HashMap<>();
    private static final Map<String, Set<Medicine>> NUMERIC_INDEX = new HashMap<>();

    private MedicineMatcher() {}

    /**
     * Pre-builds an inverted index for the medicine catalog.
     * Call this when the catalog is loaded or updated.
     */
    private static synchronized void ensureIndexed(List<Medicine> medicines) {
        if (medicines == null || medicines == lastIndexedCatalog) return;

        PerformanceProfiler.start("Matcher Indexing");
        LogUtils.d("REBUILDING_MEDICINE_INDEX for " + medicines.size() + " items");
        
        INVERTED_INDEX.clear();
        NUMERIC_INDEX.clear();
        TOKEN_CACHE.clear();
        
        for (Medicine m : medicines) {
            Set<String> tokens = new HashSet<>();
            tokens.addAll(tokenize(m.getName()));
            for (String kw : m.getSearchKeywords()) {
                tokens.addAll(tokenize(kw));
            }
            if (m.getCompany() != null) {
                tokens.addAll(tokenize(m.getCompany()));
            }

            for (String token : tokens) {
                String lowerToken = token.toLowerCase(Locale.ROOT);
                INVERTED_INDEX.computeIfAbsent(lowerToken, k -> new HashSet<>()).add(m);
                
                // 🚀 OPTIMIZATION: Index numeric parts separately for fast lookup
                if (lowerToken.matches(".*\\d.*")) {
                    String numbersOnly = lowerToken.replaceAll("[^0-9]", "");
                    if (!numbersOnly.isEmpty()) {
                        NUMERIC_INDEX.computeIfAbsent(numbersOnly, k -> new HashSet<>()).add(m);
                    }
                }
            }
        }
        lastIndexedCatalog = medicines;
        PerformanceProfiler.end("Matcher Indexing");
    }

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

    private static int totalTokenComparisons = 0;
    private static int totalLevenshteinOperations = 0;

    public static MatchResult match(String normalizedText, List<Medicine> medicines) {
        if (normalizedText == null || normalizedText.trim().isEmpty() || medicines == null || medicines.isEmpty()) {
            return MatchResult.none(normalizedText);
        }

        PerformanceProfiler.start("Medicine Matcher");
        ensureIndexed(medicines);
        
        totalTokenComparisons = 0;
        totalLevenshteinOperations = 0;

        String cleanedOcr = cleanOcrText(normalizedText);
        List<String> ocrTokens = tokenize(cleanedOcr);

        // Step 12: Add Match Explanation Logs
        LogUtils.d("MATCHING_ENGINE_START");
        LogUtils.d("RAW_OCR: " + normalizedText);
        LogUtils.d("CLEANED_OCR: " + cleanedOcr);
        LogUtils.d("OCR_TOKENS: " + ocrTokens);

        if (ocrTokens.isEmpty()) {
            PerformanceProfiler.end("Medicine Matcher");
            return MatchResult.none(normalizedText);
        }

        // 🚀 OPTIMIZATION: Use inverted index to find candidate medicines
        Set<Medicine> candidateSet = new HashSet<>();
        for (String ocrToken : ocrTokens) {
            String lowerToken = ocrToken.toLowerCase(Locale.ROOT);
            
            // 1. Exact Token Match
            Set<Medicine> matches = INVERTED_INDEX.get(lowerToken);
            if (matches != null) {
                candidateSet.addAll(matches);
            }
            
            // 2. 🚀 FAST NUMERIC LOOKUP
            if (lowerToken.matches(".*\\d.*")) {
                String numbersOnly = lowerToken.replaceAll("[^0-9]", "");
                if (!numbersOnly.isEmpty()) {
                    Set<Medicine> numMatches = NUMERIC_INDEX.get(numbersOnly);
                    if (numMatches != null) {
                        candidateSet.addAll(numMatches);
                    }
                }
            }
        }

        // If no candidates found through exact/substring, we might need a broader search
        // but for performance, we limit this.
        if (candidateSet.isEmpty()) {
             LogUtils.w("No candidates found via index. Performing full scan (emergency fallback)");
             candidateSet.addAll(medicines);
        }

        List<Candidate> candidates = new ArrayList<>();
        int keywordCount = 0;
        for (Medicine medicine : candidateSet) {
            keywordCount += medicine.getSearchKeywords().size();
            float score = calculateScore(ocrTokens, medicine);
            if (score > 0.05f) {
                candidates.add(new Candidate(medicine, score));
            }
        }

        PerformanceProfiler.log("Medicine Matcher", "Medicines Compared: " + candidateSet.size() + " / " + medicines.size());
        PerformanceProfiler.log("Medicine Matcher", "Keywords Compared: " + keywordCount);
        PerformanceProfiler.log("Medicine Matcher", "Total Token Comparisons: " + totalTokenComparisons);
        PerformanceProfiler.log("Medicine Matcher", "Levenshtein Operations: " + totalLevenshteinOperations);

        if (candidates.isEmpty()) {
            LogUtils.w("MATCH_FAILED: No candidates found.");
            PerformanceProfiler.end("Medicine Matcher");
            return MatchResult.none(normalizedText);
        }

        // Sort by score
        Collections.sort(candidates);
        
        // Step 12: Log top 5 candidates
        logTopCandidates(candidates);

        Candidate top = candidates.get(0);
        PerformanceProfiler.log("Medicine Matcher", "Best Candidate: " + top.medicine.getName());

        PerformanceProfiler.start("Confidence Calculation");
        float confidence = top.score;

        // Step 10: Close Score Protection
        if (candidates.size() > 1) {
            Candidate second = candidates.get(1);
            float gap = top.score - second.score;
            if (gap < CLOSE_MATCH_GAP) {
                LogUtils.w(String.format(Locale.US, "AMBIGUOUS_MATCH: Gap (%.2f) too small. Marking as Low Confidence.", gap));
                // If the scores are very close, it's risky. Reduce confidence slightly.
                confidence *= 0.80f; 
            }
        }

        LogUtils.i(String.format(Locale.US, "FINAL_DECISION: %s (Confidence: %.2f)", 
                top.medicine.getName(), confidence));

        PerformanceProfiler.log("Confidence Calculation", "Final Confidence: " + confidence);
        PerformanceProfiler.log("Confidence Calculation", "Threshold: " + MIN_CONFIDENCE_THRESHOLD);
        PerformanceProfiler.end("Confidence Calculation");

        // Step 11: Confidence Thresholding (Trust-First Logic)
        if (confidence < MIN_CONFIDENCE_THRESHOLD) {
            LogUtils.w(String.format(Locale.US, "MATCH_REJECTED: Confidence %.2f below threshold %.2f.", confidence, MIN_CONFIDENCE_THRESHOLD));
            PerformanceProfiler.end("Medicine Matcher");
            return MatchResult.none(normalizedText);
        }

        PerformanceProfiler.end("Medicine Matcher");
        // Return appropriate MatchResult based on confidence levels (Step 11)
        if (confidence >= HIGH_CONFIDENCE_THRESHOLD) {
            return MatchResult.exact(top.medicine, normalizedText);
        } else {
            // Medium confidence (60-74)
            return MatchResult.fuzzy(top.medicine, confidence, normalizedText);
        }
    }

    private static float calculateScore(List<String> ocrTokens, Medicine medicine) {
        Set<String> matchedTargetTokens = new HashSet<>();
        Set<String> matchedOcrTokens = new HashSet<>();
        List<String> matchedKeywordsLog = new ArrayList<>();

        // 🚀 BRAINSTORMING: SCRIPT-AWARE NORMALIZATION
        boolean ocrHasMarathi = false;
        for (String t : ocrTokens) {
            if (t.matches(".*[\\u0900-\\u097F].*")) {
                ocrHasMarathi = true;
                break;
            }
        }

        // 1. Match Brand Name
        List<String> nameTokens = getCachedTokens(medicine.getName());
        float nameMatchScore = matchTargetTokens(ocrTokens, nameTokens, BRAND_NAME_WEIGHT, matchedTargetTokens, matchedOcrTokens);

        // 2. Match Search Keywords (Now High Priority)
        // Logic: We treat searchKeywords as a list of alternatives (synonyms).
        // We calculate the match ratio for each keyword and take the BEST one.
        // This prevents multiple synonyms in the DB from penalizing the score.
        float maxKeywordRatio = 0f;
        List<String> rawKeywords = medicine.getSearchKeywords();
        Set<String> allKeywordTokens = new HashSet<>(); // For coverage calculation log

        for (String kw : rawKeywords) {
            List<String> kwTokens = getCachedTokens(kw);
            if (kwTokens.isEmpty()) continue;
            allKeywordTokens.addAll(kwTokens);
            
            Set<String> kwMatchedTarget = new HashSet<>();
            float kwMatchScore = matchTargetTokens(ocrTokens, kwTokens, KEYWORD_WEIGHT, kwMatchedTarget, matchedOcrTokens);
            
            float kwMaxScore = 0f;
            for (String t : kwTokens) kwMaxScore += getTokenWeight(t) * KEYWORD_WEIGHT;
            
            float kwRatio = kwMaxScore > 0 ? kwMatchScore / kwMaxScore : 0;
            if (kwRatio > maxKeywordRatio) {
                maxKeywordRatio = kwRatio;
            }
            
            if (!kwMatchedTarget.isEmpty()) {
                matchedTargetTokens.addAll(kwMatchedTarget);
                // Log which keyword matched and how well
                String matchLog = kw + " (" + String.format(Locale.US, "%.2f", kwRatio) + ")";
                if (!matchedKeywordsLog.contains(matchLog)) {
                    matchedKeywordsLog.add(matchLog);
                }
            }
        }

        // 3. Company Bonus
        float companyBonus = 0f;
        if (medicine.getCompany() != null && !medicine.getCompany().isEmpty()) {
            List<String> companyTokens = getCachedTokens(medicine.getCompany());
            for (String ct : companyTokens) {
                if (ocrTokens.contains(ct.toLowerCase(Locale.ROOT))) {
                    companyBonus += 0.05f;
                }
            }
        }

        // Exact Match Bonus
        float exactBonus = 0f;
        String medNameClean = medicine.getName().replaceAll("\\(.*?\\)", "").toLowerCase(Locale.ROOT).trim();
        for (String ocr : ocrTokens) {
            if (ocr.equalsIgnoreCase(medNameClean) || ocr.equalsIgnoreCase(medicine.getName())) {
                exactBonus = 0.25f;
                break;
            }
        }

        // Normalization denominators
        List<String> relevantNameTokens = new ArrayList<>();
        for (String t : nameTokens) {
            if (ocrHasMarathi || !t.matches(".*[\\u0900-\\u097F].*")) {
                relevantNameTokens.add(t);
            }
        }
        if (relevantNameTokens.isEmpty()) relevantNameTokens.addAll(nameTokens);

        // Calculate Ratios
        float maxNameScore = 0f;
        for (String t : relevantNameTokens) maxNameScore += getTokenWeight(t) * BRAND_NAME_WEIGHT;
        float nameRatio = maxNameScore > 0 ? nameMatchScore / maxNameScore : 0;

        float keywordRatio = maxKeywordRatio;

        // Coverage (how many of the medicine's defining tokens were found)
        Set<String> allDefiningTokens = new HashSet<>(relevantNameTokens);
        allDefiningTokens.addAll(allKeywordTokens);
        int matchedDefiningCount = 0;
        for(String t : allDefiningTokens) if(matchedTargetTokens.contains(t)) matchedDefiningCount++;
        float coverage = (float) matchedDefiningCount / Math.max(1, allDefiningTokens.size());
        
        float ocrRelevance = calculateOcrRelevance(ocrTokens, matchedOcrTokens);

        // 🚀 REVISED SCORING FORMULA
        // Brand Name (40%) + Search Keywords (40%) + OCR Relevance (20%)
        // This makes keywords as important as the brand name.
        float score = (nameRatio * 0.40f) + (keywordRatio * 0.40f) + (ocrRelevance * 0.20f);
        
        // Apply Variants/Numeric Strictness
        boolean nameHasNumber = false;
        for(String t : relevantNameTokens) if(t.matches(".*\\d.*")) { nameHasNumber = true; break; }
        boolean matchedAnyNumber = false;
        for(String t : matchedOcrTokens) if(t.matches(".*\\d.*")) { matchedAnyNumber = true; break; }
        
        if (nameHasNumber && !matchedAnyNumber) {
            score *= 0.75f;
        }

        // Apply Bonuses
        if (companyBonus > 0) score += companyBonus;
        score += exactBonus;

        float finalScore = Math.min(score, 1.0f);

        // Debug Log as requested
        if (finalScore > 0.4f) {
            LogUtils.d(String.format(Locale.US, 
                "Medicine: %s\nDatabase Search Keywords: %s\nMatched OCR Keywords: %s\nKeyword Score: %.2f\nName Ratio: %.2f, Keyword Ratio: %.2f, Coverage: %.2f, Final: %.2f",
                medicine.getName(), rawKeywords, matchedKeywordsLog, keywordRatio, nameRatio, keywordRatio, coverage, finalScore));
        }

        return finalScore;
    }

    private static List<String> getCachedTokens(String text) {
        if (text == null) return new ArrayList<>();
        if (TOKEN_CACHE.containsKey(text)) {
            return TOKEN_CACHE.get(text);
        }
        List<String> tokens = tokenize(text);
        TOKEN_CACHE.put(text, tokens);
        return tokens;
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
            boolean isGeneric = GENERIC_TOKENS.contains(target.toLowerCase(Locale.ROOT));

            for (String ocr : ocrTokens) {
                totalTokenComparisons++;
                // Exact Match
                if (ocr.equalsIgnoreCase(target)) {
                    score += weight;
                    matchedTargetSet.add(target);
                    matchedOcrSet.add(ocr);
                    break;
                }

                // Partial Match
                // Only allow if the OCR actually contains the target token (e.g., brand name found in larger text)
                if (!isGeneric && target.length() >= 5 && !target.matches(".*\\d.*") && 
                    ocr.contains(target)) {
                    score += weight * 0.8f; // Increased from 0.6f
                    matchedTargetSet.add(target);
                    matchedOcrSet.add(ocr);
                    break;
                }

                // Fuzzy Match
                if (!isGeneric && isFuzzyMatch(ocr, target)) {
                    score += weight * 0.7f; // Increased from 0.5f
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
            boolean isMatched = matchedOcrTokens.contains(ocr);
            float w = getTokenWeight(ocr);
            
            // 🚀 BRAINSTORMING: NOISE SUPPRESSION
            // If a token is NOT matched and is numeric, it's likely noise (batch no, date).
            // Reduce its impact on the "relevance" penalty so stray numbers don't kill the score.
            if (!isMatched && ocr.matches(".*\\d.*")) {
                w *= 0.3f; 
            }
            
            totalOcrWeight += w;
            if (isMatched) {
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
        totalLevenshteinOperations++;
        int n = s1.length();
        int m = s2.length();
        if (n == 0) return m;
        if (m == 0) return n;

        // Space optimization: use two rows instead of a full matrix
        int[] p = new int[n + 1]; // 'previous' cost array, horizontally
        int[] d = new int[n + 1]; // cost array, horizontally

        for (int i = 0; i <= n; i++) p[i] = i;

        for (int j = 1; j <= m; j++) {
            char s2_j = s2.charAt(j - 1);
            d[0] = j;
            for (int i = 1; i <= n; i++) {
                int cost = s1.charAt(i - 1) == s2_j ? 0 : 1;
                // minimum of cell to the left+1, to the top+1, diagonally left and up +cost
                d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
            }
            // copy current distance counts to 'previous row' distance counts
            int[] temp = p;
            p = d;
            d = temp;
        }
        return p[n];
    }

    private static String cleanOcrText(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("(?i)iso\\s?\\d+", " ")
                .replaceAll("(?i)lic\\.\\s?no\\.?", " ")
                .replaceAll("[^a-z0-9.]", " ") // Keep dots for numeric versions (18.5)
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Step 2 & 4: Improve Token Matching
     * Splits text into tokens and extracts numeric parts from alphanumeric strings.
     */
    private static List<String> tokenize(String text) {
        if (text == null) return new ArrayList<>();
        // Normalize: lowercase and remove most special symbols but keep dots for numbers
        String cleaned = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        
        String[] parts = cleaned.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            String token = p;
            // Remove trailing dots from tokens
            if (token.endsWith(".")) {
                token = token.substring(0, token.length() - 1);
            }
            // Allow single digits (important for 5%, 8% etc) but skip single letters
            if (token.length() < 2 && !token.matches("\\d")) continue;
            
            if (!tokens.contains(token)) {
                tokens.add(token);
            }
            
            // Step 4: Heavily weight numbers - extract numeric parts
            // Case: r303 -> 303
            if (token.matches(".*[a-z].*") && token.matches(".*\\d.*")) {
                String numbers = token.replaceAll("[^0-9]", "");
                if (numbers.length() >= 1 && !tokens.contains(numbers)) {
                    tokens.add(numbers);
                }
            }

            // Case: 18.5 -> 185
            if (token.contains(".")) {
                String combined = token.replace(".", "");
                if (combined.length() >= 2 && !tokens.contains(combined)) {
                    tokens.add(combined);
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
