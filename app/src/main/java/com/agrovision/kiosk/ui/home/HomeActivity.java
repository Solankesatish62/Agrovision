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
import com.agrovision.kiosk.data.model.Medicine;
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        LogUtils.i("HomeActivity created");

        bindViews();
        initDependencies();
        startCamera(); // 🚀 Directly starting camera as requested
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

    private void bindViews() {
        cameraPreview = findViewById(R.id.cameraPreview);
        etManualSearch = findViewById(R.id.etManualSearch);
    }

    private void initDependencies() {
        stateMachine = StateMachine.getInstance(getApplicationContext());
        
        // Setup CameraController
        cameraController = CameraController.getInstance(getApplicationContext());
        cameraController.setScanResultCallback(this);

        // 1. Fetch medicine catalog once from Repository
        MedicineRepository repository = MedicineRepository.getInstance(getApplicationContext());
        List<Medicine> catalog = repository.getAll();
        
        // 2. Initialize Orchestrator with the pre-loaded catalog
        pipeline = new RecognitionPipelineOrchestrator(catalog);
    }

    private void startCamera() {
        cameraController.startCamera(
                this,
                cameraPreview
        );
    }

    private void setupManualSearch() {
        etManualSearch.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                LogUtils.i("Manual search requested");
                stateMachine.transition(StateEvent.MANUAL_SELECTION);
                etManualSearch.clearFocus();
                return true;
            }
            return false;
        });
    }

    @Override
    public void onScanCompleted(List<String> normalizedTexts) {
        runOnUiThread(() -> {
            // 3. Resolve OCR text to ScanResults using Orchestrator
            List<ScanResult> results = pipeline.resolve(normalizedTexts);

            if (results == null || results.isEmpty()) {
                LogUtils.w("No scan results produced");
                return;
            }

            // 4. Launch ResultActivity with the processed results
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

        // Update state machine based on match findings
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
        if (state == AppState.IDLE) {
            finish();
        }
    }
}
