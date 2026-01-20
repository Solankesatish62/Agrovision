package com.agrovision.kiosk.analytics.events;

/**
 * MvpEvent
 *
 * Represents high-level, business-relevant observations
 * emitted by the system brain (StateMachine).
 *
 * RULES:
 * - No logic
 * - No Android dependencies
 * - Emitted ONLY by StateMachine
 * - Events explain WHY outcomes occurred
 *
 * THIS ENUM IS AN ANALYTICS CONTRACT.
 * Do not rename or delete values casually.
 */
public enum MvpEvent {

    /* =========================================================
       CAMERA / INPUT QUALITY
       ========================================================= */

    /**
     * Camera failed to deliver a usable frame.
     */
    FRAME_CAPTURE_FAILED,

    /**
     * Object placement was unstable or unclear,
     * preventing reliable detection.
     */
    MEDICINE_PLACEMENT_UNCLEAR,

    /**
     * Lighting conditions were insufficient for
     * reliable detection or recognition.
     */
    LOW_LIGHT_DETECTED,

    /* =========================================================
       DETECTION / RECOGNITION
       ========================================================= */

    /**
     * No readable text was extracted during OCR.
     */
    OCR_TEXT_EMPTY,

    /**
     * Recognition confidence did not meet
     * the acceptable threshold.
     */
    MATCH_CONFIDENCE_LOW,

    /**
     * Recognition completed successfully
     * with acceptable confidence.
     */
    MATCH_SUCCESS,

    /* =========================================================
       SESSION / FLOW
       ========================================================= */

    /**
     * Scan session was aborted before
     * natural completion.
     */
    SESSION_ABORTED,

    /**
     * Automatic explanation flow was interrupted
     * before completion.
     */
    EXPLANATION_SKIPPED_EARLY,

    /**
     * Automatic explanation flow completed
     * successfully.
     */
    EXPLANATION_COMPLETED,

    /* =========================================================
       USER / ENVIRONMENT SIGNALS
       ========================================================= */

    /**
     * User attention shifted away during
     * the explanation flow.
     */
    FARMER_LOOKED_AWAY,

    /**
     * Shopkeeper intervened during
     * system-driven flow.
     */
    SHOPKEEPER_INTERRUPTED,

    /* =========================================================
       IDLE / SYSTEM HEALTH
       ========================================================= */

    /**
     * System entered idle mode due to
     * prolonged inactivity.
     */
    IDLE_FORCED,

    /**
     * System recovered after a fatal error.
     */
    SYSTEM_RECOVERED
}
