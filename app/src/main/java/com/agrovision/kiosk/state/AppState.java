package com.agrovision.kiosk.state;
// Defines the package containing the finite state model of the kiosk

/**
 * AppState represents the complete and final list of
 * user-visible and system-relevant states in the AgroVision kiosk.
 *
 * IMPORTANT:
 * - This list is FINAL & LOCKED
 * - No new states should be added without product-level approval
 * - These states reflect REAL user and shop-floor behavior
 */
public enum AppState {

    READY,
    // System is active and waiting for a medicine
    // Camera ON, ML ready
    // Home screen visible
    // Entry point for all scans

    SCANNING,
    // Stable object detected
    // Vision + ML pipeline running
    // Screen shows "Scanning..."
    // Must be interruptible (object removed / pause)

    RESULT_AUTO,
    // Medicine identified automatically
    // Result is shown with auto-play explanation
    // Auto timer active

    RESULT_MANUAL_NAV,
    // User/shopkeeper is manually navigating results
    // Auto timer disabled
    // User-driven exploration

    RESULT_PAUSED,
    // Result screen is frozen
    // Used when explanation is paused or discussion is happening
    // No timers running

    UNKNOWN_NOTE,
    // System could not confidently identify medicine
    // UI shows "Not sure" / fallback guidance
    // Prevents wrong recommendations

    IDLE
    // No activity for a long time
    // Attract/demo content playing
    // Any interaction returns system to READY
}
