package com.agrovision.kiosk.ui.result;

import android.content.Intent;
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
import com.agrovision.kiosk.ui.home.HomeActivity;
import com.agrovision.kiosk.ui.result.model.ResultType;
import com.agrovision.kiosk.ui.result.model.ScanResult;
import com.agrovision.kiosk.util.LogUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/**
 * ResultActivity
 *
 * Responsibility: UI controller for identifying medicines.
 * Implements a 15-second auto-rotate timer with interactive manual overrides.
 * Automatically returns to HomeActivity after displaying results.
 */
public final class ResultActivity extends AppCompatActivity
        implements StateObserver {

    public static final String EXTRA_SCAN_RESULTS =
            "com.agrovision.kiosk.EXTRA_SCAN_RESULTS";

    private static final long AUTO_ROTATE_MS = 15_000; 
    private static final long UNKNOWN_TIMEOUT_MS = 3_000; 
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

    // 🚀 Timer System
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    
    private final Runnable autoRotateRunnable = () -> {
        LogUtils.d("Timer triggered: advancing or returning to scan");
        advance();
    };
    
    private final Runnable unknownTimeoutRunnable = () -> {
        LogUtils.i("Unknown result timeout: returning to scan");
        returnToScan(); // 🚀 Explicitly navigate
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
            returnToScan();
            return;
        }

        setupControls();
        renderCurrent();
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
        cancelAllTimers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        renderCurrent();
    }

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
                standardLayout, unknownLayout, tvMedicineName,
                imagePager, infoList, tvUnknownHeader
        );
    }

    private void loadResults() {
        scanResults = getIntent().getParcelableArrayListExtra(EXTRA_SCAN_RESULTS);
    }

    /* =========================================================
       NAVIGATION (Rule 3)
       ========================================================= */

    private void returnToScan() {
        LogUtils.i("Executing deterministic navigation to HomeActivity");
        
        // Update state machine before navigating
        StateMachine.getInstance(getApplicationContext())
                .transition(StateEvent.RESULT_TIMEOUT);

        Intent intent = new Intent(ResultActivity.this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /* =========================================================
       UI INTERACTION
       ========================================================= */

    private void setupControls() {
        btnNext.setOnClickListener(v -> {
            cancelAllTimers();
            isPaused = false;
            advance();
        });

        btnPrev.setOnClickListener(v -> {
            cancelAllTimers();
            isPaused = false;
            currentIndex = (currentIndex - 1 + scanResults.size()) % scanResults.size();
            renderCurrent();
        });

        btnPlay.setOnClickListener(v -> {
            StateMachine.getInstance(getApplicationContext())
                    .transition(isPaused ? StateEvent.RESUME_REQUESTED : StateEvent.PAUSE_REQUESTED);
        });

        btnNewScan.setOnClickListener(v -> {
            cancelAllTimers();
            StateMachine.getInstance(getApplicationContext())
                    .transition(StateEvent.NEW_SCAN_REQUESTED);
            returnToScan(); 
        });
    }

    private void renderCurrent() {
        if (scanResults == null || scanResults.isEmpty()) return;
        
        ScanResult current = scanResults.get(currentIndex);
        renderer.render(current);

        cancelAllTimers(); 

        if (current.resultType == ResultType.UNKNOWN) {
            timerHandler.postDelayed(unknownTimeoutRunnable, UNKNOWN_TIMEOUT_MS);
        } else if (!isPaused) {
            timerHandler.postDelayed(autoRotateRunnable, AUTO_ROTATE_MS);
        }
        
        updatePlayButtonState();
    }

    private void advance() {
        if (currentIndex + 1 < scanResults.size()) {
            currentIndex++;
            renderCurrent();
        } else {
            LogUtils.i("All results displayed. Returning to scan.");
            returnToScan(); // 🚀 Explicitly navigate
        }
    }

    private void updatePlayButtonState() {
        btnPlay.setImageResource(isPaused 
                ? android.R.drawable.ic_media_play 
                : android.R.drawable.ic_media_pause);
    }

    @Override
    public void onStateChanged(AppState state) {
        runOnUiThread(() -> {
            LogUtils.d("ResultActivity observed state: " + state);
            switch (state) {
                case RESULT_PAUSED:
                    isPaused = true;
                    cancelAllTimers();
                    updatePlayButtonState();
                    break;

                case RESULT_AUTO:
                case RESULT_MANUAL_NAV:
                    isPaused = false;
                    renderCurrent(); 
                    break;

                case READY:
                case IDLE:
                    cancelAllTimers();
                    if (!isFinishing()) {
                        finish(); 
                    }
                    break;
            }
        });
    }

    private void cancelAllTimers() {
        timerHandler.removeCallbacks(autoRotateRunnable);
        timerHandler.removeCallbacks(unknownTimeoutRunnable);
    }

    private void blockBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                LogUtils.i("Back navigation is blocked in kiosk mode");
            }
        });
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
        cancelAllTimers();
    }
}
