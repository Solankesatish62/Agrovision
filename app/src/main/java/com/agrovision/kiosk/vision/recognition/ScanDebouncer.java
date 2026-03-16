package com.agrovision.kiosk.vision.recognition;

import androidx.annotation.Nullable;
import java.util.Objects;

/**
 * ScanDebouncer
 *
 * PURPOSE:
 * - Prevent "spam" of the same scan result in real shop conditions.
 * - Ensures a cooldown period before the same medicine can be scanned again.
 */
public final class ScanDebouncer {

    private static final long DEFAULT_COOLDOWN_MS = 5000; // 5 seconds

    private String lastText;
    private long lastTimestamp;
    private final long cooldownMs;

    public ScanDebouncer() {
        this(DEFAULT_COOLDOWN_MS);
    }

    public ScanDebouncer(long cooldownMs) {
        this.cooldownMs = cooldownMs;
    }

    /**
     * Checks if the new scan result should be processed.
     *
     * @param newText The normalized text from OCR.
     * @return true if this is a NEW result or the cooldown has passed.
     */
    public synchronized boolean shouldProcess(@Nullable String newText) {
        if (newText == null || newText.isEmpty()) {
            return false;
        }

        long now = System.currentTimeMillis();

        // If same text AND within cooldown window -> Ignore
        if (Objects.equals(newText, lastText) && (now - lastTimestamp) < cooldownMs) {
            return false;
        }

        // Update state
        lastText = newText;
        lastTimestamp = now;
        return true;
    }

    public synchronized void reset() {
        lastText = null;
        lastTimestamp = 0;
    }
}
