//package com.agrovision.kiosk.session;
//// Declares the package; groups all scan session–related lifecycle classes together
//
//import androidx.annotation.NonNull;
//// Ensures certain fields/methods never return or accept null values (compile-time + lint safety)
//
//import java.util.UUID;
//// Used to generate a globally unique identifier for each scan session
//
///**
// * ScanSession represents ONE scan lifecycle.
// *
// * This class is intentionally simple and deterministic.
// * It is owned and synchronized externally by SessionManager.
// *
// * DESIGN INTENT:
// * - One object = one scan
// * - Explicit lifecycle via enum
// * - Idempotent termination
// * - No Android dependencies
// */
//public final class ScanSession {
//    // Final prevents inheritance, which avoids accidental lifecycle extension bugs
//
//    /**
//     * Explicit lifecycle states for a scan.
//     * Replaces implicit "endTime == 0" logic.
//     */
//    public enum ScanStatus {
//        ACTIVE,        // Scan is currently running
//        COMPLETED,     // Scan finished normally (result shown)
//        CANCELLED      // Scan aborted (object removed / new scan started)
//    }
//
//    @NonNull
//    private final String sessionId;
//    // Unique identifier for this session; immutable once created
//
//    private final long startTimeMs;
//    // Wall-clock timestamp (milliseconds) when the session started
//
//    private long endTimeMs;
//    // Wall-clock timestamp when the session ended; 0 means "not ended yet"
//
//    @NonNull
//    private ScanStatus status;
//    // Current lifecycle status of the scan session
//
//    /**
//     * Constructs a new scan session.
//     * Called ONLY by SessionManager when a scan begins.
//     */
//    public ScanSession() {
//        // Constructor defines the birth of a scan session
//
//        this.sessionId = UUID.randomUUID().toString();
//        // Generates a unique ID to prevent ghost callbacks and race conditions
//
//        this.startTimeMs = System.currentTimeMillis();
//        // Captures the exact moment the scan started (for analytics and debugging)
//
//        this.endTimeMs = 0;
//        // Initializes end time to zero, meaning "session is active"
//
//        this.status = ScanStatus.ACTIVE;
//        // New sessions always start in ACTIVE state
//    }
//
//    /**
//     * Marks the session as completed normally.
//     * This method is idempotent.
//     *
//     * @return true if this call ended the session, false if it was already terminated
//     */
//    public synchronized boolean complete() {
//        // Synchronized ensures thread safety if multiple callbacks attempt to end the session
//
//        if (isTerminated()) {
//            // If session is already completed or cancelled, do nothing
//            return false;
//            // Prevents double-ending and inconsistent timestamps
//        }
//
//        endTimeMs = System.currentTimeMillis();
//        // Records the exact completion time
//
//        status = ScanStatus.COMPLETED;
//        // Explicitly marks session as successfully completed
//
//        return true;
//        // Indicates this call successfully ended the session
//    }
//
//    /**
//     * Cancels the session.
//     * Used when user removes object or a new scan interrupts this one.
//     *
//     * @return true if this call cancelled the session, false if already terminated
//     */
//    public synchronized boolean cancel() {
//        // Synchronized ensures atomic lifecycle transition
//
//        if (isTerminated()) {
//            // If already completed or cancelled, ignore
//            return false;
//            // Prevents overriding a terminal state
//        }
//
//        endTimeMs = System.currentTimeMillis();
//        // Records cancellation time
//
//        status = ScanStatus.CANCELLED;
//        // Marks the session as cancelled
//
//        return true;
//        // Indicates successful cancellation
//    }
//
//    /**
//     * Checks whether the session is currently active.
//     *
//     * @return true if scan is still running
//     */
//    public boolean isActive() {
//        // No synchronization needed; status transitions are monotonic
//        return status == ScanStatus.ACTIVE;
//        // ACTIVE is the only non-terminal state
//    }
//
//    /**
//     * Checks whether the session has reached a terminal state.
//     *
//     * @return true if session is completed or cancelled
//     */
//    public boolean isTerminated() {
//        // Any state other than ACTIVE means the session is over
//        return status != ScanStatus.ACTIVE;
//    }
//
//    /**
//     * Returns the current lifecycle status.
//     */
//    @NonNull
//    public ScanStatus getStatus() {
//        // Simple accessor for explicit lifecycle state
//        return status;
//    }
//
//    /**
//     * Returns the unique session ID.
//     */
//    @NonNull
//    public String getSessionId() {
//        // Exposes immutable session identifier
//        return sessionId;
//    }
//
//    /**
//     * Returns the session start time in milliseconds.
//     */
//    public long getStartTimeMs() {
//        // Used for analytics, logging, and debugging
//        return startTimeMs;
//    }
//
//    /**
//     * Returns the session end time in milliseconds.
//     *
//     * @return 0 if session has not ended yet
//     */
//    public long getEndTimeMs() {
//        // Returns end time or 0 for active sessions
//        return endTimeMs;
//    }
//
//    /**
//     * Calculates session duration.
//     *
//     * @return duration in milliseconds
//     */
//    public long getDurationMs() {
//        // Determine end reference: now if active, endTime if terminated
//        long end = (endTimeMs == 0)
//                ? System.currentTimeMillis()
//                : endTimeMs;
//
//        // Ensure non-negative duration even if clock shifts slightly
//        return Math.max(0, end - startTimeMs);
//    }
//
//    /**
//     * Human-readable representation for logs and debugging.
//     */
//    @Override
//    public String toString() {
//        // Overrides Object.toString() for meaningful log output
//
//        return "ScanSession{" +
//                "id=" + sessionId.substring(0, 8) +
//                // Shortened ID for log readability
//
//                ", status=" + status +
//                // Current lifecycle state
//
//                ", durationMs=" + getDurationMs() +
//                // How long the scan ran
//
//                '}';
//        // End of string representation
//    }
//}
package com.agrovision.kiosk.session;

import com.agrovision.kiosk.data.model.Medicine;
import com.agrovision.kiosk.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ScanSession
 *
 * Thread-safe definition of ONE logical scan lifecycle.
 *
 * GUARANTEES:
 * - No ConcurrentModificationException
 * - No race conditions
 * - No duplicate medicines
 * - No infinite accumulation
 */
public final class ScanSession {

    /* =========================================================
       CONSTANTS
       ========================================================= */

    private static final int MAX_MEDICINES = 4;

    /* =========================================================
       SESSION IDENTITY
       ========================================================= */

    private final String sessionId;
    private final long createdAtMs;

    /* =========================================================
       MUTABLE STATE (GUARDED BY THIS)
       ========================================================= */

    private final List<Medicine> medicines = new ArrayList<>();
    private boolean active = true;

    /* =========================================================
       CONSTRUCTOR
       ========================================================= */

    public ScanSession() {
        this.sessionId = UUID.randomUUID().toString();
        this.createdAtMs = TimeUtils.nowMs();
    }

    /* =========================================================
       IDENTITY & TIME
       ========================================================= */

    public String getSessionId() {
        return sessionId;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    /* =========================================================
       SESSION LIFECYCLE
       ========================================================= */

    /**
     * Ends this session.
     * After this, no mutations are allowed.
     */
    public synchronized void end() {
        active = false;
    }

    public synchronized boolean isActive() {
        return active;
    }

    /* =========================================================
       MEDICINE COLLECTION (THREAD-SAFE)
       ========================================================= */

    /**
     * Attempts to add a medicine to this session.
     *
     * @return true if added, false if rejected
     */
    public synchronized boolean addMedicine(Medicine medicine) {
        if (!active) return false;
        if (medicine == null) return false;

        // Prevent duplicates
        if (medicines.contains(medicine)) {
            return false;
        }

        // Enforce hard cap
        if (medicines.size() >= MAX_MEDICINES) {
            return false;
        }

        medicines.add(medicine);
        return true;
    }

    /**
     * Returns a SNAPSHOT copy of medicines.
     *
     * IMPORTANT:
     * - Defensive copy prevents ConcurrentModificationException
     * - Caller cannot mutate internal state
     */
    public synchronized List<Medicine> getMedicines() {
        return new ArrayList<>(medicines);
    }

    public synchronized int getMedicineCount() {
        return medicines.size();
    }

    public synchronized boolean isFull() {
        return medicines.size() >= MAX_MEDICINES;
    }

    /* =========================================================
       DEBUG
       ========================================================= */

    @Override
    public synchronized String toString() {
        return "ScanSession{" +
                "sessionId='" + sessionId + '\'' +
                ", createdAtMs=" + createdAtMs +
                ", medicines=" + medicines.size() +
                ", active=" + active +
                '}';
    }
}

