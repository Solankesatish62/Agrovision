package com.agrovision.kiosk.camera;

/**
 * ScanMode
 *
 * Defines the scanning mode boundary for vision analysis.
 * - CENTER_ROI: Bypasses YOLO object detection entirely and processes the central region of interest.
 * - LEGACY_YOLO: Uses YOLO object detection and stability tracking.
 */
public enum ScanMode {
    CENTER_ROI,
    LEGACY_YOLO
}
