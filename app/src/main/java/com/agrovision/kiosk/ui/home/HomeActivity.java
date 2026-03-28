package com.agrovision.kiosk.ui.home;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import com.agrovision.kiosk.R;
import com.agrovision.kiosk.camera.CameraController;
import com.agrovision.kiosk.camera.ScanResultCallback;
import com.agrovision.kiosk.data.model.Medicine;
import com.agrovision.kiosk.data.repository.MedicineRepository;
import com.agrovision.kiosk.pipeline.RecognitionPipelineOrchestrator;
import com.agrovision.kiosk.state.AppState;
import com.agrovision.kiosk.state.StateEvent;
import com.agrovision.kiosk.state.StateMachine;
import com.agrovision.kiosk.state.StateObserver;
import com.agrovision.kiosk.ui.ad.AdActivity;
import com.agrovision.kiosk.ui.result.ResultActivity;
import com.agrovision.kiosk.ui.result.model.ResultType;
import com.agrovision.kiosk.ui.result.model.ScanResult;
import com.agrovision.kiosk.util.LogUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * HomeActivity
 *
 * Responsibility: UI controller for the scanning screen.
 * Acts as the COORDINATOR between the vision pipeline and the business logic.
 */
public final class HomeActivity extends AppCompatActivity
        implements StateObserver, ScanResultCallback {

    private StateMachine stateMachine;
    private CameraController cameraController;
    private RecognitionPipelineOrchestrator pipeline;

    private PreviewView cameraPreview;
    private EditText etManualSearch;
    private TextView tvDailyScanCount;

    // Daily Scan Count
    private static final String PREFS_NAME = "scan_stats";
    private static final String KEY_SCAN_COUNT = "scan_count";
    private static final String KEY_LAST_DATE = "last_date";

    // 🚀 Idle Ad Timer
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private static final long IDLE_THRESHOLD_MS = 60_000; // 60 seconds
    private final Runnable idleRunnable = () -> {
        LogUtils.i("System idle for 60s. Transitioning to AdActivity.");
        stateMachine.transition(StateEvent.IDLE_TIMEOUT);
        startActivity(new Intent(this, AdActivity.class));
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        LogUtils.i("HomeActivity created");

        bindViews();
        initDependencies();
        // Camera starts in onResume
        setupManualSearch();
        displayCurrentScanCount();
    }

    @Override
    protected void onStart() {
        super.onStart();
        stateMachine.addObserver(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        stateMachine.removeObserver(this);
        stopIdleTimer(); // Prevent leaks
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("STATE_DEBUG", "HomeActivity resumed. Current state: " + stateMachine.getCurrentState());
        // 🚀 RE-BIND CAMERA: Ensure camera comes back to HomeActivity preview
        startCamera();
        resetIdleTimer();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        resetIdleTimer(); // Any touch/click resets the timer
    }

    private void bindViews() {
        cameraPreview = findViewById(R.id.cameraPreview);
        etManualSearch = findViewById(R.id.etManualSearch);
        tvDailyScanCount = findViewById(R.id.tvDailyScanCount);
    }

    private void initDependencies() {
        stateMachine = StateMachine.getInstance(getApplicationContext());
        
        // Setup CameraController
        cameraController = CameraController.getInstance(getApplicationContext());
        cameraController.setScanResultCallback(this);

        // Initialize Orchestrator
        pipeline = new RecognitionPipelineOrchestrator(getApplicationContext());
    }

    private void startCamera() {
        cameraController.setScanResultCallback(this);
        cameraController.startCamera(
                this,
                cameraPreview
        );
    }

    private void setupManualSearch() {
        etManualSearch.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                LogUtils.i("Manual search requested");
                resetIdleTimer();
                stateMachine.transition(StateEvent.MANUAL_SELECTION);
                etManualSearch.clearFocus();
                return true;
            }
            return false;
        });
    }

    @Override
    public void onScanCompleted(List<String> normalizedTexts) {
        Log.d("STATE_DEBUG", "onScanCompleted triggered in HomeActivity");
        // 🚀 Reset idle timer because camera saw something or detection is active
        resetIdleTimer();

        // If we were in IDLE state, transition to READY first (UI wake up)
        if (stateMachine.getCurrentState() == AppState.IDLE) {
            stateMachine.transition(StateEvent.ACTIVITY_DETECTED);
        }

        runOnUiThread(() -> {
            List<ScanResult> results = pipeline.resolve(normalizedTexts);

            if (results == null || results.isEmpty()) {
                LogUtils.w("No scan results produced");
                return;
            }

            incrementScanCount();
            launchResultScreen(results);
        });
    }

    private void launchResultScreen(List<ScanResult> results) {
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putParcelableArrayListExtra(
                ResultActivity.EXTRA_SCAN_RESULTS,
                new ArrayList<>(results)
        );

        startActivity(intent);

        boolean hasKnown = results.stream()
                .anyMatch(r -> r.resultType == ResultType.KNOWN);

        if (hasKnown) {
            stateMachine.transition(StateEvent.MATCH_FOUND);
        } else {
            stateMachine.transition(StateEvent.MATCH_NOT_FOUND);
        }
    }

    @Override
    public void onStateChanged(AppState state) {
        LogUtils.i("HomeActivity observed state: " + state);
        // FIX: Do NOT finish HomeActivity on IDLE. Just let it stay in background.
        // This ensures we can return to it instantly when AdActivity finishes.
    }

    /* =========================================================
       IDLE TIMER LOGIC
       ========================================================= */

    private void resetIdleTimer() {
        stopIdleTimer();
        idleHandler.postDelayed(idleRunnable, IDLE_THRESHOLD_MS);
    }

    private void stopIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable);
    }

    /* =========================================================
       DAILY SCAN COUNT LOGIC
       ========================================================= */

    private void incrementScanCount() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String today = getTodayDateString();
        String lastDate = prefs.getString(KEY_LAST_DATE, "");

        int currentCount = prefs.getInt(KEY_SCAN_COUNT, 0);

        if (today.equals(lastDate)) {
            currentCount++;
        } else {
            currentCount = 1;
        }

        prefs.edit()
                .putInt(KEY_SCAN_COUNT, currentCount)
                .putString(KEY_LAST_DATE, today)
                .apply();

        displayCurrentScanCount();
    }

    private void displayCurrentScanCount() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String today = getTodayDateString();
        String lastDate = prefs.getString(KEY_LAST_DATE, "");

        int count = 0;
        if (today.equals(lastDate)) {
            count = prefs.getInt(KEY_SCAN_COUNT, 0);
        }

        if (tvDailyScanCount != null) {
            tvDailyScanCount.setText(String.format(Locale.US, "आजचे स्कॅन: %d", count));
        }
    }

    private String getTodayDateString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
