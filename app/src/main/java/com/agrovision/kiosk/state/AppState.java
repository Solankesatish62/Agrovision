package com.agrovision.kiosk.state;

/**
 * AppState represents the complete list of system states.
 */
public enum AppState {
    READY,              // waiting for medicine
    SCANNING,           // YOLO/OCR running
    RESULT_AUTO,        // Known medicine shown
    RESULT_MANUAL_NAV,  // Manual navigation
    RESULT_PAUSED,      // Frozen screen
    RESULT_UNKNOWN,     // 🚀 Medicine not recognized
    IDLE                // Inactive
}
