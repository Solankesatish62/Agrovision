package com.agrovision.kiosk.state;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.agrovision.kiosk.analytics.EventTracker;

import java.util.HashSet;
import java.util.Set;

/**
 * StateMachine is the single authority that:
 * - Holds current AppState
 * - Applies StateRules
 * - Notifies observers
 */
public final class StateMachine {

    private static StateMachine instance;

    private AppState currentState = AppState.READY;

    private final StateRules rules = new StateRules();
    private final EventTracker eventTracker;
    private final Handler mainHandler;

    private final Set<StateObserver> observers = new HashSet<>();

    public static synchronized StateMachine getInstance(Context context) {
        if (instance == null) {
            instance = new StateMachine(context.getApplicationContext());
        }
        return instance;
    }

    private StateMachine(Context appContext) {
        this.eventTracker = EventTracker.getInstance(appContext);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public synchronized AppState getCurrentState() {
        return currentState;
    }

    /**
     * Main entry point for all state transitions.
     */
    public synchronized void transition(StateEvent event) {

        AppState next = rules.calculateNextState(currentState, event);
        if (next == currentState) {
            return;
        }

        eventTracker.logStateTransition(currentState, next, event);

        currentState = next;

        notifyObservers(next);
    }

    public synchronized void addObserver(StateObserver observer) {
        observers.add(observer);
    }

    public synchronized void removeObserver(StateObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers(AppState state) {
        Set<StateObserver> snapshot = new HashSet<>(observers);
        for (StateObserver observer : snapshot) {
            observer.onStateChanged(state);
        }
    }
}
