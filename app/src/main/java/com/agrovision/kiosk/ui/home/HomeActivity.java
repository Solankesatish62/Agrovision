package com.agrovision.kiosk.ui.home;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.agrovision.kiosk.R;
import com.agrovision.kiosk.camera.CameraController;
import com.agrovision.kiosk.camera.ScanResultCallback;
import com.agrovision.kiosk.pipeline.RecognitionPipelineOrchestrator;
import com.agrovision.kiosk.state.AppState;
import com.agrovision.kiosk.state.StateEvent;
import com.agrovision.kiosk.state.StateMachine;
import com.agrovision.kiosk.state.StateObserver;
import com.agrovision.kiosk.ui.ad.AdActivity;
import com.agrovision.kiosk.ui.ad.AdManager;
import com.agrovision.kiosk.ui.result.ResultActivity;
import com.agrovision.kiosk.app.UpdateManager;
import com.agrovision.kiosk.ui.result.model.ResultType;
import com.agrovision.kiosk.ui.result.model.ScanResult;
import com.agrovision.kiosk.util.AudioCacheManager;
import com.agrovision.kiosk.util.LogUtils;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private BoundingBoxOverlay overlayView;
    private TextView tvDailyScanCount;

    // 🚀 Permission Launcher
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    LogUtils.i("Camera permission granted by user.");
                    startCamera();
                } else {
                    LogUtils.e("Camera permission denied by user.");
                    Toast.makeText(this, "कॅमेरा परवानगी आवश्यक आहे (Camera permission required)", Toast.LENGTH_LONG).show();
                }
            });

    // 🚀 Scan Lock & Debounce
    private boolean isScanLocked = false;
    private long lastScanTime = 0;

    // Daily Scan Count
    private static final String PREFS_NAME = "scan_stats";
    private static final String KEY_SCAN_COUNT = "scan_count";
    private static final String KEY_SUCCESSFUL_AD_COUNT = "successful_ad_count";
    private static final String KEY_LAST_DATE = "last_date";

    // 🚀 Idle Ad Timer
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private static final long IDLE_THRESHOLD_MS = 30_000; // 30 seconds
    private final Runnable idleRunnable = () -> {
        AppState currentState = stateMachine.getCurrentState();
        LogUtils.i("System idle check triggered. Current state: " + currentState);
        
        // Only trigger idle ad if we are in a state that represents being "on home" or "ready/scanning"
        // but no active match was found yet.
        if (currentState == AppState.READY || currentState == AppState.SCANNING || currentState == AppState.IDLE) {
            LogUtils.i("System idle threshold reached. Transitioning to AdActivity (IDLE mode).");
            stateMachine.transition(StateEvent.IDLE_AD_TRIGGERED);
            
            Intent intent = new Intent(this, AdActivity.class);
            intent.putExtra(AdActivity.EXTRA_AD_TYPE, AdActivity.AdType.IDLE);
            intent.putExtra(AdActivity.EXTRA_AD_DURATION, 15000L); // 15 seconds for idle
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } else {
            LogUtils.d("Skipping idle ad: currently in state " + currentState);
            // Re-post if we are still active but in a weird state?
            resetIdleTimer();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // 🚀 STEP 1: FORCE LANDSCAPE orientation for Kiosk
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        LogUtils.i("HomeActivity created");

        bindViews();
        initDependencies();
        // Camera starts in onResume
        displayCurrentScanCount();

        // 🚀 Initialize Ad Preloading
        AdManager.getInstance(this);

        // 🚀 STEP 3: Check for OTA updates
        new UpdateManager(this).checkForUpdates();
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
        
        // 🚀 Unlock scanning when returning to Home
        isScanLocked = false;
        
        // 🚀 ENSURE DETECTION IS RESUMED after ads or result screens
        cameraController.setDetectionEnabled(true);
        
        // 🚀 CRITICAL FIX: Reset the pipeline lock to prevent deadlocks from previous activities
        cameraController.resetPipeline();
        
        // 🚀 RE-BIND CAMERA: Check permissions first
        checkCameraPermission();
        resetIdleTimer();
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            LogUtils.i("Requesting camera permission...");
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        resetIdleTimer(); // Any touch/click resets the timer
    }

    private void bindViews() {
        cameraPreview = findViewById(R.id.cameraPreview);
        overlayView = findViewById(R.id.overlayView);
        tvDailyScanCount = findViewById(R.id.tvDailyScanCount);

        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettingsDialog());
    }

    private void showSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        androidx.appcompat.widget.SwitchCompat switchVoice = dialogView.findViewById(R.id.switchVoice);
        SeekBar seekBarVolume = dialogView.findViewById(R.id.seekBarVolume);
        Spinner spinnerResultTime = dialogView.findViewById(R.id.spinnerResultTime);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.result_time_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerResultTime.setAdapter(adapter);

        SharedPreferences prefs = getSharedPreferences("kiosk_settings", MODE_PRIVATE);
        switchVoice.setChecked(prefs.getBoolean("voice_enabled", true));
        seekBarVolume.setProgress(prefs.getInt("voice_volume", 100));

        int currentTime = prefs.getInt("RESULT_SCREEN_TIME", 30);
        int selection = 0;
        if (currentTime == 45) selection = 1;
        else if (currentTime == 60) selection = 2;
        spinnerResultTime.setSelection(selection);

        new AlertDialog.Builder(this)
                .setTitle("सेटिंग्ज (Settings)")
                .setView(dialogView)
                .setPositiveButton("जतन करा (Save)", (dialog, which) -> {
                    int selectedTime = 30;
                    if (spinnerResultTime.getSelectedItemPosition() == 1) selectedTime = 45;
                    else if (spinnerResultTime.getSelectedItemPosition() == 2) selectedTime = 60;

                    prefs.edit()
                            .putBoolean("voice_enabled", switchVoice.isChecked())
                            .putInt("voice_volume", seekBarVolume.getProgress())
                            .putInt("RESULT_SCREEN_TIME", selectedTime)
                            .apply();
                })
                .setNegativeButton("रद्द करा (Cancel)", null)
                .show();
    }

    private void initDependencies() {
        stateMachine = StateMachine.getInstance(getApplicationContext());
        
        // Setup CameraController
        cameraController = CameraController.getInstance(getApplicationContext());
        cameraController.setScanResultCallback(this);
        cameraController.setOverlayView(overlayView);

        // Initialize Orchestrator
        pipeline = new RecognitionPipelineOrchestrator(getApplicationContext());
    }

    private void startCamera() {
        cameraController.setScanResultCallback(this);
        cameraController.setOverlayView(overlayView);
        cameraController.startCamera(
                this,
                cameraPreview
        );
    }

    @Override
    public void onScanCompleted(List<String> normalizedTexts) {
        Log.d("PIPELINE_TRACE", "8. onScanCompleted triggered in HomeActivity. Items: " + (normalizedTexts != null ? normalizedTexts.size() : 0));

        // 🚀 STEP 1: Debounce and Lock Check
        long now = System.currentTimeMillis();
        if (isScanLocked) {
            Log.d("PIPELINE_TRACE", "8. Skipped: Scanner is locked");
            return;
        }
        if (now - lastScanTime < 1500) {
            Log.d("PIPELINE_TRACE", "8. Skipped: Debounce active (" + (now - lastScanTime) + "ms)");
            return;
        }

        // 🚀 Reset idle timer because camera saw something or detection is active
        resetIdleTimer();

        // If we were in IDLE state, transition to READY first (UI wake up)
        if (stateMachine.getCurrentState() == AppState.IDLE) {
            Log.i("PIPELINE_TRACE", "8. Waking up from IDLE");
            stateMachine.transition(StateEvent.ACTIVITY_DETECTED);
        }

        runOnUiThread(() -> {
            // Re-check lock on UI thread to prevent race conditions during navigation
            if (isScanLocked) return;

            Log.d("PIPELINE_TRACE", "9. Resolving medicines...");
            List<ScanResult> results = pipeline.resolve(normalizedTexts);

            if (results == null || results.isEmpty()) {
                LogUtils.w("No scan results produced");
                Log.d("PIPELINE_TRACE", "9. Resolve returned empty list");
                return;
            }

            // 🚀 STEP 2: Lock the scanner
            isScanLocked = true;
            lastScanTime = System.currentTimeMillis();

            // Prefetch audio immediately
            AudioCacheManager cacheManager = AudioCacheManager.getInstance(this);
            for (ScanResult res : results) {
                if (res.medicineId != null && res.audioUrls != null) {
                    for (int i = 0; i < res.audioUrls.size(); i++) {
                        cacheManager.prefetchAudio(res.medicineId, i, res.audioUrls.get(i), null);
                    }
                }
            }

            Log.i("PIPELINE_TRACE", "10. Launching Result screen. Count: " + results.size());
            
            boolean hasKnown = results.stream().anyMatch(r -> r.resultType == ResultType.KNOWN);
            incrementScanCount(hasKnown);
            
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
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        boolean hasKnown = results.stream()
                .anyMatch(r -> r.resultType == ResultType.KNOWN);

        Log.d("PIPELINE_TRACE", "11. State transition. hasKnown: " + hasKnown);
        if (hasKnown) {
            stateMachine.transition(StateEvent.MATCH_FOUND);
        } else {
            stateMachine.transition(StateEvent.MATCH_NOT_FOUND);
        }
    }

    @Override
    public void onStateChanged(AppState state) {
        LogUtils.i("HomeActivity observed state: " + state);
        
        // Reset idle timer whenever we enter an active state
        if (state == AppState.SCANNING || state == AppState.RESULT_AUTO || 
            state == AppState.RESULT_UNKNOWN || state == AppState.READY) {
            runOnUiThread(this::resetIdleTimer);
        }

        // If we just finished an ad, make sure we are in READY state and resume scanning
        if (state == AppState.READY) {
            isScanLocked = false;
        }
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

    private void incrementScanCount(boolean isSuccessful) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String today = getTodayDateString();
        String lastDate = prefs.getString(KEY_LAST_DATE, "");

        int currentCount = prefs.getInt(KEY_SCAN_COUNT, 0);

        if (today.equals(lastDate)) {
            currentCount++;
        } else {
            currentCount = 1;
        }

        SharedPreferences.Editor editor = prefs.edit()
                .putInt(KEY_SCAN_COUNT, currentCount)
                .putString(KEY_LAST_DATE, today);

        if (isSuccessful) {
            int adCounter = prefs.getInt(KEY_SUCCESSFUL_AD_COUNT, 0) + 1;
            Log.d("AD_DEBUG", "Successful scan count: " + adCounter);
            editor.putInt(KEY_SUCCESSFUL_AD_COUNT, adCounter);
        }

        editor.apply();

        displayCurrentScanCount();

        // 🚀 Sync to Firebase in background
        syncScanCountToFirebase(today);
    }

    private void syncScanCountToFirebase(String today) {
        String shopId = getSharedPreferences("kiosk_settings", MODE_PRIVATE)
                .getString("shop_mobile", "910000000000");

        // Use top-level collection for easier dashboard aggregation
        String docId = shopId + "_" + today;
        DocumentReference docRef = FirebaseFirestore.getInstance()
                .collection("daily_scans")
                .document(docId);

        docRef.get().addOnSuccessListener(document -> {
            if (document.exists()) {
                Long current = document.getLong("scanCount");
                long newCount = (current != null ? current : 0) + 1;
                docRef.update("scanCount", newCount,
                        "lastUpdated", FieldValue.serverTimestamp(),
                        "shopId", shopId,
                        "date", today)
                        .addOnFailureListener(e -> LogUtils.e("Firebase scan update failed", e));
            } else {
                Map<String, Object> data = new HashMap<>();
                data.put("scanCount", 1);
                data.put("lastUpdated", FieldValue.serverTimestamp());
                data.put("shopId", shopId);
                data.put("date", today);

                docRef.set(data)
                        .addOnFailureListener(e -> LogUtils.e("Firebase scan set failed", e));
            }
        }).addOnFailureListener(e -> LogUtils.e("Firebase scan fetch failed", e));
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
