//package com.agrovision.kiosk.analytics;
//
//import android.content.Context;
//
//import com.agrovision.kiosk.analytics.events.MvpEvent;
//import com.agrovision.kiosk.threading.IoExecutor;
//import com.agrovision.kiosk.util.LogUtils;
//
//import java.util.concurrent.atomic.AtomicReference;
//
///**
// * EventTracker
// *
// * PURPOSE:
// * - Central gateway for emitting analytics events
// *
// * DESIGN:
// * - Singleton
// * - Holds Application Context only
// * - Delegates async work to IoExecutor
// *
// * HARD RULES:
// * - ONLY StateMachine may emit events
// * - Never creates its own threads
// * - Never blocks callers
// */
//public final class EventTracker {
//
//    // Singleton instance
//    private static volatile EventTracker INSTANCE;
//
//    // Application context (never Activity / Service)
//    private final Context appContext;
//
//    // Last emitted event (for DebugOverlay)
//    private final AtomicReference<MvpEvent> lastEvent =
//            new AtomicReference<>(null);
//
//    /**
//     * Private constructor to enforce singleton.
//     */
//    private EventTracker(Context context) {
//        this.appContext = context.getApplicationContext();
//    }
//
//    /**
//     * Returns the singleton EventTracker instance.
//     *
//     * Must be initialized with Application Context.
//     */
//    public static EventTracker getInstance(Context context) {
//        if (INSTANCE == null) {
//            synchronized (EventTracker.class) {
//                if (INSTANCE == null) {
//                    INSTANCE = new EventTracker(context);
//                }
//            }
//        }
//        return INSTANCE;
//    }
//
//    /**
//     * Asynchronous initialization hook.
//     *
//     * Uses IoExecutor to avoid creating new threads.
//     */
//    public void initAsync() {
//        IoExecutor.submit(() -> {
//            try {
//                // Future initialization logic (file/db/network)
//                LogUtils.i("EventTracker initialized");
//            } catch (Exception e) {
//                // Analytics must never crash the kiosk
//                LogUtils.e("EventTracker init failed", e);
//            }
//        });
//    }
//
//    /**
//     * Emit an analytics event.
//     *
//     * CONTRACT:
//     * - Caller must be StateMachine
//     * - Event must represent a meaningful system fact
//     */
//    public void track(MvpEvent event) {
//        if (event == null) return;
//
//        try {
//            // Update last event for observability
//            lastEvent.set(event);
//
//            // MVP analytics output (Logcat only)
//            LogUtils.i("MVP_EVENT=" + event.name());
//
//            // Future: persist / upload via IoExecutor
//
//        } catch (Exception e) {
//            // Analytics must never affect system stability
//            LogUtils.e("EventTracker track failed", e);
//        }
//    }
//
//    /**
//     * Returns the most recently emitted event.
//     *
//     * Used by DebugOverlay only.
//     */
//    public MvpEvent getLastEvent() {
//        return lastEvent.get();
//    }
//}
package com.agrovision.kiosk.analytics;

import android.content.Context;

import com.agrovision.kiosk.analytics.events.MvpEvent;
import com.agrovision.kiosk.state.AppState;   // ✅ Added
import com.agrovision.kiosk.state.StateEvent; // ✅ Added
import com.agrovision.kiosk.threading.IoExecutor;
import com.agrovision.kiosk.util.LogUtils;

import java.util.concurrent.atomic.AtomicReference;

/**
 * EventTracker
 *
 * PURPOSE:
 * - Central gateway for emitting analytics events
 * - Central logger for critical state transitions
 *
 * DESIGN:
 * - Singleton
 * - Holds Application Context only
 * - Delegates async work to IoExecutor
 *
 * HARD RULES:
 * - ONLY StateMachine may emit events
 * - Never creates its own threads
 * - Never blocks callers
 */
public final class EventTracker {

    // Singleton instance
    private static volatile EventTracker INSTANCE;

    // Application context (never Activity / Service)
    private final Context appContext;

    // Last emitted event (for DebugOverlay)
    private final AtomicReference<MvpEvent> lastEvent =
            new AtomicReference<>(null);

    /**
     * Private constructor to enforce singleton.
     */
    private EventTracker(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Returns the singleton EventTracker instance.
     *
     * Must be initialized with Application Context.
     */
    public static EventTracker getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (EventTracker.class) {
                if (INSTANCE == null) {
                    INSTANCE = new EventTracker(context);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Asynchronous initialization hook.
     *
     * Uses IoExecutor to avoid creating new threads.
     */
    public void initAsync() {
        IoExecutor.submit(() -> {
            try {
                // Future initialization logic (file/db/network)
                LogUtils.i("EventTracker initialized");
            } catch (Exception e) {
                // Analytics must never crash the kiosk
                LogUtils.e("EventTracker init failed", e);
            }
        });
    }

    /**
     * Emit an analytics event.
     *
     * CONTRACT:
     * - Caller must be StateMachine
     * - Event must represent a meaningful system fact
     */
    public void track(MvpEvent event) {
        if (event == null) return;

        try {
            // Update last event for observability
            lastEvent.set(event);

            // MVP analytics output (Logcat only)
            LogUtils.i("MVP_EVENT=" + event.name());

            // Future: persist / upload via IoExecutor

        } catch (Exception e) {
            // Analytics must never affect system stability
            LogUtils.e("EventTracker track failed", e);
        }
    }

    /**
     * Logs a State Machine transition.
     *
     * PURPOSE:
     * - purely for debugging and tracing the system brain.
     * - This is NOT an analytics event (MvpEvent) because it happens too often.
     */
    public void logStateTransition(AppState from, AppState to, StateEvent trigger) { // ✅ FIXED: Method added
        if (from == null || to == null || trigger == null) return;

        try {
            // We use LogUtils directly.
            // Format: STATE_CHANGE: READY -> SCANNING [via SCAN_REQUESTED]
            LogUtils.i(String.format("STATE_CHANGE: %s -> %s [via %s]",
                    from.name(),
                    to.name(),
                    trigger.name()));

        } catch (Exception e) {
            LogUtils.e("Failed to log state transition", e);
        }
    }

    /**
     * Returns the most recently emitted event.
     *
     * Used by DebugOverlay only.
     */
    public MvpEvent getLastEvent() {
        return lastEvent.get();
    }
}
