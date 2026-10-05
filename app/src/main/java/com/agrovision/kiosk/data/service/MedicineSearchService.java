package com.agrovision.kiosk.data.service;

import android.util.Log;

import com.agrovision.kiosk.data.model.Medicine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * MedicineSearchService
 *
 * Responsibility: Ranking and filtering medicine search results.
 */
public final class MedicineSearchService {
    private static final String TAG = "AGROVISION_SEARCH";
    private static final int MAX_RESULTS = 8;

    private MedicineSearchService() {}

    public static List<Medicine> search(String query, List<Medicine> catalog) {
        if (query == null || query.trim().length() < 1) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();
        String normalizedQuery = query.toLowerCase(Locale.ROOT).trim();
        String alphaNumericQuery = normalizedQuery.replaceAll("[^a-z0-9]", "");

        List<SearchResult> results = new ArrayList<>();

        for (Medicine medicine : catalog) {
            int score = calculateScore(medicine, normalizedQuery, alphaNumericQuery);
            if (score > 0) {
                results.add(new SearchResult(medicine, score));
            }
        }

        // Sort by score descending, then by name ascending
        Collections.sort(results, (r1, r2) -> {
            if (r2.score != r1.score) {
                return Integer.compare(r2.score, r1.score);
            }
            return r1.medicine.getName().compareToIgnoreCase(r2.medicine.getName());
        });

        List<Medicine> finalMedicines = new ArrayList<>();
        int count = Math.min(results.size(), MAX_RESULTS);
        for (int i = 0; i < count; i++) {
            finalMedicines.add(results.get(i).medicine);
        }

        long endTime = System.currentTimeMillis();
        try {
            Log.i(TAG, String.format(Locale.ROOT, "query=%s results=%d time=%dms topResult=%s",
                    query, finalMedicines.size(), (endTime - startTime),
                    finalMedicines.isEmpty() ? "NONE" : finalMedicines.get(0).getName()));
        } catch (RuntimeException e) {
            // Fallback for unit tests where Log is not mocked
            System.out.println(String.format(Locale.ROOT, "query=%s results=%d time=%dms topResult=%s",
                    query, finalMedicines.size(), (endTime - startTime),
                    finalMedicines.isEmpty() ? "NONE" : finalMedicines.get(0).getName()));
        }

        return finalMedicines;
    }

    private static int calculateScore(Medicine medicine, String query, String alphaQuery) {
        String name = medicine.getName().toLowerCase(Locale.ROOT);
        String chemical = medicine.getChemicalName() != null ? medicine.getChemicalName().toLowerCase(Locale.ROOT) : "";
        String company = medicine.getCompany() != null ? medicine.getCompany().toLowerCase(Locale.ROOT) : "";
        String alphaName = name.replaceAll("[^a-z0-9]", "");

        // 1. Exact Match (Highest)
        if (name.equals(query) || alphaName.equals(alphaQuery)) return 100;

        // 2. Prefix Match
        if (name.startsWith(query) || alphaName.startsWith(alphaQuery)) return 80;

        // 3. Name Contains
        if (name.contains(query) || alphaName.contains(alphaQuery)) return 60;

        // 4. Chemical or Company Match
        if (chemical.contains(query) || company.contains(query)) return 40;

        // 5. Keyword Match
        if (medicine.getSearchKeywords() != null) {
            for (String kw : medicine.getSearchKeywords()) {
                if (kw.toLowerCase(Locale.ROOT).contains(query)) return 30;
            }
        }

        // 6. Fuzzy Match (Levenshtein)
        if (query.length() >= 4) {
            int distance = levenshteinDistance(alphaName, alphaQuery);
            // Allow 1 mistake for 4-6 chars, 2 for 7+
            int maxAllowed = (query.length() > 6) ? 2 : 1;
            if (distance <= maxAllowed) return 20;
        }

        return 0;
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

    private static class SearchResult {
        final Medicine medicine;
        final int score;

        SearchResult(Medicine medicine, int score) {
            this.medicine = medicine;
            this.score = score;
        }
    }
}
