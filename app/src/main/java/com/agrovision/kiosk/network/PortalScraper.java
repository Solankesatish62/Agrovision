package com.agrovision.kiosk.network;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PortalScraper
 *
 * Responsibility: Fetch product names from manufacturer portals (ttags.in, ivcs.ai).
 */
public final class PortalScraper {

    private static final String TAG = "PortalScraper";

    public interface Callback {
        void onResult(String productName);
        void onError(Exception e);
    }

    private PortalScraper() {}

    public static void fetchProductName(String portalUrl, Callback callback) {
        new Thread(() -> {
            try {
                String html = downloadHtml(portalUrl);
                String name = extractProductName(html);
                callback.onResult(name);
            } catch (Exception e) {
                Log.e(TAG, "Scraping failed", e);
                callback.onError(e);
            }
        }).start();
    }

    private static String downloadHtml(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        } finally {
            conn.disconnect();
        }
        return result.toString();
    }

    private static String extractProductName(String html) {
        if (html == null || html.isEmpty()) return "";

        // Strategy 1: Targeted Industrial IDs (From your BASF/eCubix example)
        // Matches: id="product_name">NAME</td> or id="productName">NAME</span>
        Pattern idPattern = Pattern.compile("id=[\"']product_?name[\"'][^>]*>(.*?)<", Pattern.CASE_INSENSITIVE);
        Matcher matcher = idPattern.matcher(html);
        if (matcher.find()) {
            String name = matcher.group(1).replaceAll("<[^>]*>", "").trim();
            if (!isGeneric(name)) return name;
        }

        // Strategy 2: Look for labels followed by values in tables
        // Matches: <td>Product Name:</td><td>NAME</td>
        Pattern labelPattern = Pattern.compile("Product\\s*Name[:\\s]*</td>\\s*<td[^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE);
        matcher = labelPattern.matcher(html);
        if (matcher.find()) {
            String name = matcher.group(1).replaceAll("<[^>]*>", "").trim();
            if (!isGeneric(name)) return name;
        }

        // Strategy 3: Look for <title>
        Pattern titlePattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE);
        matcher = titlePattern.matcher(html);
        if (matcher.find()) {
            String title = matcher.group(1).trim();
            // Clean up titles like "Authentication - Slayer Pro"
            if (title.contains("-")) {
                String[] parts = title.split("-");
                title = parts[parts.length - 1].trim();
            }
            if (!isGeneric(title)) return title;
        }

        // Strategy 4: Look for <h1> or <h2> tags
        Pattern hTagPattern = Pattern.compile("<h[12][^>]*>(.*?)</h[12]>", Pattern.CASE_INSENSITIVE);
        matcher = hTagPattern.matcher(html);
        while (matcher.find()) {
            String hText = matcher.group(1).replaceAll("<[^>]*>", "").trim();
            if (!isGeneric(hText) && hText.length() > 3) {
                return hText;
            }
        }

        return "";
    }

    private static boolean isGeneric(String text) {
        String lower = text.toLowerCase();
        return lower.contains("authenticat") || lower.contains("verify") || 
               lower.contains("welcome") || lower.contains("track") ||
               lower.contains("trace") || lower.contains("trusttags") ||
               lower.contains("productinfo") || lower.contains("genuin") ||
               lower.contains("system") || lower.contains("check");
    }
}
