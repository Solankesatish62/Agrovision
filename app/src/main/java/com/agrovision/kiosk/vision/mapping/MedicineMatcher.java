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
 */
public final class MedicineMatcher {

    private static final float MIN_CONFIDENCE_THRESHOLD = 0.68f;
    private static final float HIGH_CONFIDENCE_THRESHOLD = 0.82f;
    private static final float CLOSE_MATCH_GAP = 0.12f;

    private static final float BRAND_NAME_WEIGHT = 3.5f; 
    private static final float KEYWORD_WEIGHT = 2.5f;
    private static final float NUMERIC_BONUS = 5.0f;
    private static final float GENERIC_PENALTY = 0.2f;  
    private static final float COMPANY_BONUS_WEIGHT = 1.8f; 

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

    private static synchronized void ensureIndexed(List<Medicine> medicines) {
        if (medicines == null || medicines == lastIndexedCatalog) return;

        PerformanceProfiler.start("Matcher Indexing");
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

    private static class Candidate implements Comparable<Candidate> {
        final Medicine medicine;
        final float score;

        Candidate(Medicine medicine, float score) {
            this.medicine = medicine;
            this.score = score;
        }

        @Override
        public int compareTo(Candidate other) {
            return Float.compare(other.score, this.score);
        }
    }

    public static MatchResult match(String normalizedText, List<Medicine> medicines) {
        if (normalizedText == null || normalizedText.trim().isEmpty() || medicines == null || medicines.isEmpty()) {
            return MatchResult.none(normalizedText);
        }

        PerformanceProfiler.start("Medicine Matcher");
        ensureIndexed(medicines);
        
        String cleanedOcr = cleanOcrText(normalizedText);
        List<String> ocrTokens = tokenize(cleanedOcr);

        if (ocrTokens.isEmpty()) {
            PerformanceProfiler.end("Medicine Matcher");
            return MatchResult.none(normalizedText);
        }

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

        List<Candidate> candidates = new ArrayList<>();
        for (Medicine medicine : candidateSet) {
            float score = calculateScore(ocrTokens, medicine);
            if (score > 0.05f) {
                candidates.add(new Candidate(medicine, score));
            }
        }

        if (candidates.isEmpty()) {
            PerformanceProfiler.end("Medicine Matcher");
            return MatchResult.none(normalizedText);
        }

        Collections.sort(candidates);
        Candidate top = candidates.get(0);
        float confidence = top.score;

        if (candidates.size() > 1) {
            float gap = top.score - candidates.get(1).score;
            if (gap < CLOSE_MATCH_GAP) {
                confidence *= 0.80f; 
            }
        }

        PerformanceProfiler.end("Medicine Matcher");
        if (confidence < MIN_CONFIDENCE_THRESHOLD) {
            return MatchResult.none(normalizedText);
        }

        if (confidence >= HIGH_CONFIDENCE_THRESHOLD) {
            return MatchResult.exact(top.medicine, normalizedText);
        } else {
            return MatchResult.fuzzy(top.medicine, confidence, normalizedText);
        }
    }

    private static boolean isCommonGenericNumber(String num) {
        return num.equals("25") || num.equals("50") || num.equals("10") || num.equals("100") || num.equals("500");
    }

    private static float calculateScore(List<String> ocrTokens, Medicine medicine) {
        Set<String> matchedTargetTokens = new HashSet<>();
        Set<String> matchedOcrTokens = new HashSet<>();

        List<String> nameTokens = getCachedTokens(medicine.getName());
        float nameMatchScore = matchTargetTokens(ocrTokens, nameTokens, BRAND_NAME_WEIGHT, matchedTargetTokens, matchedOcrTokens);

        float maxKeywordRatio = 0f;
        List<String> rawKeywords = medicine.getSearchKeywords();
        for (String kw : rawKeywords) {
            List<String> kwTokens = getCachedTokens(kw);
            if (kwTokens.isEmpty()) continue;
            
            Set<String> kwMatchedTarget = new HashSet<>();
            float kwMatchScore = matchTargetTokens(ocrTokens, kwTokens, KEYWORD_WEIGHT, kwMatchedTarget, matchedOcrTokens);
            
            float kwMaxScore = 0f;
            for (String t : kwTokens) kwMaxScore += getTokenWeight(t) * KEYWORD_WEIGHT;
            
            float kwRatio = kwMaxScore > 0 ? kwMatchScore / kwMaxScore : 0;
            if (kwRatio > maxKeywordRatio) maxKeywordRatio = kwRatio;
            if (!kwMatchedTarget.isEmpty()) matchedTargetTokens.addAll(kwMatchedTarget);
        }

        float companyBonus = 0f;
        if (medicine.getCompany() != null && !medicine.getCompany().isEmpty()) {
            List<String> companyTokens = getCachedTokens(medicine.getCompany());
            for (String ct : companyTokens) {
                if (ocrTokens.contains(ct.toLowerCase(Locale.ROOT))) companyBonus += 0.05f;
            }
        }

        float maxNameScore = 0f;
        for (String t : nameTokens) maxNameScore += getTokenWeight(t) * BRAND_NAME_WEIGHT;
        float nameRatio = maxNameScore > 0 ? nameMatchScore / maxNameScore : 0;

        float ocrRelevance = calculateOcrRelevance(ocrTokens, matchedOcrTokens);
        float score = (nameRatio * 0.40f) + (maxKeywordRatio * 0.40f) + (ocrRelevance * 0.20f);
        
        score += companyBonus;
        return Math.min(score, 1.0f);
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
