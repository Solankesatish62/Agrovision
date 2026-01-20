package com.agrovision.kiosk.session;

import android.content.Context;

import com.agrovision.kiosk.data.holder.CurrentScanHolder;
import com.agrovision.kiosk.data.model.Medicine;
import com.agrovision.kiosk.state.StateEvent;
import com.agrovision.kiosk.state.StateMachine;
import com.agrovision.kiosk.util.LogUtils;

/**
 * SessionManager
 *
 * SINGLE AUTHORITY for scan session lifecycle.
 *
 * RESPONSIBILITIES:
 * - Create exactly one active ScanSession at a time
 * - Enforce timeouts via SessionTimer
 * - Prevent infinite recognition loops
 * - Coordinate with StateMachine safely (no deadlocks)
 * - Clear transient memory on session end
 *
 * HARD GUARANTEES:
 * - Deadlock-safe (no external calls while holding locks)
 * - Init-order safe (survives missing/delayed init())
 * - Type-safe (explicit Medicine dependency)
 * - Deterministic behavior
 */
public final class SessionManager {

    /* =========================================================
       SINGLETON
       ========================================================= */

    private static volatile SessionManager INSTANCE;

    public static SessionManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SessionManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SessionManager(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    /* =========================================================
       DEPENDENCIES (FINAL, SAFE)
       ========================================================= */

    private final StateMachine stateMachine;
    private final CurrentScanHolder scanHolder;

    /* =========================================================
       CONFIGURATION (SAFE DEFAULTS)
       ========================================================= */

    // Safe defaults to prevent startup crashes
    // These may be overridden via init()
    private long maxSessionDurationMs = 60_000L; // 60 seconds
    private long idleTimeoutMs        = 15_000L; // 15 seconds

    /* =========================================================
       SESSION STATE (GUARDED BY THIS)
       ========================================================= */

    private ScanSession currentSession;
    private SessionTimer sessionTimer;

    /* =========================================================
       CONSTRUCTOR
       ========================================================= */

    private SessionManager(Context appContext) {
        this.stateMachine = StateMachine.getInstance(appContext);
        this.scanHolder = CurrentScanHolder.getInstance();
    }

    /* =========================================================
       ONE-TIME CONFIGURATION
       ========================================================= */

    /**
     * Optional initialization hook.
     *
     * Safe to call multiple times.
     * Invalid values are ignored to preserve kiosk stability.
     */
    public synchronized void init(long maxSessionDurationMs,
                                  long idleTimeoutMs) {

        if (maxSessionDurationMs <= 0 || idleTimeoutMs <= 0) {
            LogUtils.w("SessionManager init received invalid values. Using defaults.");
            return;
        }

        this.maxSessionDurationMs = maxSessionDurationMs;
        this.idleTimeoutMs = idleTimeoutMs;
    }

    /* =========================================================
       SESSION LIFECYCLE
       ========================================================= */

    /**
     * Starts a new scan session.
     *
     * Any existing session is forcefully terminated.
     */
    public void startNewSession() {

        StateEvent transitionEvent;

        synchronized (this) {
            forceEndSessionLocked("Starting new session");

            currentSession = new ScanSession();
            sessionTimer = new SessionTimer(
                    maxSessionDurationMs,
                    idleTimeoutMs
            );

            LogUtils.i("ScanSession started: " + currentSession.getSessionId());
            transitionEvent = StateEvent.NEW_SCAN_REQUESTED;
        }

        // 🔓 External call AFTER releasing lock (deadlock-safe)
        stateMachine.transition(transitionEvent);
    }

    /**
     * Attempts to add a medicine to the current session.
     *
     * @return true if accepted, false otherwise
     */
    public boolean addMedicineToSession(Medicine medicine) {
        synchronized (this) {
            if (currentSession == null || !currentSession.isActive()) {
                return false;
            }

            boolean added = currentSession.addMedicine(medicine);
            if (added && sessionTimer != null) {
                sessionTimer.markActivity();
            }
            return added;
        }
    }

    /**
     * Polling method.
     *
     * MUST be called periodically (e.g., camera frame loop).
     * NEVER scheduled with timers or background threads.
     */
    public void tick() {

        StateEvent transitionEvent = null;

        synchronized (this) {
            if (currentSession == null || sessionTimer == null) {
                return;
            }

            if (sessionTimer.isSessionExpired()) {
                LogUtils.w("ScanSession expired");
                forceEndSessionLocked("Session expired");
                transitionEvent = StateEvent.IDLE_TIMEOUT;


            } else if (sessionTimer.isIdleTimedOut()) {
                LogUtils.w("ScanSession idle timeout");
                forceEndSessionLocked("Idle timeout");
                transitionEvent = StateEvent.IDLE_TIMEOUT;
            }
        }

        // 🔓 External call AFTER lock release
        if (transitionEvent != null) {
            stateMachine.transition(transitionEvent);
        }
    }

    /**
     * Completes the current session normally.
     */
    public void completeSession() {

        StateEvent transitionEvent;

        synchronized (this) {
            if (currentSession == null) return;

            currentSession.end();

            if (currentSession.getMedicineCount() > 0) {
                transitionEvent = StateEvent.MATCH_FOUND;
            } else {
                transitionEvent = StateEvent.MATCH_NOT_FOUND;
            }

            cleanupLocked();
        }

        stateMachine.transition(transitionEvent);

    }

    /* =========================================================
       INTERNAL CLEANUP (LOCK REQUIRED)
       ========================================================= */

    private void forceEndSessionLocked(String reason) {
        if (currentSession == null) return;

        LogUtils.w("Force ending session: " + reason);
        currentSession.end();
        cleanupLocked();
    }

    private void cleanupLocked() {
        currentSession = null;
        sessionTimer = null;

        // Always clear transient scan memory
        scanHolder.clear();
    }

    /* =========================================================
       STATE QUERIES (READ-ONLY)
       ========================================================= */

    public synchronized boolean hasActiveSession() {
        return currentSession != null && currentSession.isActive();
    }

    public synchronized ScanSession getCurrentSession() {
        return currentSession;
    }
}
