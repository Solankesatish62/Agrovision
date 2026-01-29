package com.agrovision.kiosk.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import com.agrovision.kiosk.R;
import com.agrovision.kiosk.camera.CameraController;
import com.agrovision.kiosk.camera.ScanResultCallback;
import com.agrovision.kiosk.data.repository.MedicineRepository;
import com.agrovision.kiosk.pipeline.RecognitionPipelineOrchestrator;
import com.agrovision.kiosk.state.AppState;
import com.agrovision.kiosk.state.StateEvent;
import com.agrovision.kiosk.state.StateMachine;
import com.agrovision.kiosk.state.StateObserver;
import com.agrovision.kiosk.ui.result.ResultActivity;
import com.agrovision.kiosk.ui.result.model.ResultType;
import com.agrovision.kiosk.ui.result.model.ScanResult;
import com.agrovision.kiosk.util.LogUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * HomeActivity
 *
 * UI screen for READY / SCANNING states only.
 *
 * HARD RULES:
 * - UI reads state
 * - UI renders
 * - UI emits events
 * - UI NEVER touches camera frames
 * - UI NEVER touches ML / OCR
 * - UI decides NOTHING
 */
public final class HomeActivity extends AppCompatActivity
        implements StateObserver, ScanResultCallback {

    /* =========================================================
       CORE SYSTEMS
       ========================================================= */

    private StateMachine stateMachine;
    private CameraController cameraController;
    private RecognitionPipelineOrchestrator pipeline;

    /* =========================================================
       UI ELEMENTS (MATCH XML IDS EXACTLY)
       ========================================================= */

    private PreviewView cameraPreview;     // @id/cameraPreview
    private EditText etManualSearch;        // @id/etManualSearch

    /* =========================================================
       LIFECYCLE
       ========================================================= */

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        LogUtils.i("HomeActivity created");

        bindViews();
        initDependencies();
        startCamera();
        setupManualSearch();
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraController.stopCamera();
        LogUtils.i("HomeActivity destroyed");
    }

    /* =========================================================
       INITIALIZATION
       ========================================================= */

    private void bindViews() {
        cameraPreview = findViewById(R.id.cameraPreview);
        etManualSearch = findViewById(R.id.etManualSearch);
    }

    private void initDependencies() {
        stateMachine = StateMachine.getInstance(getApplicationContext());
        cameraController = CameraController.getInstance(getApplicationContext());
        cameraController.setScanResultCallback(this);

        // Load medicine catalog and initialize pipeline
        MedicineRepository repository = MedicineRepository.getInstance(getApplicationContext());
        pipeline = new RecognitionPipelineOrchestrator(repository.getAll());
    }

    private void startCamera() {
        cameraController.startCamera(
                this,
                cameraPreview
        );
    }

    /* =========================================================
       MANUAL SEARCH (EVENT EMISSION ONLY)
       ========================================================= */

    private void setupManualSearch() {
        etManualSearch.setOnKeyListener((v, keyCode, event) -> {

            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_ENTER) {

                LogUtils.i("Manual search requested");

                // UI emits semantic intent ONLY
                stateMachine.transition(StateEvent.MANUAL_SELECTION);

                // Kiosk hygiene
                etManualSearch.clearFocus();

                return true;
            }
            return false;
        });
    }

    /* =========================================================
       SCAN RESULT CALLBACK
       ========================================================= */

    @Override
    public void onScanCompleted(List<String> normalizedTexts) {
        List<ScanResult> results = pipeline.resolve(normalizedTexts);

        if (results.isEmpty()) {
            LogUtils.w("No scan results produced");
            return;
        }

        launchResultScreen(results);
    }

    private void launchResultScreen(List<ScanResult> results) {
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra(
                ResultActivity.EXTRA_SCAN_RESULTS,
                new ArrayList<>(results)
        );

        startActivity(intent);

        boolean hasKnown =
                results.stream()
                        .anyMatch(r -> r.resultType == ResultType.KNOWN);

        StateMachine sm = StateMachine.getInstance(getApplicationContext());

        if (hasKnown) {
            sm.transition(StateEvent.MATCH_FOUND);
        } else {
            sm.transition(StateEvent.MATCH_NOT_FOUND);
        }

    }

    /* =========================================================
       STATE OBSERVER
       ========================================================= */

    @Override
    public void onStateChanged(AppState state) {

        LogUtils.i("HomeActivity observed state: " + state);

        switch (state) {

            case READY:
            case SCANNING:
                // HomeActivity is valid
                break;

            default:
                // Any RESULT or IDLE state exits HomeActivity
                finish();
                break;
        }
    }
}
