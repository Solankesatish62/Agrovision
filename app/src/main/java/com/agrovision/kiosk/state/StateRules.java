package com.agrovision.kiosk.state;

import java.util.EnumMap;
import java.util.Map;

/**
 * StateRules defines all LEGAL state transitions.
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
        ready.put(StateEvent.IDLE_AD_TRIGGERED, AppState.IDLE_AD);
        transitionTable.put(AppState.READY, ready);

        // ================= SCANNING =================
        Map<StateEvent, AppState> scanning = new EnumMap<>(StateEvent.class);
        scanning.put(StateEvent.MATCH_FOUND, AppState.RESULT_AUTO);
        scanning.put(StateEvent.MATCH_NOT_FOUND, AppState.RESULT_UNKNOWN);
        scanning.put(StateEvent.OBJECT_REMOVED, AppState.READY);
        scanning.put(StateEvent.PAUSE_REQUESTED, AppState.RESULT_PAUSED);
        scanning.put(StateEvent.IDLE_TIMEOUT, AppState.IDLE); 
        scanning.put(StateEvent.IDLE_AD_TRIGGERED, AppState.IDLE_AD); // 🚀 Allow idle ad from scanning
        transitionTable.put(AppState.SCANNING, scanning);

        // ================= RESULT_AUTO =================
        Map<StateEvent, AppState> resultAuto = new EnumMap<>(StateEvent.class);
        resultAuto.put(StateEvent.RESULT_TIMEOUT, AppState.READY);
        resultAuto.put(StateEvent.SCAN_AD_TRIGGERED, AppState.SCAN_AD);
        resultAuto.put(StateEvent.NEW_SCAN_REQUESTED, AppState.SCANNING);
        resultAuto.put(StateEvent.PAUSE_REQUESTED, AppState.RESULT_PAUSED);
        resultAuto.put(StateEvent.IDLE_TIMEOUT, AppState.IDLE);
        transitionTable.put(AppState.RESULT_AUTO, resultAuto);

        // ================= RESULT_MANUAL_NAV =================
        Map<StateEvent, AppState> resultManual = new EnumMap<>(StateEvent.class);
        resultManual.put(StateEvent.NEW_SCAN_REQUESTED, AppState.SCANNING);
        resultManual.put(StateEvent.RESULT_TIMEOUT, AppState.READY);
        resultManual.put(StateEvent.SCAN_AD_TRIGGERED, AppState.SCAN_AD);
        resultManual.put(StateEvent.PAUSE_REQUESTED, AppState.RESULT_PAUSED);
        resultManual.put(StateEvent.IDLE_TIMEOUT, AppState.IDLE);
        transitionTable.put(AppState.RESULT_MANUAL_NAV, resultManual);

        // ================= RESULT_PAUSED =================
        Map<StateEvent, AppState> paused = new EnumMap<>(StateEvent.class);
        paused.put(StateEvent.RESUME_REQUESTED, AppState.RESULT_AUTO);
        paused.put(StateEvent.NEW_SCAN_REQUESTED, AppState.SCANNING);
        paused.put(StateEvent.OBJECT_REMOVED, AppState.READY);
        paused.put(StateEvent.IDLE_TIMEOUT, AppState.IDLE);
        transitionTable.put(AppState.RESULT_PAUSED, paused);

        // ================= RESULT_UNKNOWN =================
        Map<StateEvent, AppState> unknown = new EnumMap<>(StateEvent.class);
        unknown.put(StateEvent.MANUAL_SELECTION, AppState.RESULT_MANUAL_NAV);
        unknown.put(StateEvent.OBJECT_REMOVED, AppState.READY);
        unknown.put(StateEvent.NEW_SCAN_REQUESTED, AppState.SCANNING);
        unknown.put(StateEvent.RESULT_TIMEOUT, AppState.READY);
        unknown.put(StateEvent.PAUSE_REQUESTED, AppState.RESULT_PAUSED);
        unknown.put(StateEvent.IDLE_TIMEOUT, AppState.IDLE);
        transitionTable.put(AppState.RESULT_UNKNOWN, unknown);

        // ================= IDLE =================
        Map<StateEvent, AppState> idle = new EnumMap<>(StateEvent.class);
        idle.put(StateEvent.ACTIVITY_DETECTED, AppState.READY);
        idle.put(StateEvent.OBJECT_DETECTED, AppState.SCANNING);
        idle.put(StateEvent.IDLE_AD_TRIGGERED, AppState.IDLE_AD);
        transitionTable.put(AppState.IDLE, idle);

        // ================= SCAN_AD =================
        Map<StateEvent, AppState> scanAd = new EnumMap<>(StateEvent.class);
        scanAd.put(StateEvent.AD_COMPLETED, AppState.READY);
        scanAd.put(StateEvent.ACTIVITY_DETECTED, AppState.READY);
        transitionTable.put(AppState.SCAN_AD, scanAd);

        // ================= IDLE_AD =================
        Map<StateEvent, AppState> idleAd = new EnumMap<>(StateEvent.class);
        idleAd.put(StateEvent.AD_COMPLETED, AppState.READY);
        idleAd.put(StateEvent.ACTIVITY_DETECTED, AppState.READY);
        // 🚀 CRITICAL: Removed OBJECT_DETECTED -> SCANNING transition here.
        // We only wake up from Idle Ads on real touch or confirmed medicine detection (OCR).
        // This prevents "jumpy" camera noise from closing the advertisement prematurely.
        transitionTable.put(AppState.IDLE_AD, idleAd);
    }

    public AppState calculateNextState(AppState currentState, StateEvent event) {
        if (currentState == null || event == null) return currentState;
        Map<StateEvent, AppState> allowed = transitionTable.get(currentState);
        if (allowed == null) return currentState;
        AppState next = allowed.get(event);
        return next != null ? next : currentState;
    }
}
