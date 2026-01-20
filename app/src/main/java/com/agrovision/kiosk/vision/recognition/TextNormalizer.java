package com.agrovision.kiosk.vision.recognition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TextNormalizer {

    private TextNormalizer() {}

    public static String normalize(String cleanedText) {

        if (cleanedText == null || cleanedText.isEmpty()) {
            return "";
        }

        String[] rawTokens = cleanedText.split(" ");

        List<String> normalizedTokens = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        StringBuilder charMergeBuffer = new StringBuilder();

        for (String token : rawTokens) {

            if (token.isEmpty()) continue;

            // Merge isolated letters only (not digits)
            if (token.length() == 1 && Character.isLetter(token.charAt(0))) {
                charMergeBuffer.append(token);
                continue;
            }

            flushMergedBuffer(charMergeBuffer, normalizedTokens, seen);
            processToken(token, normalizedTokens, seen);
        }

        flushMergedBuffer(charMergeBuffer, normalizedTokens, seen);

        return String.join(" ", normalizedTokens);
    }

    /* =========================================================
       SHARED TOKEN PIPELINE (FIXED)
       ========================================================= */

    private static void processToken(String token,
                                     List<String> output,
                                     Set<String> seen) {

        String normalized = normalizeFormulation(token);

        // 🔒 FIX: Drop only single-letter tokens, keep digits
        if (normalized.length() == 1 && Character.isLetter(normalized.charAt(0))) {
            return;
        }

        if (seen.add(normalized)) {
            output.add(normalized);
        }
    }

    private static void flushMergedBuffer(StringBuilder buffer,
                                          List<String> output,
                                          Set<String> seen) {

        if (buffer.length() == 0) return;

        processToken(buffer.toString(), output, seen);
        buffer.setLength(0);
    }

    private static String normalizeFormulation(String token) {

        switch (token) {
            case "sl":
                return "sl";
            case "wg":
                return "wg";
            case "ec":
                return "ec";
            case "wp":
                return "wp";
            default:
                return token;
        }
    }
}
