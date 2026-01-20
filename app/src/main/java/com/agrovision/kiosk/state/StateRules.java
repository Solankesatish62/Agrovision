package com.agrovision.kiosk.state;

import java.util.EnumMap;
import java.util.Map;

/**
 * StateRules defines all LEGAL state transitions.
 *
 * It is a pure decision table:
 * (CurrentState + Event) -> NextState
 *
 * Illegal transitions are safely ignored.
 */
public final class StateRules {

    private final Map<AppState, Map<StateEvent, AppState>> transitionTable;

    public StateRules() {
        transitionTable = new EnumMap<>(AppState.class);
        defineTransitions();
    }

    private void defineTransitions() {

        // ================= READY =================
        Map<StateEvent, AppState> ready = new EnumMap<>(StateEvent.class);
        ready.put(StateEvent.OBJECT_DETECTED, AppState.SCANNING);
        ready.put(StateEvent.IDLE_TIMEOUT, AppState.IDLE);
        transitionTable.put(AppState.READY, ready);

        // ================= SCANNING =================
        Map<StateEvent, AppState> scanning = new EnumMap<>(StateEvent.class);
        scanning.put(StateEvent.MATCH_FOUND, AppState.RESULT_AUTO);
        scanning.put(StateEvent.MATCH_NOT_FOUND, AppState.UNKNOWN_NOTE);
        scanning.put(StateEvent.OBJECT_REMOVED, AppState.READY);
        scanning.put(StateEvent.PAUSE_REQUESTED, AppState.RESULT_PAUSED);
        transitionTable.put(AppState.SCANNING, scanning);

        // ================= RESULT_AUTO =================
        Map<StateEvent, AppState> resultAuto = new EnumMap<>(StateEvent.class);
        resultAuto.put(StateEvent.RESULT_TIMEOUT, AppState.READY);
        resultAuto.put(StateEvent.NEW_SCAN_REQUESTED, AppState.SCANNING);
        resultAuto.put(StateEvent.PAUSE_REQUESTED, AppState.RESULT_PAUSED);
        transitionTable.put(AppState.RESULT_AUTO, resultAuto);

        // ================= RESULT_MANUAL_NAV =================
        Map<StateEvent, AppState> resultManual = new EnumMap<>(StateEvent.class);
        resultManual.put(StateEvent.NEW_SCAN_REQUESTED, AppState.SCANNING);
        resultManual.put(StateEvent.RESULT_TIMEOUT, AppState.READY);
        transitionTable.put(AppState.RESULT_MANUAL_NAV, resultManual);

        // ================= RESULT_PAUSED =================
        Map<StateEvent, AppState> paused = new EnumMap<>(StateEvent.class);
        paused.put(StateEvent.RESUME_REQUESTED, AppState.RESULT_AUTO);
        paused.put(StateEvent.NEW_SCAN_REQUESTED, AppState.SCANNING);
        paused.put(StateEvent.OBJECT_REMOVED, AppState.READY);
        transitionTable.put(AppState.RESULT_PAUSED, paused);

        // ================= UNKNOWN_NOTE =================
        Map<StateEvent, AppState> unknown = new EnumMap<>(StateEvent.class);
        unknown.put(StateEvent.MANUAL_SELECTION, AppState.RESULT_MANUAL_NAV);
        unknown.put(StateEvent.OBJECT_REMOVED, AppState.READY);
        unknown.put(StateEvent.NEW_SCAN_REQUESTED, AppState.SCANNING);
        transitionTable.put(AppState.UNKNOWN_NOTE, unknown);

        // ================= IDLE =================
        Map<StateEvent, AppState> idle = new EnumMap<>(StateEvent.class);
        idle.put(StateEvent.ACTIVITY_DETECTED, AppState.READY);
        transitionTable.put(AppState.IDLE, idle);
    }

    /**
     * Returns the next state for the given state + event.
     * Illegal transitions are ignored safely.
     */
    public AppState calculateNextState(AppState currentState, StateEvent event) {

        if (currentState == null || event == null) {
            return currentState;
        }

        Map<StateEvent, AppState> allowed = transitionTable.get(currentState);
        if (allowed == null) {
            return currentState;
        }

        AppState next = allowed.get(event);
        return next != null ? next : currentState;
    }
}
