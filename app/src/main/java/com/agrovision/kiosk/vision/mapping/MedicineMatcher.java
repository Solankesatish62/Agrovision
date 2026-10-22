package com.agrovision.kiosk.vision.mapping;

import com.agrovision.kiosk.data.model.Medicine;
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
 */
public final class MedicineMatcher {

    private static final String LOG_TAG = "MedicineMatcher";

    private static final float MIN_CONFIDENCE_THRESHOLD = 0.68f;
    private static final float HIGH_CONFIDENCE_THRESHOLD = 0.82f;
    private static final float CLOSE_MATCH_GAP = 0.12f;

    private static final float BRAND_NAME_WEIGHT = 3.5f; 
    private static final float KEYWORD_WEIGHT = 2.5f;
    private static final float NUMERIC_BONUS = 5.0f;
    private static final float GENERIC_PENALTY = 0.2f;  

    private static final List<String> GENERIC_TOKENS = Arrays.asList(
            "bio", "plus", "gold", "super", "vita", "agro", "ultra", "power", "veta",
            "max", "premium", "extra", "active", "advance", "shakti", "krishi",
            "insecticide", "fungicide", "herbicide", "pesticide", "liquid", "powder"
    );

    private static final Map<String, List<String>> TOKEN_CACHE = new HashMap<>();
    private static List<Medicine> lastIndexedCatalog = null;
    private static final Map<String, Set<Medicine>> INVERTED_INDEX = new HashMap<>();
    private static final Map<String, Set<Medicine>> NUMERIC_INDEX = new HashMap<>();

    private MedicineMatcher() {}

    private static void logSafe(String tag, String message) {
        try {
            android.util.Log.i(tag, message);
        } catch (RuntimeException e) {
            // Unit test environment fallback
            System.out.println("[" + tag + "] " + message);
        }
    }

    private static long getElapsedRealtime() {
        try {
            return android.os.SystemClock.elapsedRealtime();
        } catch (RuntimeException e) {
            return System.currentTimeMillis();
        }
    }

    private static synchronized void ensureIndexed(List<Medicine> medicines) {
        if (medicines == null || medicines == lastIndexedCatalog) return;

        PerformanceProfiler.start("Matcher Indexing");
        INVERTED_INDEX.clear();
        NUMERIC_INDEX.clear();
        TOKEN_CACHE.clear();
        
        for (Medicine m : medicines) {
            Set<String> tokens = new HashSet<>();
            tokens.addAll(tokenize(m.getName()));
            if (m.getSearchKeywords() != null) {
                for (String kw : m.getSearchKeywords()) {
                    tokens.addAll(tokenize(kw));
                }
            }
            if (m.getCompany() != null) {
                tokens.addAll(tokenize(m.getCompany()));
            }

            for (String token : tokens) {
                String lowerToken = token.toLowerCase(Locale.ROOT);
                INVERTED_INDEX.computeIfAbsent(lowerToken, k -> new HashSet<>()).add(m);
                
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

    public static class CandidateDetail implements Comparable<CandidateDetail> {
        public final Medicine medicine;
        public final float finalScore;
        public final float nameRatio;
        public final float nameMatchScore;
        public final float maxNameScore;
        public final Set<String> matchedNameTokens;
        
        public final float maxKeywordRatio;
        public final String bestMatchedKeyword;
        public final Set<String> matchedKeywordTokens;
        
        public final float companyBonus;
        public final Set<String> matchedCompanyTokens;
        
        public final float ocrRelevance;
        public final Set<String> matchedOcrTokens;

        CandidateDetail(
                Medicine medicine,
                float finalScore,
                float nameRatio,
                float nameMatchScore,
                float maxNameScore,
                Set<String> matchedNameTokens,
                float maxKeywordRatio,
                String bestMatchedKeyword,
                Set<String> matchedKeywordTokens,
                float companyBonus,
                Set<String> matchedCompanyTokens,
                float ocrRelevance,
                Set<String> matchedOcrTokens
        ) {
            this.medicine = medicine;
            this.finalScore = finalScore;
            this.nameRatio = nameRatio;
            this.nameMatchScore = nameMatchScore;
            this.maxNameScore = maxNameScore;
            this.matchedNameTokens = matchedNameTokens;
            this.maxKeywordRatio = maxKeywordRatio;
            this.bestMatchedKeyword = bestMatchedKeyword;
            this.matchedKeywordTokens = matchedKeywordTokens;
            this.companyBonus = companyBonus;
            this.matchedCompanyTokens = matchedCompanyTokens;
            this.ocrRelevance = ocrRelevance;
            this.matchedOcrTokens = matchedOcrTokens;
        }

        @Override
        public int compareTo(CandidateDetail other) {
            return Float.compare(other.finalScore, this.finalScore);
        }
    }

    public static MatchResult match(String normalizedText, List<Medicine> medicines) {
        return match(normalizedText, medicines, null);
    }

    public static MatchResult match(String normalizedText, List<Medicine> medicines, com.agrovision.kiosk.camera.ScanMetrics metrics) {
        if (normalizedText == null || normalizedText.trim().isEmpty() || medicines == null || medicines.isEmpty()) {
            return MatchResult.none(normalizedText);
        }

        long matchStartMs = getElapsedRealtime();

        PerformanceProfiler.start("Medicine Matcher");

        // 1. Indexing check & inverted index build duration
        long indexStartMs = getElapsedRealtime();
        ensureIndexed(medicines);
        long indexMs = getElapsedRealtime() - indexStartMs;

        // 2. OCR text cleaning and tokenization duration
        long tokenizeStartMs = getElapsedRealtime();
        String cleanedOcr = cleanOcrText(normalizedText);
        List<String> ocrTokens = tokenize(cleanedOcr);
        long tokenizeMs = getElapsedRealtime() - tokenizeStartMs;

        if (ocrTokens.isEmpty()) {
            PerformanceProfiler.end("Medicine Matcher");
            long totalMs = getElapsedRealtime() - matchStartMs;
            if (metrics != null) {
                metrics.matchIndexMs = indexMs;
                metrics.matchTokenizeMs = tokenizeMs;
                metrics.matchingMs = totalMs;
            }
            return MatchResult.none(normalizedText);
        }

        // 3. Inverted index candidate retrieval duration
        long lookupStartMs = getElapsedRealtime();
        Set<Medicine> candidateSet = new HashSet<>();
        for (String ocrToken : ocrTokens) {
            String lowerToken = ocrToken.toLowerCase(Locale.ROOT);
            
            Set<Medicine> matches = INVERTED_INDEX.get(lowerToken);
            if (matches != null) {
                candidateSet.addAll(matches);
            }
            
            if (lowerToken.matches(".*\\d.*")) {
                String numbersOnly = lowerToken.replaceAll("[^0-9]", "");
                if (numbersOnly.length() >= 2 && !isCommonGenericNumber(numbersOnly)) {
                    Set<Medicine> numMatches = NUMERIC_INDEX.get(numbersOnly);
                    if (numMatches != null) {
                        candidateSet.addAll(numMatches);
                    }
                }
            }
        }

        if (candidateSet.isEmpty()) {
             candidateSet.addAll(medicines);
        }
        long lookupMs = getElapsedRealtime() - lookupStartMs;

        // 4. Detailed Candidate Scoring duration
        long scoringStartMs = getElapsedRealtime();
        List<CandidateDetail> candidateDetails = new ArrayList<>();
        for (Medicine medicine : candidateSet) {
            CandidateDetail detail = calculateCandidateDetail(ocrTokens, medicine);
            if (detail.finalScore > 0.05f) {
                candidateDetails.add(detail);
            }
        }
        long scoringMs = getElapsedRealtime() - scoringStartMs;

        // 5. Ranking & Decision duration
        long sortStartMs = getElapsedRealtime();

        StringBuilder sb = new StringBuilder();
        sb.append("\n==================== OCR MATCHING ANALYSIS ====================\n");
        sb.append("Input OCR Text: \"").append(normalizedText).append("\"\n");
        sb.append("Tokenized OCR: ").append(ocrTokens).append(" (").append(ocrTokens.size()).append(" tokens)\n");
        sb.append("Total Catalog Size: ").append(medicines.size()).append(" | Filtered Candidates: ").append(candidateSet.size()).append("\n");
        sb.append("--------------------------------------------------------------\n");

        if (candidateDetails.isEmpty()) {
            long sortMs = getElapsedRealtime() - sortStartMs;
            long totalMatchMs = getElapsedRealtime() - matchStartMs;

            if (metrics != null) {
                metrics.matchIndexMs = indexMs;
                metrics.matchTokenizeMs = tokenizeMs;
                metrics.matchLookupMs = lookupMs;
                metrics.matchScoringMs = scoringMs;
                metrics.matchSortMs = sortMs;
                metrics.candidateCount = candidateSet.size();
                metrics.totalCatalogSize = medicines.size();
                metrics.matchingMs = totalMatchMs;
            }

            sb.append("⏱️ MATCHING TIME PROFILING BREAKDOWN:\n");
            sb.append(String.format(Locale.ROOT, "   - Index Check / Build           : %d ms\n", indexMs));
            sb.append(String.format(Locale.ROOT, "   - Text Cleaning & Tokenization   : %d ms\n", tokenizeMs));
            sb.append(String.format(Locale.ROOT, "   - Inverted Index Candidate Search: %d ms (Selected %d candidates from %d total catalog)\n", lookupMs, candidateSet.size(), medicines.size()));
            sb.append(String.format(Locale.ROOT, "   - Candidate Scoring & Evaluation : %d ms (Evaluated %d candidates)\n", scoringMs, candidateSet.size()));
            sb.append(String.format(Locale.ROOT, "   - Candidate Ranking & Gap Penalty: %d ms\n", sortMs));
            sb.append("   -----------------------------------------------------------\n");
            sb.append(String.format(Locale.ROOT, "   - TOTAL MEDICINE MATCHING TIME   : %d ms\n", totalMatchMs));
            sb.append("--------------------------------------------------------------\n");

            sb.append("NO CANDIDATES SCORED ABOVE THRESHOLD 0.05\n");
            sb.append("--------------------------------------------------------------\n");
            sb.append("MATCH DECISION: NO MATCH [NONE] (Reason: No valid candidates found above min threshold)\n");
            sb.append("==============================================================");
            logSafe(LOG_TAG, sb.toString());
            PerformanceProfiler.end("Medicine Matcher");
            return MatchResult.none(normalizedText);
        }

        Collections.sort(candidateDetails);

        sb.append("EVALUATED CANDIDATES (Ranked by Score):\n");
        int rank = 1;
        for (CandidateDetail cd : candidateDetails) {
            if (rank > 5) break; // Log top 5 candidates to keep log readable
            sb.append(String.format(Locale.ROOT, "#%d: %s (%s) [ID: %s]\n",
                    rank, cd.medicine.getName(), cd.medicine.getCompany() != null ? cd.medicine.getCompany() : "N/A", cd.medicine.getId()));
            sb.append(String.format(Locale.ROOT, "   - Final Calculated Score: %.3f\n", cd.finalScore));
            sb.append("   - Component Breakdown:\n");
            sb.append(String.format(Locale.ROOT, "       * Brand Name Match Ratio: %.3f (40%% weight) -> Score Contribution: %.3f | Matched Brand Tokens: %s\n",
                    cd.nameRatio, cd.nameRatio * 0.40f, cd.matchedNameTokens));
            sb.append(String.format(Locale.ROOT, "       * Keyword Match Ratio   : %.3f (40%% weight) -> Score Contribution: %.3f | Best Keyword: \"%s\" | Matched Keywords: %s\n",
                    cd.maxKeywordRatio, cd.maxKeywordRatio * 0.40f, cd.bestMatchedKeyword != null ? cd.bestMatchedKeyword : "N/A", cd.matchedKeywordTokens));
            sb.append(String.format(Locale.ROOT, "       * OCR Token Relevance   : %.3f (20%% weight) -> Score Contribution: %.3f | Matched OCR Tokens: %s of %d\n",
                    cd.ocrRelevance, cd.ocrRelevance * 0.20f, cd.matchedOcrTokens, ocrTokens.size()));
            sb.append(String.format(Locale.ROOT, "       * Company Bonus         : +%.3f | Matched Company Tokens: %s\n",
                    cd.companyBonus, cd.matchedCompanyTokens));
            sb.append(String.format(Locale.ROOT, "   - Score Formula: (%.3f * 0.40) + (%.3f * 0.40) + (%.3f * 0.20) + %.3f = %.3f\n",
                    cd.nameRatio, cd.maxKeywordRatio, cd.ocrRelevance, cd.companyBonus, cd.finalScore));
            rank++;
        }

        sb.append("--------------------------------------------------------------\n");
        sb.append("MATCH DECISION SUMMARY:\n");

        CandidateDetail top = candidateDetails.get(0);
        float confidence = top.finalScore;

        sb.append(String.format(Locale.ROOT, "   Top Candidate   : %s (Raw Score: %.3f)\n", top.medicine.getName(), top.finalScore));

        if (candidateDetails.size() > 1) {
            CandidateDetail runnerUp = candidateDetails.get(1);
            float gap = top.finalScore - runnerUp.finalScore;
            sb.append(String.format(Locale.ROOT, "   Runner-Up       : %s (Raw Score: %.3f)\n", runnerUp.medicine.getName(), runnerUp.finalScore));
            sb.append(String.format(Locale.ROOT, "   Confidence Gap  : %.3f (Close Match Gap Threshold: %.3f)\n", gap, CLOSE_MATCH_GAP));
            if (gap < CLOSE_MATCH_GAP) {
                confidence *= 0.80f;
                sb.append(String.format(Locale.ROOT, "   -> GAP PENALTY APPLIED: 20%% reduction -> Adjusted Confidence: %.3f\n", confidence));
            } else {
                sb.append("   -> Gap Penalty Applied: NO\n");
            }
        } else {
            sb.append("   Runner-Up       : NONE\n");
        }

        sb.append(String.format(Locale.ROOT, "   Threshold Checks: Required Min = %.3f | Required High = %.3f\n",
                MIN_CONFIDENCE_THRESHOLD, HIGH_CONFIDENCE_THRESHOLD));

        MatchResult matchResult;
        if (confidence < MIN_CONFIDENCE_THRESHOLD) {
            matchResult = MatchResult.none(normalizedText);
            sb.append(String.format(Locale.ROOT, "   FINAL RESULT    : NO MATCH [NONE] (Confidence %.3f < Min Threshold %.3f)\n",
                    confidence, MIN_CONFIDENCE_THRESHOLD));
        } else if (confidence >= HIGH_CONFIDENCE_THRESHOLD) {
            matchResult = MatchResult.exact(top.medicine, normalizedText);
            sb.append(String.format(Locale.ROOT, "   FINAL RESULT    : MATCH SUCCESS [EXACT] -> Medicine: \"%s\" (Confidence: %.3f >= High Threshold %.3f)\n",
                    top.medicine.getName(), confidence, HIGH_CONFIDENCE_THRESHOLD));
        } else {
            matchResult = MatchResult.fuzzy(top.medicine, confidence, normalizedText);
            sb.append(String.format(Locale.ROOT, "   FINAL RESULT    : MATCH SUCCESS [FUZZY] -> Medicine: \"%s\" (Confidence: %.3f)\n",
                    top.medicine.getName(), confidence));
        }

        long sortMs = getElapsedRealtime() - sortStartMs;
        long totalMatchMs = getElapsedRealtime() - matchStartMs;

        if (metrics != null) {
            metrics.matchIndexMs = indexMs;
            metrics.matchTokenizeMs = tokenizeMs;
            metrics.matchLookupMs = lookupMs;
            metrics.matchScoringMs = scoringMs;
            metrics.matchSortMs = sortMs;
            metrics.candidateCount = candidateSet.size();
            metrics.totalCatalogSize = medicines.size();
            metrics.matchingMs = totalMatchMs;
        }

        sb.append("\n--------------------------------------------------------------\n");
        sb.append("⏱️ MATCHING TIME PROFILING BREAKDOWN:\n");
        sb.append(String.format(Locale.ROOT, "   - Index Check / Build           : %d ms\n", indexMs));
        sb.append(String.format(Locale.ROOT, "   - Text Cleaning & Tokenization   : %d ms\n", tokenizeMs));
        sb.append(String.format(Locale.ROOT, "   - Inverted Index Candidate Search: %d ms (Selected %d candidates from %d total catalog)\n", lookupMs, candidateSet.size(), medicines.size()));
        sb.append(String.format(Locale.ROOT, "   - Candidate Scoring & Evaluation : %d ms (Evaluated %d candidates, ~%.2f ms/candidate)\n", scoringMs, candidateSet.size(), !candidateSet.isEmpty() ? (float) scoringMs / candidateSet.size() : 0f));
        sb.append(String.format(Locale.ROOT, "   - Candidate Ranking & Gap Penalty: %d ms\n", sortMs));
        sb.append("   -----------------------------------------------------------\n");
        sb.append(String.format(Locale.ROOT, "   - TOTAL MEDICINE MATCHING TIME   : %d ms\n", totalMatchMs));
        sb.append("==============================================================");

        logSafe(LOG_TAG, sb.toString());

        PerformanceProfiler.end("Medicine Matcher");
        return matchResult;
    }

    private static boolean isCommonGenericNumber(String num) {
        return num.equals("25") || num.equals("50") || num.equals("10") || num.equals("100") || num.equals("500");
    }

    private static CandidateDetail calculateCandidateDetail(List<String> ocrTokens, Medicine medicine) {
        Set<String> matchedNameTokens = new HashSet<>();
        Set<String> matchedOcrTokens = new HashSet<>();

        List<String> nameTokens = getCachedTokens(medicine.getName());
        float nameMatchScore = matchTargetTokens(ocrTokens, nameTokens, BRAND_NAME_WEIGHT, matchedNameTokens, matchedOcrTokens);

        float maxKeywordRatio = 0f;
        String bestKeyword = null;
        Set<String> matchedKeywordTokens = new HashSet<>();

        List<String> rawKeywords = medicine.getSearchKeywords();
        if (rawKeywords != null) {
            for (String kw : rawKeywords) {
                List<String> kwTokens = getCachedTokens(kw);
                if (kwTokens.isEmpty()) continue;
                
                Set<String> kwMatchedTarget = new HashSet<>();
                float kwMatchScore = matchTargetTokens(ocrTokens, kwTokens, KEYWORD_WEIGHT, kwMatchedTarget, matchedOcrTokens);
                
                float kwMaxScore = 0f;
                for (String t : kwTokens) kwMaxScore += getTokenWeight(t) * KEYWORD_WEIGHT;
                
                float kwRatio = kwMaxScore > 0 ? kwMatchScore / kwMaxScore : 0;
                if (kwRatio > maxKeywordRatio) {
                    maxKeywordRatio = kwRatio;
                    bestKeyword = kw;
                }
                if (!kwMatchedTarget.isEmpty()) {
                    matchedKeywordTokens.addAll(kwMatchedTarget);
                }
            }
        }

        float companyBonus = 0f;
        Set<String> matchedCompanyTokens = new HashSet<>();
        if (medicine.getCompany() != null && !medicine.getCompany().isEmpty()) {
            List<String> companyTokens = getCachedTokens(medicine.getCompany());
            for (String ct : companyTokens) {
                if (ocrTokens.contains(ct.toLowerCase(Locale.ROOT))) {
                    companyBonus += 0.05f;
                    matchedCompanyTokens.add(ct);
                }
            }
        }

        float maxNameScore = 0f;
        for (String t : nameTokens) maxNameScore += getTokenWeight(t) * BRAND_NAME_WEIGHT;
        float nameRatio = maxNameScore > 0 ? nameMatchScore / maxNameScore : 0;

        float ocrRelevance = calculateOcrRelevance(ocrTokens, matchedOcrTokens);
        float rawScore = (nameRatio * 0.40f) + (maxKeywordRatio * 0.40f) + (ocrRelevance * 0.20f) + companyBonus;
        float finalScore = Math.min(rawScore, 1.0f);

        return new CandidateDetail(
                medicine,
                finalScore,
                nameRatio,
                nameMatchScore,
                maxNameScore,
                matchedNameTokens,
                maxKeywordRatio,
                bestKeyword,
                matchedKeywordTokens,
                companyBonus,
                matchedCompanyTokens,
                ocrRelevance,
                matchedOcrTokens
        );
    }

    private static List<String> getCachedTokens(String text) {
        if (text == null) return new ArrayList<>();
        if (TOKEN_CACHE.containsKey(text)) return TOKEN_CACHE.get(text);
        List<String> tokens = tokenize(text);
        TOKEN_CACHE.put(text, tokens);
        return tokens;
    }

    private static float matchTargetTokens(List<String> ocrTokens, List<String> targetTokens, float baseWeight, Set<String> matchedTargetSet, Set<String> matchedOcrSet) {
        float score = 0f;
        for (String target : targetTokens) {
            float weight = getTokenWeight(target) * baseWeight;
            boolean isGeneric = GENERIC_TOKENS.contains(target.toLowerCase(Locale.ROOT));

            for (String ocr : ocrTokens) {
                if (ocr.equalsIgnoreCase(target)) {
                    score += weight;
                    matchedTargetSet.add(target);
                    matchedOcrSet.add(ocr);
                    break;
                }
                if (!isGeneric && isFuzzyMatch(ocr, target)) {
                    score += weight * 0.7f;
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
            if (matchedOcrTokens.contains(ocr)) matchedOcrWeight += w;
        }
        return totalOcrWeight > 0 ? (matchedOcrWeight / totalOcrWeight) : 0;
    }

    private static float getTokenWeight(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        if (GENERIC_TOKENS.contains(lower)) return GENERIC_PENALTY;
        if (lower.matches(".*\\d.*")) return NUMERIC_BONUS;
        return 1.0f;
    }

    private static boolean isFuzzyMatch(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 < 4 || len2 < 4) return false;
        if (Math.abs(len1 - len2) > 1) return false;
        
        int edits = 0;
        int i = 0, j = 0;
        while (i < len1 && j < len2) {
            if (s1.charAt(i) != s2.charAt(j)) {
                if (edits == 1) return false;
                if (len1 > len2) i++;
                else if (len1 < len2) j++;
                else { i++; j++; }
                edits++;
            } else { i++; j++; }
        }
        if (i < len1 || j < len2) edits++;
        return edits <= 1;
    }

    private static String cleanOcrText(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static List<String> tokenize(String text) {
        if (text == null) return new ArrayList<>();
        String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", " ").trim();
        String[] parts = cleaned.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String token : parts) {
            if (token.length() < 2 && !token.matches("\\d")) continue;
            if (!tokens.contains(token)) tokens.add(token);
        }
        return tokens;
    }
}
