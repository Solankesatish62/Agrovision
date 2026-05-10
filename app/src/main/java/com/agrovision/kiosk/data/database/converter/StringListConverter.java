package com.agrovision.kiosk.data.database.converter;

import androidx.room.TypeConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * StringListConverter
 *
 * Converts List<String> ↔ CSV String for Room persistence.
 *
 * WHY THIS EXISTS:
 * - Room cannot store List<String> directly
 *
 * RULES:
 * - Must be null-safe
 * - Must never crash on bad input
 * - Must return immutable-friendly structures
 */
public final class StringListConverter {

    private static final String DELIMITER = "\\|";
    private static final String JOINER = "|";

    /**
     * Converts a List<String> into a pipe-separated string.
     *
     * Example:
     * ["Cotton, Rice", "Wheat"] → "Cotton, Rice|Wheat"
     */
    @TypeConverter
    public static String fromList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < list.size(); i++) {
            String item = list.get(i);
            if (item == null) continue;

            builder.append(item);

            // Append delimiter only between elements
            if (i < list.size() - 1) {
                builder.append(JOINER);
            }
        }

        return builder.toString();
    }

    /**
     * Converts a pipe-separated string back into a List<String>.
     *
     * Example:
     * "Cotton, Rice|Wheat" → ["Cotton, Rice", "Wheat"]
     */
    @TypeConverter
    public static List<String> toList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String[] parts = value.split(DELIMITER);
        List<String> list = new ArrayList<>(parts.length);

        for (String part : parts) {
            if (part == null) continue;

            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }

        return list;
    }
}
