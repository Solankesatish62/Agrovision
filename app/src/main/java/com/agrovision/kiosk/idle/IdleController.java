package com.agrovision.kiosk.idle;

import android.content.Context;

import com.agrovision.kiosk.state.AppState;
import com.agrovision.kiosk.state.StateEvent;
import com.agrovision.kiosk.state.StateMachine;

/**
 * IdleController
 *
 * PURPOSE:
 * - Coordinates idle behavior using IdleTimer
 * - Translates inactivity into StateMachine transitions
 *
 * DESIGN:
 * - Poll-based (no threads, no handlers)
 * - Deadlock-safe
 * - StateMachine is the single authority
 */
public final class IdleController {

    private final IdleTimer idleTimer;
    private final StateMachine stateMachine;

    /**
     * @param context Application context
     * @param idleTimeoutMs Idle timeout duration
     */
    public IdleController(Context context, long idleTimeoutMs) {
        this.idleTimer = new IdleTimer(idleTimeoutMs);
        this.stateMachine = StateMachine.getInstance(context.getApplicationContext());
    }

    /* =========================================================
       ACTIVITY SIGNALING
       ========================================================= */

    /**
     * Must be called on ANY user or system activity.
     *
     * Safe to call from any thread.
     */
    public void onActivity() {
        idleTimer.reset();

        // If we were idle, wake the system
        AppState current = stateMachine.getCurrentState();
        if (current == AppState.IDLE) {
            stateMachine.transition(StateEvent.ACTIVITY_DETECTED);
        }
    }

    /* =========================================================
       POLLING
       ========================================================= */

    /**
     * Polling method.
     *
     * MUST be called periodically from a safe loop
     * (e.g., camera frame loop or main tick).
     *
     * No timers. No background threads.
     */
    public void tick() {

        // Check idle condition first
        if (!idleTimer.isIdle()) {
            return;
        }

        // Avoid redundant transitions
        AppState current = stateMachine.getCurrentState();
        if (current != AppState.IDLE) {
            stateMachine.transition(StateEvent.IDLE_TIMEOUT);
        }
    }

    /* =========================================================
       VISIBILITY (OPTIONAL, DEBUG)
       ========================================================= */

    public long getIdleDurationMs() {
        return idleTimer.getIdleDurationMs();
    }

    public long getIdleTimeoutMs() {
        return idleTimer.getIdleTimeoutMs();
    }
}
