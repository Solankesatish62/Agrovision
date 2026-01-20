package com.agrovision.kiosk.util;

/**
 * Global application constants.
 *
 * Categories:
 * 1. ABSOLUTE UNITS — never change (physics / math)
 * 2. DEFAULT FALLBACKS — overridable via KioskConfigLoader
 *
 * Rules:
 * - No logic
 * - No runtime state
 * - No environment flags
 */
public final class Constants {

    private Constants() {
        throw new AssertionError("No instances allowed");
    }

    /* =========================================================
       ABSOLUTE TIME UNITS (PHYSICAL CONSTANTS)
       ========================================================= */
    public static final long ONE_SECOND_MS = 1_000L;
    public static final long ONE_MINUTE_MS = 60 * ONE_SECOND_MS;

    /* =========================================================
       APP
       ========================================================= */
    public static final String APP_NAME = "AgroVision";
    public static final String APP_TAG = "AgroVisionLog";

    /* =========================================================
       TIMEOUTS — DEFAULT FALLBACKS (OVERRIDABLE)
       ========================================================= */
    public static final long DEFAULT_IDLE_TIMEOUT_MS = 90 * ONE_SECOND_MS;
    public static final long DEFAULT_SESSION_TIMEOUT_MS = 30 * ONE_SECOND_MS;
    public static final long DEFAULT_STABILITY_WINDOW_MS = 800L;

    /* =========================================================
       CAMERA — DEFAULT FALLBACKS
       ========================================================= */
    public static final int DEFAULT_CAMERA_TARGET_FPS = 15;
    public static final int DEFAULT_CAMERA_IMAGE_WIDTH = 1280;
    public static final int DEFAULT_CAMERA_IMAGE_HEIGHT = 720;

    public static final float DEFAULT_LOW_LIGHT_THRESHOLD = 40.0f;

    /* =========================================================
       VISION — DEFAULT FALLBACKS
       ========================================================= */
    public static final float DEFAULT_YOLO_CONFIDENCE_THRESHOLD = 0.45f;
    public static final float DEFAULT_MATCH_CONFIDENCE_THRESHOLD = 0.60f;

    public static final int DEFAULT_MAX_DETECTIONS_PER_FRAME = 5;

    /* =========================================================
       SESSION — DEFAULT FALLBACKS
       ========================================================= */
    public static final int DEFAULT_MAX_MEDICINES_PER_SESSION = 4;

    /* =========================================================
       THREADING
       ========================================================= */
    public static final int YOLO_THREAD_PRIORITY = Thread.NORM_PRIORITY + 1;
    public static final int OCR_THREAD_PRIORITY = Thread.NORM_PRIORITY;
}
