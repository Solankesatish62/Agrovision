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
import com.agrovision.kiosk.state.StateEvent;
import com.agrovision.kiosk.state.StateMachine;
import com.agrovision.kiosk.ui.result.model.ScanResult;
import com.agrovision.kiosk.util.LogUtils;
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
public final class ResultActivity extends AppCompatActivity {

    public static final String EXTRA_SCAN_RESULTS =
            "com.agrovision.kiosk.EXTRA_SCAN_RESULTS";

    private static final long AUTO_ADVANCE_MS = 20_000;
    private static final long PAUSE_DURATION_MS = 120_000;

    // UI controls
    private ImageButton btnNext;
    private ImageButton btnPrev;
    private ImageButton btnNewScan;
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
    protected void onResume() {
        super.onResume();
        hideSystemUI();
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


    @SuppressWarnings("unchecked")
    private void loadResults() {
        scanResults = (List<ScanResult>)
                getIntent().getSerializableExtra(EXTRA_SCAN_RESULTS);
    }

    /* =========================================================
       RENDERING (DELEGATED)
       ========================================================= */

    private void renderCurrent() {
        if (scanResults == null || scanResults.isEmpty()) return;
        if (currentIndex < 0 || currentIndex >= scanResults.size()) return;

        renderer.render(scanResults.get(currentIndex));
    }


    /* =========================================================
       NAVIGATION + TIMERS (UX ONLY)
       ========================================================= */

    private void setupControls() {

        btnNext.setOnClickListener(v -> {
            isPaused = false;
            advance();
        });

        btnPrev.setOnClickListener(v -> {
            isPaused = false;
            currentIndex =
                    (currentIndex - 1 + scanResults.size()) % scanResults.size();
            renderCurrent();
            restartTimer();
        });

        btnPlay.setOnClickListener(v -> togglePause());

        btnNewScan.setOnClickListener(v -> {
            StateMachine.getInstance(getApplicationContext())
                    .transition(StateEvent.NEW_SCAN_REQUESTED);
            finish();
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
        stopTimer();
    }
}
