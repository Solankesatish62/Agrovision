package com.agrovision.kiosk.camera;

import android.util.Size;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;

/**
 * CameraConfig
 *
 * Immutable configuration holder for Camera behavior.
 *
 * PURPOSE:
 * - Centralize all camera-related constants
 * - Ensure predictable performance for ML pipelines
 *
 * RULES:
 * - No CameraX binding logic
 * - No Android lifecycle
 * - No mutation after creation
 */
public final class CameraConfig {

    /* =========================================================
       CAMERA SELECTION
       ========================================================= */

    // Back camera is mandatory for medicine scanning
    public static final int LENS_FACING = CameraSelector.LENS_FACING_BACK;

    /* =========================================================
       RESOLUTION & FORMAT
       ========================================================= */

    /**
     * Fixed analysis resolution.
     *
     * WHY:
     * - YOLO prefers consistent input size
     * - Reduces device-specific variance
     * - Avoids runtime scaling surprises
     */
    public static final Size ANALYSIS_RESOLUTION = new Size(1280, 720);

    /**
     * Image format for analysis.
     *
     * YUV is:
     * - Faster
     * - Native to CameraX
     * - ML-friendly
     */
    public static final int IMAGE_FORMAT =
            ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888;

    /* =========================================================
       FRAME RATE CONTROL
       ========================================================= */

    /**
     * Target FPS for analysis.
     *
     * IMPORTANT:
     * - Camera may produce more frames
     * - FrameAnalyzer must DROP extras
     */
    public static final int TARGET_FPS = 15;

    /* =========================================================
       BACKPRESSURE STRATEGY
       ========================================================= */

    /**
     * Keep only the latest frame.
     *
     * WHY:
     * - Prevents frame queue buildup
     * - Avoids "time travel" latency
     */
    public static final int BACKPRESSURE_STRATEGY =
            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST;

    /* =========================================================
       CAPTURE CONFIG (OPTIONAL)
       ========================================================= */

    /**
     * ImageCapture mode.
     *
     * Even if unused initially, defining it here
     * avoids future fragmentation.
     */
    public static final int CAPTURE_MODE =
            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY;

    /* =========================================================
       FLASH / TORCH
       ========================================================= */

    /**
     * Torch usage.
     *
     * FALSE by default:
     * - Shops prefer ambient light
     * - Torch drains battery & heats sensor
     */
    public static final boolean TORCH_ENABLED_BY_DEFAULT = false;

    /* =========================================================
       ROTATION
       ========================================================= */

    /**
     * Whether to auto-handle rotation.
     *
     * CameraController will decide final orientation.
     */
    public static final boolean HANDLE_ROTATION = true;

    /* =========================================================
       PRIVATE CONSTRUCTOR
       ========================================================= */

    private CameraConfig() {
        // Prevent instantiation
    }
}
