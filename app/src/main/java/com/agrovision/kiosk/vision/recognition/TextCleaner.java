package com.agrovision.kiosk.vision.recognition;

import java.text.Normalizer;
import java.util.Locale;

/**
 * TextCleaner
 *
 * PURPOSE:
 * - Sanitize raw OCR text into a stable, searchable form
 *
 * INPUT:
 * - Noisy OCR output (mixed case, symbols, line breaks)
 *
 * OUTPUT:
 * - Cleaned lowercase ASCII text with normalized spacing
 *
 * HARD RULES:
 * - Deterministic
 * - No guessing
 * - No Android dependencies
 * - Safe for repeated calls
 */
public final class TextCleaner {

    // Prevent instantiation
    private TextCleaner() {}

    /**
     * Cleans raw OCR text.
     *
     * @param rawText OCR output (may be null or empty)
     * @return cleaned text (never null)
     */
    public static String clean(String rawText) {

        // Null-safe
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }

        String text = rawText;

        // 1️⃣ Normalize Unicode (é → e, etc.)
        text = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        // 2️⃣ Convert to lowercase (locale-safe)
        text = text.toLowerCase(Locale.ROOT);

        // 3️⃣ Replace all non-alphanumeric characters with space
        // Keeps numbers (important for concentrations like 20%, 50wg)
        text = text.replaceAll("[^a-z0-9]", " ");

        // 4️⃣ Collapse multiple spaces into one
        text = text.replaceAll("\\s+", " ");

        // 5️⃣ Trim leading & trailing spaces
        return text.trim();
    }
}
