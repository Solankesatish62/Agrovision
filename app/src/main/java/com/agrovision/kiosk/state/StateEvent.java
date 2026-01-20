package com.agrovision.kiosk.state;

/**
 * StateEvent represents high-level semantic events
 * that can cause state transitions.
 *
 * RULES:
 * - No low-level camera / OCR / ML signals here
 * - Events describe WHAT happened, not HOW
 * - This enum must stay aligned with StateRules
 */
public enum StateEvent {

    // ---------- Object lifecycle ----------
    OBJECT_DETECTED,        // Stable object placed on counter
    OBJECT_REMOVED,         // Counter becomes empty
    NEW_SCAN_REQUESTED,     // New bottle placed or explicit rescan

    // ---------- Scan result ----------
    MATCH_FOUND,            // Confident auto match
    MATCH_NOT_FOUND,        // No confident match
    MANUAL_SELECTION,       // User manually selects medicine

    // ---------- Result flow ----------
    RESULT_TIMEOUT,         // Auto-result time expired
    PAUSE_REQUESTED,        // Pause current result
    RESUME_REQUESTED,       // Resume from pause

    // ---------- Idle ----------
    IDLE_TIMEOUT,           // No activity for configured duration
    ACTIVITY_DETECTED       // Any interaction while idle
}
