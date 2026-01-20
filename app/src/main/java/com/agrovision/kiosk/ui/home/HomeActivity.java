package com.agrovision.kiosk.ui.home;

import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import com.agrovision.kiosk.R;
import com.agrovision.kiosk.camera.CameraController;
import com.agrovision.kiosk.state.AppState;
import com.agrovision.kiosk.state.StateEvent;
import com.agrovision.kiosk.state.StateMachine;
import com.agrovision.kiosk.state.StateObserver;
import com.agrovision.kiosk.util.LogUtils;

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
        implements StateObserver {

    /* =========================================================
       CORE SYSTEMS
       ========================================================= */

    private StateMachine stateMachine;
    private CameraController cameraController;

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
