package com.agrovision.kiosk.ui.result;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.agrovision.kiosk.R;
import com.agrovision.kiosk.state.AppState;
import com.agrovision.kiosk.state.StateEvent;
import com.agrovision.kiosk.state.StateMachine;
import com.agrovision.kiosk.state.StateObserver;
import com.agrovision.kiosk.ui.result.model.ResultType;
import com.agrovision.kiosk.ui.result.model.ScanResult;
import com.agrovision.kiosk.util.LogUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

/**
 * ResultActivity
 *
 * RESPONSIBILITIES:
 * - Orchestrate result navigation
 * - Control timers (UX only)
 * - Emit semantic UI events
 *
 * DOES NOT:
 * - Render UI
 * - Decide known/unknown
 * - Access DB/network
 */
public final class ResultActivity extends AppCompatActivity
        implements StateObserver {

    public static final String EXTRA_SCAN_RESULTS =
            "com.agrovision.kiosk.EXTRA_SCAN_RESULTS";

    private static final long AUTO_ADVANCE_MS = 20_000;
    private static final long UNKNOWN_TIMEOUT_MS = 3_000; // 🚀 Rule 1: 3 seconds timeout
    private static final long PAUSE_DURATION_MS = 120_000;

    // UI controls
    private ImageButton btnNext;
    private ImageButton btnPrev;
    private MaterialButton btnNewScan;
    private FloatingActionButton btnPlay;

    // Renderer
    private ResultRenderer renderer;

    // State
    private List<ScanResult> scanResults;
    private int currentIndex = 0;
    private boolean isPaused = false;

    // Timer
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoAdvanceRunnable = this::advance;
    
    // 🚀 Rule 3: Handler for Unknown Result timeout
    private final Runnable unknownTimeoutRunnable = () -> {
        LogUtils.i("Unknown result timeout reached");
        StateMachine.getInstance(getApplicationContext())
                .transition(StateEvent.RESULT_TIMEOUT); // 🚀 Rule 2
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_result);

        blockBackNavigation();
        hideSystemUI();

        bindControls();
        initRenderer();
        loadResults();

        if (scanResults == null || scanResults.isEmpty()) {
            finish();
            return;
        }

        renderCurrent();
        setupControls();
        startTimer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        StateMachine.getInstance(getApplicationContext())
                .addObserver(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        StateMachine.getInstance(getApplicationContext())
                .removeObserver(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        
        // 🚀 Rule 3: Ensure timer starts/restarts when resuming if needed
        renderCurrent();
    }

    /* =========================================================
       INITIALIZATION
       ========================================================= */

    private void bindControls() {
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrev);
        btnNewScan = findViewById(R.id.btnNewScan);
        btnPlay = findViewById(R.id.btnPlay);
    }

    private void initRenderer() {

        View standardLayout = findViewById(R.id.standardResultLayout);
        View unknownLayout = findViewById(R.id.unknownResultLayout);

        TextView tvMedicineName = findViewById(R.id.tvMedicineName);
        ViewPager2 imagePager = findViewById(R.id.imagePager);
        RecyclerView infoList = findViewById(R.id.infoList);
        TextView tvUnknownHeader = findViewById(R.id.tvUnknownHeader);

        renderer = new ResultRenderer(
                standardLayout,
                unknownLayout,
                tvMedicineName,
                imagePager,
                infoList,
                tvUnknownHeader
        );
    }

    private void loadResults() {
        scanResults = getIntent().getParcelableArrayListExtra(EXTRA_SCAN_RESULTS);
    }

    /* =========================================================
       RENDERING (DELEGATED)
       ========================================================= */

    private void renderCurrent() {
        if (scanResults == null || scanResults.isEmpty()) return;
        if (currentIndex < 0 || currentIndex >= scanResults.size()) return;

        ScanResult current = scanResults.get(currentIndex);
        renderer.render(current);

        // 🚀 Rule 3 & 6: Handle Unknown Result Timer
        if (current.resultType == ResultType.UNKNOWN) {
            stopTimer(); // Clear standard auto-advance

            LogUtils.i("Starting 3s timeout for UNKNOWN result");
            timerHandler.removeCallbacks(unknownTimeoutRunnable);
            timerHandler.postDelayed(unknownTimeoutRunnable, UNKNOWN_TIMEOUT_MS);
        } else {
            timerHandler.removeCallbacks(unknownTimeoutRunnable);
            startTimer(); // Use standard advance for KNOWN results
        }
    }

    /* =========================================================
       NAVIGATION + TIMERS (UX ONLY)
       ========================================================= */

    private void setupControls() {

        btnNext.setOnClickListener(v -> {
            cancelAllTimers(); // 🚀 Rule 4
            isPaused = false;
            advance();
        });

        btnPrev.setOnClickListener(v -> {
            cancelAllTimers(); // 🚀 Rule 4
            isPaused = false;
            currentIndex =
                    (currentIndex - 1 + scanResults.size()) % scanResults.size();
            renderCurrent();
            restartTimer();
        });

        btnPlay.setOnClickListener(v -> {
            cancelAllTimers(); // 🚀 Rule 4
            StateMachine.getInstance(getApplicationContext())
                    .transition(
                        isPaused
                            ? StateEvent.RESUME_REQUESTED
                            : StateEvent.PAUSE_REQUESTED
                    );
        });

        btnNewScan.setOnClickListener(v -> {
            cancelAllTimers(); // 🚀 Rule 4
            StateMachine.getInstance(getApplicationContext())
                    .transition(StateEvent.NEW_SCAN_REQUESTED);
        });
    }

    private void advance() {
        currentIndex = (currentIndex + 1) % scanResults.size();
        renderCurrent();
        restartTimer();
    }

    private void togglePause() {
        isPaused = !isPaused;

        if (isPaused) {
            stopTimer();
            btnPlay.setImageResource(android.R.drawable.ic_media_play);

            timerHandler.postDelayed(() -> {
                if (isPaused) togglePause();
            }, PAUSE_DURATION_MS);

        } else {
            btnPlay.setImageResource(android.R.drawable.ic_media_pause);
            restartTimer();
        }
    }

    @Override
    public void onStateChanged(AppState state) {

        switch (state) {

            case RESULT_AUTO:
            case RESULT_MANUAL_NAV:
                // Active result viewing → timers allowed
                isPaused = false;
                btnPlay.setImageResource(android.R.drawable.ic_media_pause);
                restartTimer();
                break;

            case RESULT_PAUSED:
                // Freeze UX
                isPaused = true;
                btnPlay.setImageResource(android.R.drawable.ic_media_play);
                stopTimer();
                break;

            case READY:
            case IDLE:
                // Exit result screen
                cancelAllTimers();
                finish(); // 🚀 Rule 5
                break;

            default:
                // Ignore others
                break;
        }
    }

    private void startTimer() {
        stopTimer();
        if (!isPaused && scanResults.size() > 1) {
            timerHandler.postDelayed(autoAdvanceRunnable, AUTO_ADVANCE_MS);
        }
    }

    private void restartTimer() {
        stopTimer();
        startTimer();
    }

    private void stopTimer() {
        timerHandler.removeCallbacks(autoAdvanceRunnable);
    }
    
    private void cancelAllTimers() {
        stopTimer();
        timerHandler.removeCallbacks(unknownTimeoutRunnable);
    }

    /* =========================================================
       KIOSK ENFORCEMENT
       ========================================================= */

    private void blockBackNavigation() {
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        LogUtils.i("Back blocked (kiosk mode)");
                    }
                }
        );
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelAllTimers(); // 🚀 Rule 4
    }
}
