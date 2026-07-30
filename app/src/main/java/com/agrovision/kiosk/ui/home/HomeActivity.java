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
import android.view.KeyEvent;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import android.widget.AutoCompleteTextView;
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
import com.agrovision.kiosk.threading.IoExecutor;
import com.agrovision.kiosk.threading.MatchingExecutor;
import com.agrovision.kiosk.threading.RecognitionExecutor;
import com.agrovision.kiosk.ui.ad.AdActivity;
import com.agrovision.kiosk.ui.ad.AdManager;
import com.agrovision.kiosk.ui.result.ResultActivity;
import com.agrovision.kiosk.ui.result.UnknownActivity;
import com.agrovision.kiosk.app.UpdateManager;
import com.agrovision.kiosk.ui.result.model.ResultType;
import com.agrovision.kiosk.ui.result.model.ScanResult;
import com.agrovision.kiosk.util.AudioCacheManager;
import com.agrovision.kiosk.util.LogUtils;
import com.agrovision.kiosk.util.PerformanceProfiler;
import com.agrovision.kiosk.util.SoundManager;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
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
        implements StateObserver, ScanResultCallback, AdManager.OnAdUpdateListener {

    private StateMachine stateMachine;
    private CameraController cameraController;
    private RecognitionPipelineOrchestrator pipeline;

    private PreviewView cameraPreview;
    private BoundingBoxOverlay overlayView;
    private TextView tvDailyScanCount;
    private View progressScanner;
    private View brandingSeparator;
    private View shopBrandingContainer;
    private View shopTextBranding;
    private android.widget.TextView tvShopName;
    private android.widget.ImageView ivShopBrandingPoster;

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
    private volatile boolean isScanLocked = false;
    private long lastScanTime = 0;

    // 🚀 Barcode Scanner Buffer
    private final StringBuilder barcodeBuffer = new StringBuilder();
    private long lastKeyTime = 0;
    private static final long SCANNER_THRESHOLD_MS = 50;

    // Daily Scan Count
    private static final String PREFS_NAME = "scan_stats";
    private static final String KEY_SCAN_COUNT = "scan_count";
    private static final String KEY_LAST_DATE = "last_date";

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

        // 🚀 Initialize Ad Preloading & Branding
        AdManager adManager = AdManager.getInstance(this);
        adManager.addListener(this);
        updateBranding(adManager.getShopBrandingType(), adManager.getShopDisplayName(), adManager.getShopBannerUrl());

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        PerformanceProfiler.log("Return Home", "HomeActivity Visible");
        Log.d("STATE_DEBUG", "HomeActivity resumed. Current state: " + stateMachine.getCurrentState());
        
        // 🚀 Unlock scanning when returning to Home
        isScanLocked = false;
        
        // 🚀 ENSURE DETECTION IS RESUMED after ads or result screens
        cameraController.setDetectionEnabled(true);
        
        // 🚀 CRITICAL FIX: Reset the pipeline lock to prevent deadlocks from previous activities
        cameraController.resetPipeline();
        
        // 🚀 RE-BIND CAMERA: Check permissions first
        checkCameraPermission();

        // Hide progress if it was left visible
        if (progressScanner != null) {
            progressScanner.setVisibility(View.GONE);
        }
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
    }

    private void bindViews() {
        cameraPreview = findViewById(R.id.cameraPreview);
        overlayView = findViewById(R.id.overlayView);
        tvDailyScanCount = findViewById(R.id.tvDailyScanCount);
        progressScanner = findViewById(R.id.progressScanner);
        brandingSeparator = findViewById(R.id.brandingSeparator);
        shopBrandingContainer = findViewById(R.id.shopBrandingContainer);
        shopTextBranding = findViewById(R.id.shopTextBranding);
        tvShopName = findViewById(R.id.tvShopName);
        ivShopBrandingPoster = findViewById(R.id.ivShopBrandingPoster);

        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettingsDialog());
    }

    private void showSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        MaterialSwitch switchVoice = dialogView.findViewById(R.id.switchVoice);
        Slider sliderVolume = dialogView.findViewById(R.id.sliderVolume);
        AutoCompleteTextView spinnerResultTime = dialogView.findViewById(R.id.spinnerResultTime);
        View btnShowAds = dialogView.findViewById(R.id.btnShowAds);

        // Setup Dropdown for Result Time
        String[] options = {"30 Seconds (Default)", "45 Seconds", "60 Seconds"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options);
        spinnerResultTime.setAdapter(adapter);

        SharedPreferences prefs = getSharedPreferences("kiosk_settings", MODE_PRIVATE);
        switchVoice.setChecked(prefs.getBoolean("voice_enabled", true));
        sliderVolume.setValue((float) prefs.getInt("voice_volume", 100));

        int currentTime = prefs.getInt("RESULT_SCREEN_TIME", 30);
        if (currentTime == 45) spinnerResultTime.setText(options[1], false);
        else if (currentTime == 60) spinnerResultTime.setText(options[2], false);
        else spinnerResultTime.setText(options[0], false);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("जतन करा (Save)", (d, which) -> {
                    int selectedTime = 30;
                    String selectedText = spinnerResultTime.getText().toString();
                    if (selectedText.equals(options[1])) selectedTime = 45;
                    else if (selectedText.equals(options[2])) selectedTime = 60;

                    prefs.edit()
                            .putBoolean("voice_enabled", switchVoice.isChecked())
                            .putInt("voice_volume", (int) sliderVolume.getValue())
                            .putInt("RESULT_SCREEN_TIME", selectedTime)
                            .apply();
                })
                .setNegativeButton("रद्द करा (Cancel)", null)
                .create();

        // Style the dialog background to be transparent so our MaterialCard corners show
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnShowAds.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(this, AdActivity.class);
            intent.putExtra(AdActivity.EXTRA_AD_TYPE, AdActivity.AdType.SLIDESHOW);
            startActivity(intent);
        });

        dialog.show();
    }

    private void initDependencies() {
        stateMachine = StateMachine.getInstance(getApplicationContext());
        
        // Setup CameraController
        cameraController = CameraController.getInstance(getApplicationContext());
        cameraController.setScanResultCallback(this);
        cameraController.setOverlayView(overlayView);

        // Initialize Orchestrator
        pipeline = new RecognitionPipelineOrchestrator(getApplicationContext());

        // 🚀 Preload Sounds
        SoundManager.getInstance(this);
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
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            long now = System.currentTimeMillis();
            
            // If it's been too long since the last key, it's probably a human, reset buffer
            if (now - lastKeyTime > SCANNER_THRESHOLD_MS && barcodeBuffer.length() > 0) {
                barcodeBuffer.setLength(0);
            }
            lastKeyTime = now;

            if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                String result = barcodeBuffer.toString().trim();
                if (!result.isEmpty()) {
                    LogUtils.i("Barcode Gun Scan detected: " + result);
                    handleBarcodeScan(result);
                }
                barcodeBuffer.setLength(0);
                return true; // Handled
            }

            char c = (char) event.getUnicodeChar();
            if (c > 31 && c < 127) { // Printable characters
                barcodeBuffer.append(c);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void handleBarcodeScan(String rawInput) {
        synchronized (this) {
            if (isScanLocked) return;
            isScanLocked = true;
        }
        
        // 🚀 STOP CAMERA PROCESSING
        cameraController.resetPipeline();
        
        // 🚀 SHOW PROGRESS UI
        runOnUiThread(() -> {
            if (progressScanner != null) {
                progressScanner.setVisibility(View.VISIBLE);
            }
        });

        // 🚀 ORCHESTRATE BARCODE SCAN
        pipeline.handleBarcodeScan(rawInput, new RecognitionPipelineOrchestrator.BarcodeCallback() {
            @Override
            public void onResult(List<ScanResult> results) {
                runOnUiThread(() -> {
                    if (progressScanner != null) progressScanner.setVisibility(View.GONE);
                    if (!results.isEmpty()) {
                        lastScanTime = System.currentTimeMillis();
                        cameraController.setDetectionEnabled(false);
                        
                        boolean hasKnown = false;
                        for (ScanResult r : results) {
                            if (r.resultType == ResultType.KNOWN) {
                                hasKnown = true;
                                break;
                            }
                        }
                        incrementScanCount(hasKnown);
                        
                        launchResultScreen(results);
                    } else {
                        synchronized (HomeActivity.this) {
                            isScanLocked = false;
                        }
                    }
                });
            }

            @Override
            public void onUnknown(String scrapedName, String rawCode) {
                runOnUiThread(() -> {
                    if (progressScanner != null) progressScanner.setVisibility(View.GONE);
                    lastScanTime = System.currentTimeMillis();
                    cameraController.setDetectionEnabled(false);

                    // 🚀 Play Error Sound
                    SoundManager.getInstance(HomeActivity.this).playError();

                    // Launch Unknown Activity with the scraped name
                    Intent intent = new Intent(HomeActivity.this, UnknownActivity.class);
                    intent.putExtra("SCRAPED_NAME", scrapedName);
                    intent.putExtra("RAW_CODE", rawCode);
                    startActivity(intent);
                });
            }

            @Override
            public void onFailure() {
                runOnUiThread(() -> {
                    if (progressScanner != null) progressScanner.setVisibility(View.GONE);
                    synchronized (HomeActivity.this) {
                        isScanLocked = false;
                    }
                });
            }
        });
    }

    @Override
    public void onScanCompleted(List<String> normalizedTexts) {
        PerformanceProfiler.start("HomeActivity.onScanCompleted");
        Log.d("PIPELINE_TRACE", "8. onScanCompleted triggered in HomeActivity.");

        // 🚀 ATOMIC LOCK CHECK & DEBOUNCE
        synchronized (this) {
            if (isScanLocked) {
                PerformanceProfiler.end("HomeActivity.onScanCompleted");
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastScanTime < 2500) {
                PerformanceProfiler.end("HomeActivity.onScanCompleted");
                return;
            }

            isScanLocked = true;
        }

        // 🚀 PERFORM HEAVY RESOLUTION ON BACKGROUND THREAD
        MatchingExecutor.submit(() -> {
            Log.d("PIPELINE_TRACE", "9. Resolving medicines on background thread...");
            List<ScanResult> results = pipeline.resolve(normalizedTexts);

            if (results == null || results.isEmpty()) {
                LogUtils.w("No scan results produced");
                synchronized (this) {
                    isScanLocked = false;
                }
                PerformanceProfiler.end("HomeActivity.onScanCompleted");
                return;
            }

            runOnUiThread(() -> {
                // 🚀 STOP FURTHER PIPELINE PROCESSING only after we have a valid result
                cameraController.resetPipeline();

                // Update timestamp only after successful resolution
                lastScanTime = System.currentTimeMillis();

                if (stateMachine.getCurrentState() == AppState.IDLE) {
                    stateMachine.transition(StateEvent.ACTIVITY_DETECTED);
                }

                // 🚀 HARD PAUSE CAMERA
                cameraController.setDetectionEnabled(false);

                // Prefetch audio immediately (IO task)
                IoExecutor.submit(() -> {
                    AudioCacheManager cacheManager = AudioCacheManager.getInstance(this);
                    for (ScanResult res : results) {
                        if (res.medicineId != null && res.audioUrls != null) {
                            for (int i = 0; i < res.audioUrls.size(); i++) {
                                cacheManager.prefetchAudio(res.medicineId, i, res.audioUrls.get(i), null);
                            }
                        }
                    }
                });

                Log.i("PIPELINE_TRACE", "10. Launching Result screen.");
                
                boolean hasKnown = false;
                for (ScanResult r : results) {
                    if (r.resultType == ResultType.KNOWN) {
                        hasKnown = true;
                        break;
                    }
                }
                incrementScanCount(hasKnown);
                
                PerformanceProfiler.end("HomeActivity.onScanCompleted");
                launchResultScreen(results);
            });
        });
    }

    private void launchResultScreen(List<ScanResult> results) {
        PerformanceProfiler.start("launchResultScreen");
        PerformanceProfiler.log("launchResultScreen", "Method Enter");
        Intent intent = new Intent(this, ResultActivity.class);
        PerformanceProfiler.log("launchResultScreen", "Intent Created");
        intent.putParcelableArrayListExtra(
                ResultActivity.EXTRA_SCAN_RESULTS,
                new ArrayList<>(results)
        );
        PerformanceProfiler.log("launchResultScreen", "Intent Extras Prepared");

        startActivity(intent);
        PerformanceProfiler.log("launchResultScreen", "startActivity() called");
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        boolean hasKnown = results.stream()
                .anyMatch(r -> r.resultType == ResultType.KNOWN);

        // 🚀 Play appropriate sound
        if (hasKnown) {
            SoundManager.getInstance(this).playSuccess();
        } else {
            SoundManager.getInstance(this).playError();
        }

        Log.d("PIPELINE_TRACE", "11. State transition. hasKnown: " + hasKnown);
        if (hasKnown) {
            stateMachine.transition(StateEvent.MATCH_FOUND);
        } else {
            stateMachine.transition(StateEvent.MATCH_NOT_FOUND);
        }
        PerformanceProfiler.log("launchResultScreen", "Method Exit");
        PerformanceProfiler.end("launchResultScreen");
    }

    @Override
    public void onStateChanged(AppState state) {
        LogUtils.i("HomeActivity observed state: " + state);
        
        // If we just finished an ad, make sure we are in READY state and resume scanning
        if (state == AppState.READY) {
            synchronized (this) {
                isScanLocked = false;
            }
        }
    }

    /* =========================================================
       SHOP BANNER & AD CALLBACKS
       ========================================================= */

    @Override
    public void onAdsUpdated(List<AdManager.AdModel> ads, long intervalMs, String version) {
        // Not used on HomeActivity for now
    }

    @Override
    public void onShopBannerUpdated(String bannerUrl) {
        // Handled by onBrandingUpdated for the header
    }

    @Override
    public void onBrandingUpdated(String type, String name, String url) {
        runOnUiThread(() -> updateBranding(type, name, url));
    }

    private void updateBranding(String type, String name, String url) {
        if (shopBrandingContainer == null) return;

        boolean hasBranding = (type != null && ((type.equals("text") && name != null && !name.isEmpty()) || (type.equals("poster") && url != null && !url.isEmpty())));

        if (!hasBranding) {
            shopBrandingContainer.setVisibility(View.GONE);
            brandingSeparator.setVisibility(View.GONE);
            return;
        }

        shopBrandingContainer.setVisibility(View.VISIBLE);
        brandingSeparator.setVisibility(View.VISIBLE);

        if ("poster".equals(type)) {
            shopTextBranding.setVisibility(View.GONE);
            ivShopBrandingPoster.setVisibility(View.VISIBLE);
            com.bumptech.glide.Glide.with(this)
                    .load(url)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .into(ivShopBrandingPoster);
        } else {
            ivShopBrandingPoster.setVisibility(View.GONE);
            shopTextBranding.setVisibility(View.VISIBLE);
            
            if (name != null) {
                SpannableString spannable = new SpannableString(name);
                int firstSpace = name.indexOf(" ");
                if (firstSpace > 0) {
                    // First word Green (#006400), subsequent words Orange (#FF8C00)
                    spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#006400")), 0, firstSpace, 0);
                    spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#FF8C00")), firstSpace, name.length(), 0);
                } else {
                    spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#006400")), 0, name.length(), 0);
                }
                tvShopName.setText(spannable);
            }
        }
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

        // 🚀 OPTIMIZATION: Use FieldValue.increment to avoid unnecessary READ calls.
        // This reduces Firestore cost by 50% for this operation.
        Map<String, Object> data = new HashMap<>();
        data.put("scanCount", FieldValue.increment(1));
        data.put("lastUpdated", FieldValue.serverTimestamp());
        data.put("shopId", shopId);
        data.put("date", today);

        docRef.set(data, SetOptions.merge())
                .addOnFailureListener(e -> LogUtils.e("Firebase scan sync failed", e));
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SoundManager.getInstance(this).release();
    }

    private String getTodayDateString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
