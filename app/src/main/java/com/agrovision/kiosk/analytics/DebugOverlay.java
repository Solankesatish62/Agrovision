package com.agrovision.kiosk.analytics;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.agrovision.kiosk.state.AppState;
import com.agrovision.kiosk.state.StateMachine;

/**
 * DebugOverlay
 *
 * PURPOSE:
 * - On-screen, read-only diagnostics for field debugging
 *
 * DESIGN RULES:
 * - NO logic
 * - NO state changes
 * - NO analytics emission
 * - UI reflection ONLY
 *
 * This overlay may be removed entirely in production
 * without affecting system behavior.
 */
public final class DebugOverlay {

    // Root view of the overlay
    private final View rootView;

    // Text view showing debug info
    private final TextView debugText;

    // Data sources (read-only)
    private final StateMachine stateMachine;
    private final EventTracker eventTracker;
    private final PerformanceLogger performanceLogger;

    /**
     * Creates a DebugOverlay instance.
     *
     * @param context Application context
     */
    public DebugOverlay(Context context) {
        Context appContext = context.getApplicationContext();

        this.stateMachine = StateMachine.getInstance(appContext);
        this.eventTracker = EventTracker.getInstance(appContext);
        this.performanceLogger = PerformanceLogger.getInstance();

        // Create root container
        FrameLayout container = new FrameLayout(appContext);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        ));

        // Create text view
        debugText = new TextView(appContext);
        debugText.setTextColor(Color.WHITE);
        debugText.setBackgroundColor(0xAA000000); // semi-transparent black
        debugText.setTextSize(12f);
        debugText.setPadding(16, 16, 16, 16);

        container.addView(debugText);

        this.rootView = container;
    }

    /**
     * Returns the overlay view.
     *
     * The caller decides whether to attach it to the UI.
     */
    public View getView() {
        return rootView;
    }

    /**
     * Refreshes displayed debug information.
     *
     * SAFE:
     * - Read-only
     * - Can be called frequently
     * - No side effects
     */
    public void refresh() {
        AppState currentState = stateMachine.getCurrentState();

        StringBuilder sb = new StringBuilder(128);

        sb.append("STATE: ")
                .append(currentState != null ? currentState.name() : "N/A")
                .append("\n");

        sb.append("LAST EVENT: ")
                .append(eventTracker.getLastEvent() != null
                        ? eventTracker.getLastEvent().name()
                        : "NONE")
                .append("\n");

        sb.append("YOLO: ")
                .append(performanceLogger.getLastYoloLatencyMs())
                .append(" ms\n");

        sb.append("OCR: ")
                .append(performanceLogger.getLastOcrLatencyMs())
                .append(" ms\n");

        sb.append("SCAN: ")
                .append(performanceLogger.getLastEndToEndLatencyMs())
                .append(" ms");

        debugText.setText(sb.toString());
    }
}
