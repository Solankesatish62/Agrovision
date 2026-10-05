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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.widget.AutoCompleteTextView;
import android.view.ViewGroup;
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
import com.agrovision.kiosk.threading.MatchingExecutor;
import com.agrovision.kiosk.ui.ad.AdActivity;
import com.agrovision.kiosk.ui.ad.AdManager;
import com.agrovision.kiosk.ui.splash.SplashActivity;
import com.agrovision.kiosk.ui.result.ResultActivity;
import com.agrovision.kiosk.app.UpdateManager;
import com.agrovision.kiosk.data.model.Medicine;
import com.agrovision.kiosk.data.repository.MedicineRepository;
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
    private AutoCompleteTextView etSearchMedicine;
    private View btnClearSearch;

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
        
        setupSearchListener();
        setupSearchAutocomplete();

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

        // 🚀 Security Lock: Ensure kiosk is still paired
        SharedPreferences prefs = getSharedPreferences("kiosk_settings", MODE_PRIVATE);
        if (!prefs.getBoolean("is_registered", false) || prefs.getString("shop_id", null) == null) {
            LogUtils.w("Pairing lost or missing. Redirecting to splash.");
            Intent intent = new Intent(this, SplashActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

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
        etSearchMedicine = findViewById(R.id.etSearchMedicine);
        btnClearSearch = findViewById(R.id.btnClearSearch);

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                if (etSearchMedicine != null) {
                    etSearchMedicine.setText("");
                    etSearchMedicine.requestFocus();
                }
            });
        }

        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettingsDialog());
    }

    private void setupSearchAutocomplete() {
        if (etSearchMedicine == null) return;
        
        // Custom Adapter to show rich medicine info (Name, Chemical, Company)
        class MedicineAutocompleteAdapter extends android.widget.BaseAdapter implements android.widget.Filterable {
            private final List<Medicine> fullList;
            private List<Medicine> filteredList;
            
            MedicineAutocompleteAdapter(List<Medicine> list) {
                this.fullList = new ArrayList<>(list);
                this.filteredList = new ArrayList<>(list);
            }

            @Override public int getCount() { return filteredList.size(); }
            @Override public Medicine getItem(int position) { return filteredList.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.item_medicine_search, parent, false);
                }
                
                Medicine med = getItem(position);
                TextView tvName = convertView.findViewById(R.id.tvMedicineName);
                TextView tvChemical = convertView.findViewById(R.id.tvChemicalName);
                TextView tvCompany = convertView.findViewById(R.id.tvCompanyName);
                
                tvName.setText(med.getName());
                
                if (med.getChemicalName() != null && !med.getChemicalName().isEmpty()) {
                    tvChemical.setText(med.getChemicalName());
                    tvChemical.setVisibility(View.VISIBLE);
                } else {
                    tvChemical.setVisibility(View.GONE);
                }
                
                if (med.getCompany() != null && !med.getCompany().isEmpty()) {
                    tvCompany.setText(med.getCompany());
                    tvCompany.setVisibility(View.VISIBLE);
                } else {
                    tvCompany.setVisibility(View.GONE);
                }
                
                return convertView;
            }

            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        List<Medicine> suggestions;
                        
                        if (constraint == null || constraint.length() == 0) {
                            suggestions = new ArrayList<>(fullList);
                        } else {
                            suggestions = com.agrovision.kiosk.data.service.MedicineSearchService.search(constraint.toString(), fullList);
                        }
                        
                        results.values = suggestions;
                        results.count = suggestions.size();
                        return results;
                    }

                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        filteredList = (List<Medicine>) results.values;
                        notifyDataSetChanged();
                    }

                    @Override
                    public CharSequence convertResultToString(Object resultValue) {
                        return ((Medicine) resultValue).getName();
                    }
                };
            }
        }

        // Listen for catalog updates
        MedicineRepository.getInstance(this).addOnCatalogUpdateListener(newCatalog -> {
            if (newCatalog == null) return;
            runOnUiThread(() -> {
                MedicineAutocompleteAdapter adapter = new MedicineAutocompleteAdapter(newCatalog);
                etSearchMedicine.setAdapter(adapter);
            });
        });

        // Initial populate
        List<Medicine> initial = MedicineRepository.getInstance(this).getAll();
        if (!initial.isEmpty()) {
            MedicineAutocompleteAdapter adapter = new MedicineAutocompleteAdapter(initial);
            etSearchMedicine.setAdapter(adapter);
        }
        
        etSearchMedicine.setOnItemClickListener((parent, view, position, id) -> {
            Medicine selected = (Medicine) parent.getItemAtPosition(position);
            handleManualSearch(selected.getName());
            etSearchMedicine.setText("");
            hideKeyboard(etSearchMedicine);
            etSearchMedicine.clearFocus();
        });
    }

    private void setupSearchListener() {
        if (etSearchMedicine == null) return;

        etSearchMedicine.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (btnClearSearch != null) {
                    btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                }
            }
        });

        etSearchMedicine.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                
                String query = etSearchMedicine.getText().toString().trim();
                if (!query.isEmpty()) {
                    handleManualSearch(query);
                    // Clear search and hide keyboard
                    etSearchMedicine.setText("");
                    hideKeyboard(v);
                }
                return true;
            }
            return false;
        });
    }

    private void hideKeyboard(View view) {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void handleManualSearch(String query) {
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

        // 🚀 PERFORM SEARCH ON BACKGROUND THREAD
        MatchingExecutor.submit(() -> {
            Log.d("SEARCH_TRACE", "Manual search for: " + query);
            
            List<Medicine> all = MedicineRepository.getInstance(this).getAll();
            List<Medicine> searchResults = com.agrovision.kiosk.data.service.MedicineSearchService.search(query, all);
            
            final Medicine bestMatch = searchResults.isEmpty() ? null : searchResults.get(0);
            final boolean finalAmbiguous = searchResults.size() > 1;

            runOnUiThread(() -> {
                if (progressScanner != null) progressScanner.setVisibility(View.GONE);

                if (finalAmbiguous && bestMatch != null) {
                    // Check if it's an exact match vs multiple possibilities
                    boolean isExact = bestMatch.getName().equalsIgnoreCase(query.trim());
                    
                    if (!isExact) {
                        // Multiple options found, don't open result screen automatically
                        SoundManager.getInstance(HomeActivity.this).playError();
                        Toast.makeText(HomeActivity.this, "एकापेक्षा जास्त औषधे सापडली, कृपया यादीतून निवडा (Multiple matches, please select one)", Toast.LENGTH_SHORT).show();
                        etSearchMedicine.showDropDown();
                        synchronized (HomeActivity.this) {
                            isScanLocked = false;
                        }
                        return;
                    }
                }

                if (bestMatch != null) {
                    // Found a single clear match or an exact match
                    List<ScanResult> results = new ArrayList<>();
                    results.add(new ScanResult(
                        ResultType.KNOWN,
                        bestMatch.getId(),
                        bestMatch.getName(),
                        bestMatch.getCompany(),
                        bestMatch.getChemicalName(),
                        bestMatch.getImageUrls(),
                        bestMatch.getAudioUrls(),
                        com.agrovision.kiosk.ui.result.mapper.ResultInfoMapper.fromMedicine(bestMatch),
                        query,
                        false
                    ));
                    
                    lastScanTime = System.currentTimeMillis();
                    cameraController.setDetectionEnabled(false);
                    
                    // Clear focus and hide keyboard
                    etSearchMedicine.clearFocus();
                    
                    incrementScanCount(results);
                    launchResultScreen(results);
                } else {
                    // No match found at all
                    SoundManager.getInstance(HomeActivity.this).playError();
                    Toast.makeText(HomeActivity.this, "माहिती सापडली नाही (Medicine Not Found)", Toast.LENGTH_SHORT).show();
                    
                    synchronized (HomeActivity.this) {
                        isScanLocked = false;
                    }
                }
            });
        });
    }

    private void showSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        com.google.android.material.materialswitch.MaterialSwitch switchVoice = dialogView.findViewById(R.id.switchVoice);
        com.google.android.material.slider.Slider sliderVolume = dialogView.findViewById(R.id.sliderVolume);
        AutoCompleteTextView spinnerResultTime = dialogView.findViewById(R.id.spinnerResultTime);
        View btnShowAds = dialogView.findViewById(R.id.btnShowAds);

        // Setup Dropdown for Result Time
        String[] options = {"30 Seconds (Default)", "45 Seconds", "60 Seconds"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options);
        spinnerResultTime.setAdapter(adapter);

        SharedPreferences settingsPrefs = getSharedPreferences("kiosk_settings", MODE_PRIVATE);
        switchVoice.setChecked(settingsPrefs.getBoolean("voice_enabled", true));
        sliderVolume.setValue((float) settingsPrefs.getInt("voice_volume", 100));

        int currentTime = settingsPrefs.getInt("RESULT_SCREEN_TIME", 30);
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

                    getSharedPreferences("kiosk_settings", MODE_PRIVATE).edit()
                            .putBoolean("voice_enabled", ((com.google.android.material.materialswitch.MaterialSwitch) dialogView.findViewById(R.id.switchVoice)).isChecked())
                            .putInt("voice_volume", (int) ((com.google.android.material.slider.Slider) dialogView.findViewById(R.id.sliderVolume)).getValue())
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
        // 🚀 If search bar is focused, let it handle the keys
        if (etSearchMedicine != null && etSearchMedicine.hasFocus()) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                // Handling back event via callback is recommended, but for focus clearing this is fine.
                // We'll keep it as is but fix the redundant check if needed.
                etSearchMedicine.clearFocus();
                hideKeyboard(etSearchMedicine);
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            long now = System.currentTimeMillis();
            long delta = now - lastKeyTime;
            lastKeyTime = now;

            int keyCode = event.getKeyCode();

            // 🚀 Handle 'Shift + A' key for manual advertisement trigger
            if (keyCode == KeyEvent.KEYCODE_A && event.isShiftPressed()) {
                LogUtils.i("Manual Ad trigger requested via keyboard shortcut 'Shift+A'");
                launchAdScreen();
                return true;
            }

            // 🚀 Support keyboard arrows to view previous results from Home
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                
                LogUtils.i("Arrow key pressed on Home - opening history");
                Intent intent = new Intent(this, ResultActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
            }

            // 🚀 Handle ENTER key
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                String result = barcodeBuffer.toString().trim();
                barcodeBuffer.setLength(0);
                if (!result.isEmpty()) {
                    if (delta < SCANNER_THRESHOLD_MS) {
                        handleBarcodeScan(result);
                    } else {
                        handleManualSearch(result);
                    }
                }
                return true;
            }

            // 🚀 Handle printable characters
            char c = (char) event.getUnicodeChar();
            if (c > 31 && c < 127) {
                // If it's a human typing (not a burst from scanner)
                if (delta > SCANNER_THRESHOLD_MS) {
                    etSearchMedicine.requestFocus();
                    etSearchMedicine.setText(String.valueOf(c));
                    etSearchMedicine.setSelection(1);
                    
                    // Force dropdown to show after focus
                    etSearchMedicine.postDelayed(() -> {
                        if (etSearchMedicine.hasFocus()) {
                            etSearchMedicine.showDropDown();
                        }
                    }, 100);

                    // Clear anything the barcode buffer might have caught erroneously
                    barcodeBuffer.setLength(0);
                    return true;
                }
                
                barcodeBuffer.append(c);
                return true;
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
                        // Filter only KNOWN results
                        List<ScanResult> knownResults = new ArrayList<>();
                        for (ScanResult r : results) {
                            if (r.resultType == ResultType.KNOWN) {
                                knownResults.add(r);
                            }
                        }

                        if (!knownResults.isEmpty()) {
                            lastScanTime = System.currentTimeMillis();
                            cameraController.setDetectionEnabled(false);
                            
                            incrementScanCount(knownResults);
                            launchResultScreen(knownResults);
                        } else {
                            // If all are unknown, just reset and continue
                            synchronized (HomeActivity.this) {
                                isScanLocked = false;
                            }
                            cameraController.setDetectionEnabled(true);
                        }
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
                    
                    // 🚀 DON'T show Unknown Screen anymore
                    LogUtils.w("Barcode: Unknown detected (" + scrapedName + ") - suppressing screen");
                    
                    synchronized (HomeActivity.this) {
                        isScanLocked = false;
                    }
                    cameraController.setDetectionEnabled(true);
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

            if (results != null && !results.isEmpty()) {
                runOnUiThread(() -> {
                    // 🚀 STOP FURTHER PIPELINE PROCESSING only after we have a valid result
                    cameraController.resetPipeline();

                    // 🚀 Filter only KNOWN results
                    List<ScanResult> knownResults = new ArrayList<>();
                    for (ScanResult r : results) {
                        if (r.resultType == ResultType.KNOWN) {
                            knownResults.add(r);
                        }
                    }

                    if (knownResults.isEmpty()) {
                        LogUtils.d("OCR: No known medicines found in " + results.size() + " detections - suppressing");
                        synchronized (this) {
                            isScanLocked = false;
                        }
                        cameraController.setDetectionEnabled(true);
                        PerformanceProfiler.end("HomeActivity.onScanCompleted");
                        return;
                    }

                    // Update timestamp only after successful resolution
                    lastScanTime = System.currentTimeMillis();

                    if (stateMachine.getCurrentState() == AppState.IDLE) {
                        stateMachine.transition(StateEvent.ACTIVITY_DETECTED);
                    }

                    // 🚀 HARD PAUSE CAMERA
                    cameraController.setDetectionEnabled(false);

                    // Prefetch audio immediately (IO task)
                    com.agrovision.kiosk.threading.IoExecutor.submit(() -> {
                        AudioCacheManager cacheManager = AudioCacheManager.getInstance(this);
                        for (ScanResult res : knownResults) {
                            if (res.medicineId != null && res.audioUrls != null) {
                                for (int i = 0; i < res.audioUrls.size(); i++) {
                                    cacheManager.prefetchAudio(res.medicineId, i, res.audioUrls.get(i), null);
                                }
                            }
                        }
                    });

                    Log.i("PIPELINE_TRACE", "10. Launching Result screen with " + knownResults.size() + " items.");
                    
                    incrementScanCount(knownResults);
                    
                    PerformanceProfiler.end("HomeActivity.onScanCompleted");
                    launchResultScreen(knownResults);
                });
            } else {
                LogUtils.w("No scan results produced");
                synchronized (this) {
                    isScanLocked = false;
                }
                PerformanceProfiler.end("HomeActivity.onScanCompleted");
            }
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
        
        if (state == AppState.IDLE_AD || state == AppState.SCAN_AD) {
            launchAdScreen();
        }

        // If we just finished an ad, make sure we are in READY state and resume scanning
        if (state == AppState.READY) {
            synchronized (this) {
                isScanLocked = false;
            }
            cameraController.setDetectionEnabled(true);
        }
    }

    private void launchAdScreen() {
        Intent intent = new Intent(this, AdActivity.class);
        intent.putExtra(AdActivity.EXTRA_AD_TYPE, AdActivity.AdType.SLIDESHOW);
        startActivity(intent);
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

    /* =========================================================
       DAILY SCAN COUNT LOGIC
       ========================================================= */

    private void incrementScanCount(List<ScanResult> results) {
        if (results == null || results.isEmpty()) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String today = getTodayDateString();
        String lastDate = prefs.getString(KEY_LAST_DATE, "");

        int currentCount = prefs.getInt(KEY_SCAN_COUNT, 0);

        if (today.equals(lastDate)) {
            currentCount += results.size();
        } else {
            currentCount = results.size();
        }

        SharedPreferences.Editor editor = prefs.edit()
                .putInt(KEY_SCAN_COUNT, currentCount)
                .putString(KEY_LAST_DATE, today);

        editor.apply();

        displayCurrentScanCount();

        // 🚀 Sync to Firebase in background
        syncScanCountToFirebase(today, results);
    }

    private void syncScanCountToFirebase(String today, List<ScanResult> results) {
        SharedPreferences prefs = getSharedPreferences("kiosk_settings", MODE_PRIVATE);
        String shopId = prefs.getString("shop_id", prefs.getString("shop_mobile", "910000000000"));
        String shopName = prefs.getString("shop_name", "Unknown Shop");

        // 1. Group results by medicineId for batch incrementing
        Map<String, Integer> medicineCounts = new HashMap<>();
        Map<String, String> medicineNames = new HashMap<>();
        for (ScanResult res : results) {
            if (res.medicineId != null) {
                String safeId = res.medicineId.replace(".", "_");
                medicineCounts.put(safeId, medicineCounts.getOrDefault(safeId, 0) + 1);
                medicineNames.put(safeId, res.displayName);
            }
        }

        // 1. Daily Scan Log
        String docId = shopId + "_" + today;
        DocumentReference dailyDocRef = FirebaseFirestore.getInstance()
                .collection("daily_scans")
                .document(docId);

        Map<String, Object> dailyUpdates = new HashMap<>();
        dailyUpdates.put("scanCount", FieldValue.increment(results.size()));
        dailyUpdates.put("lastUpdated", FieldValue.serverTimestamp());
        dailyUpdates.put("shopId", shopId);
        dailyUpdates.put("shopName", shopName);
        dailyUpdates.put("date", today);

        // Add medicine-specific counts with safe incrementing
        for (Map.Entry<String, Integer> entry : medicineCounts.entrySet()) {
            String key = "medicines." + entry.getKey();
            dailyUpdates.put(key + ".count", FieldValue.increment(entry.getValue()));
            dailyUpdates.put(key + ".name", medicineNames.get(entry.getKey()));
        }

        // Use set with merge to ensure document exists, but dots in keys only work with update() 
        // for nesting. So we ensure it exists first, then update.
        dailyDocRef.set(new HashMap<>(), SetOptions.merge())
                .addOnSuccessListener(aVoid -> dailyDocRef.update(dailyUpdates));

        // 2. Monthly Global Stats (Aggregation for Analytics)
        String monthId = today.substring(0, 7); // yyyy-MM
        DocumentReference monthlyDocRef = FirebaseFirestore.getInstance()
                .collection("monthly_stats")
                .document(monthId);

        Map<String, Object> monthlyUpdates = new HashMap<>();
        monthlyUpdates.put("totalScans", FieldValue.increment(results.size()));
        monthlyUpdates.put("lastUpdated", FieldValue.serverTimestamp());
        monthlyUpdates.put("month", monthId);

        // Track shop performance
        monthlyUpdates.put("shops." + shopId + ".count", FieldValue.increment(results.size()));
        monthlyUpdates.put("shops." + shopId + ".name", shopName);

        // Track top medicines globally
        for (Map.Entry<String, Integer> entry : medicineCounts.entrySet()) {
            String medId = entry.getKey();
            int count = entry.getValue();
            String medName = medicineNames.get(medId);

            // Global totals
            monthlyUpdates.put("medicines." + medId + ".count", FieldValue.increment(count));
            monthlyUpdates.put("medicines." + medId + ".name", medName);
            
            // Shop-specific totals for this month
            monthlyUpdates.put("shops." + shopId + ".medicines." + medId + ".count", FieldValue.increment(count));
            monthlyUpdates.put("shops." + shopId + ".medicines." + medId + ".name", medName);
        }

        monthlyDocRef.set(new HashMap<>(), SetOptions.merge())
                .addOnSuccessListener(aVoid -> monthlyDocRef.update(monthlyUpdates))
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
