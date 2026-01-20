package com.agrovision.kiosk.state;

/**
 * StateObserver is implemented by components
 * that need to react to state changes.
 *
 * IMPORTANT:
 * - Observers must NEVER change state
 * - Observers must NEVER call transition()
 * - Observers must be lightweight and non-blocking
 */
public interface StateObserver {

    /**
     * Called whenever the application state changes.
     *
     * @param newState The new AppState decided by StateMachine
     */
    void onStateChanged(AppState newState);
}
