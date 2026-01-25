package com.agrovision.kiosk.ui.result.model;

/**
 * ResultInfoItem
 *
 * Represents a single piece of medical guidance.
 * Types correspond to specific colors and icons from the design mockup.
 */
public final class ResultInfoItem {

    public enum Type {
        CROP,      // Green (Leaf)
        PEST,      // Blue (Bug)
        USAGE,     // Light Green (Beaker)
        TIMING,    // Orange (Calendar)
        DOSAGE,    // Grey (Sprayer/Dosage)
        CAUTION    // Mint/Red (Alert)
    }

    public final Type type;
    public final String text;

    public ResultInfoItem(Type type, String text) {
        this.type = type;
        this.text = text;
    }
}
