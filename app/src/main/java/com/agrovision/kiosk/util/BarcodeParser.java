package com.agrovision.kiosk.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BarcodeParser
 *
 * Responsibility: Extract clean UIDs or URLs from noisy barcode scan strings.
 */
public final class BarcodeParser {

    private BarcodeParser() {}

    /**
     * Extracts a clean identity or URL from raw barcode input.
     */
    public static String getCleanIdentity(String raw) {
        if (raw == null || raw.isEmpty()) return "";

        // 1. WhatsApp UID Case (wa.me)
        if (raw.contains("wa.me") && raw.contains("UID_")) {
            Pattern pattern = Pattern.compile("UID_([^&\\s-]+)");
            Matcher matcher = pattern.matcher(raw);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        // 2. Scrapable URL Case (TrustTags / IVCS)
        if (raw.contains("ttags.in") || raw.contains("ivcs.ai")) {
            // Keep full URL for scraping
            return raw.trim();
        }

        // 3. Raw Text Case (UID GS1...)
        String clean = raw.replace("UID", "").trim();
        // Remove spaces if it's a code block
        if (clean.contains(" ") && clean.length() < 30) {
            clean = clean.split(" ")[0];
        }
        
        return clean;
    }

    /**
     * Extracts the prefix (first 5-6 chars) for local matching.
     */
    public static String getPrefix(String cleanId) {
        if (cleanId == null || cleanId.length() < 5) return cleanId;
        
        // If it's a URL, don't return a prefix (URLs need scraping)
        if (cleanId.startsWith("http")) return "";

        return cleanId.substring(0, Math.min(cleanId.length(), 6));
    }
}
