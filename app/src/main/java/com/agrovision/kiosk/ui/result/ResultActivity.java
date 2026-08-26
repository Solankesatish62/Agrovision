package com.agrovision.kiosk.ui.result;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.agrovision.kiosk.R;
import com.agrovision.kiosk.camera.CameraController;
import com.agrovision.kiosk.state.AppState;
import com.agrovision.kiosk.state.StateEvent;
import com.agrovision.kiosk.state.StateMachine;
import com.agrovision.kiosk.state.StateObserver;
import com.agrovision.kiosk.ui.ad.AdActivity;
import com.agrovision.kiosk.ui.home.HomeActivity;
import com.agrovision.kiosk.ui.result.adapter.ResultInfoAdapter;
import com.agrovision.kiosk.ui.result.model.ResultType;
import com.agrovision.kiosk.ui.result.model.ScanResult;
import com.agrovision.kiosk.util.AudioCacheManager;
import com.agrovision.kiosk.util.LogUtils;
import com.agrovision.kiosk.util.PerformanceProfiler;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ResultActivity
 *
 * Responsibility: UI controller for identifying medicines.
 * Implements navigation between multiple results and auto-return functionality.
 */
public final class ResultActivity extends AppCompatActivity
        implements StateObserver {

    private static final String TAG = "ResultActivity";

    public static final String EXTRA_SCAN_RESULTS =
            "com.agrovision.kiosk.EXTRA_SCAN_RESULTS";

    private static final long AUTO_ROTATE_MS = 60_000;
    private static final long UNKNOWN_TIMEOUT_MS = 3_000;

    // Persistent history of last 10 unique medicines
    private static final List<ScanResult> HISTORY = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 10;

    // UI controls
    private ImageButton btnNext;
    private ImageButton btnPrev;
    private ImageButton btnMute;
    private MaterialButton btnNewScan;
    private FloatingActionButton btnPause;
    private ViewPager2 imagePager;
    private RecyclerView infoList;

    // Renderer
    private ResultRenderer renderer;

    // State
    private List<ScanResult> scanResults;
    private int currentScanCount = 0; // 🚀 Keep track of items from the ACTUAL current scan
    private int currentIndex = 0;
    private boolean isPaused = false;

    // Medicine Rotation Timer (Switching between medicines)
    private final Handler timerHandler = new Handler(Looper.getMainLooper());

    // Audio Player
    private MediaPlayer mediaPlayer;
    private int currentAudioIndex = 0;
    private boolean isAudioPlaying = false;
    private int lastPlayedIndex = -1;

    private final Runnable autoRotateRunnable = () -> {
        LogUtils.d("Timer triggered: automatically showing next result");
        // 🚀 BUG FIX: Auto-timer should only cycle through CURRENT scan results, then exit.
        // It should NOT automatically move into history (previous scans).
        if (currentIndex + 1 < currentScanCount) {
            showNextResult();
        } else {
            LogUtils.i("End of current scan results reached. Returning to home.");
            returnToScan();
        }
    };

    private final Runnable unknownTimeoutRunnable = () -> {
        LogUtils.i("Unknown result timeout: returning to scan");
        returnToScan();
    };

    // Image Rotation System (Rotating images of the current medicine)
    private int currentImageIndex = 0;
    private List<String> imageUrls;
    private final Handler imageRotationHandler = new Handler(Looper.getMainLooper());
    private Runnable imageSwitcher;

    public ResultActivity() {
        super();
        PerformanceProfiler.start("ResultActivity Startup");
        PerformanceProfiler.log("ResultActivity Startup", "Activity Constructor");
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        PerformanceProfiler.log("ResultActivity Startup", "onCreate() START");
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        PerformanceProfiler.log("ResultActivity Startup", "setContentView() START");
        setContentView(R.layout.activity_result);
        PerformanceProfiler.log("ResultActivity Startup", "setContentView() END");

        blockBackNavigation();
        hideSystemUI();

        // 🚀 DISABLE DETECTION while result is showing
        CameraController.getInstance(this).setDetectionEnabled(false);

        PerformanceProfiler.log("ResultActivity Startup", "bindViews() START");
        bindControls();
        PerformanceProfiler.log("ResultActivity Startup", "bindViews() END");
        
        PerformanceProfiler.log("ResultActivity Startup", "initRenderer() START");
        initRenderer();
        PerformanceProfiler.log("ResultActivity Startup", "initRenderer() END");
        
        loadResults();

        if (scanResults == null || scanResults.isEmpty()) {
            LogUtils.i("No results available for navigation");
            returnToScan();
            return;
        }

        setupControls();
        
        PerformanceProfiler.log("ResultActivity Startup", "Data Binding & First Render START");
        renderCurrent();
        PerformanceProfiler.log("ResultActivity Startup", "Data Binding & First Render END");
        
        PerformanceProfiler.end("ResultActivity Startup");
        
        // Setup window focus listener for "Fully Visible"
        getWindow().getDecorView().getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
            if (hasFocus) {
                PerformanceProfiler.log("ResultActivity Startup", "Window Focus Gained - UI Ready");
                PerformanceProfiler.printSummary();
            }
        });
        
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            PerformanceProfiler.log("ResultActivity Startup", "First Layout");
        });
        
        PerformanceProfiler.log("ResultActivity Startup", "onCreate() END");
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
        stopTimer();
        stopImageRotation();
        stopAudio();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        // 🚀 Optimization: Only render if we don't have results yet
        // otherwise let the state machine or navigation handle it
        if (scanResults != null && !scanResults.isEmpty()) {
            renderCurrent();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        LogUtils.i("Received new intent in ResultActivity - updating results");
        stopAudio();
        loadResults();
        renderCurrent();
    }

    private void bindControls() {
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrev);
        btnMute = findViewById(R.id.btnMute);
        btnNewScan = findViewById(R.id.btnNewScan);
        btnPause = findViewById(R.id.btnPause);
        imagePager = findViewById(R.id.imagePager);
    }

    private void initRenderer() {
        View standardLayout = findViewById(R.id.standardResultLayout);
        View unknownLayout = findViewById(R.id.unknownResultLayout);
        TextView tvMedicineName = findViewById(R.id.tvMedicineName);
        TextView tvManufacturer = findViewById(R.id.tvManufacturer);
        TextView tvChemicalComposition = findViewById(R.id.tvChemicalComposition);
        infoList = findViewById(R.id.infoList);
        TextView tvUnknownHeader = findViewById(R.id.tvUnknownHeader);

        renderer = new ResultRenderer(
                standardLayout, unknownLayout, tvMedicineName,
                tvManufacturer, tvChemicalComposition,
                imagePager, infoList, tvUnknownHeader
        );

        if (imagePager != null) {
            // Smooth fade transition between images
            imagePager.setPageTransformer((page, position) -> {
                page.setAlpha(0.5f + (1 - Math.abs(position)) * 0.5f);
            });

            imagePager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    currentImageIndex = position;
                }
            });
        }
    }

    private void loadResults() {
        ArrayList<ScanResult> currentScan = getIntent().getParcelableArrayListExtra(EXTRA_SCAN_RESULTS);

        // 🚀 RESET indices when loading new results
        lastPlayedIndex = -1;
        currentAudioIndex = 0;

        if (currentScan != null) {
            // Update history with KNOWN medicines from current scan (in reverse to maintain order when adding at 0)
            for (int i = currentScan.size() - 1; i >= 0; i--) {
                ScanResult res = currentScan.get(i);
                if (res.resultType == ResultType.KNOWN) {
                    // Remove existing entry for same medicine to move it to the front
                    HISTORY.removeIf(h -> h.medicineId != null && h.medicineId.equals(res.medicineId));
                    HISTORY.add(0, res);
                }
            }

            // Keep history to 10
            while (HISTORY.size() > MAX_HISTORY_SIZE) {
                HISTORY.remove(HISTORY.size() - 1);
            }
        }

        // Build the list to display:
        // 1. Current scan results (including UNKNOWN)
        // 2. History of previous scans (excluding those already in current scan)
        List<ScanResult> displayList = new ArrayList<>();
        if (currentScan != null) {
            displayList.addAll(currentScan);
            currentScanCount = currentScan.size();
        } else {
            currentScanCount = 0;
        }

        for (ScanResult hist : HISTORY) {
            boolean alreadyInScan = false;
            if (currentScan != null) {
                for (ScanResult curr : currentScan) {
                    if (curr.medicineId != null && curr.medicineId.equals(hist.medicineId)) {
                        alreadyInScan = true;
                        break;
                    }
                }
            }
            if (!alreadyInScan) {
                displayList.add(hist);
            }
        }

        // Final limit to 10 items total
        if (displayList.size() > MAX_HISTORY_SIZE) {
            scanResults = new ArrayList<>(displayList.subList(0, MAX_HISTORY_SIZE));
        } else {
            scanResults = displayList;
        }

        currentIndex = 0;
    }

    private void returnToScan() {
        PerformanceProfiler.start("Return Home");
        PerformanceProfiler.log("Return Home", "Auto Return Started");
        LogUtils.i("Executing deterministic navigation to HomeActivity");

        stopTimer();
        stopImageRotation();
        stopAudio();

        StateMachine.getInstance(getApplicationContext())
                .transition(StateEvent.RESULT_TIMEOUT);

        Intent intent = new Intent(ResultActivity.this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        PerformanceProfiler.log("Return Home", "ResultActivity Finish");
        PerformanceProfiler.end("Return Home");
        finish();
    }

    private void setupControls() {
        btnNext.setOnClickListener(v -> {
            LogUtils.i("Next button clicked");
            showNextResult();
        });

        btnPrev.setOnClickListener(v -> {
            LogUtils.i("Previous button clicked");
            showPreviousResult();
        });

        btnPause.setOnClickListener(v -> {
            LogUtils.i("Pause button clicked");
            AppState currentState = StateMachine.getInstance(getApplicationContext()).getCurrentState();
            if (currentState == AppState.RESULT_PAUSED) {
                LogUtils.d("Requesting RESUME");
                StateMachine.getInstance(getApplicationContext()).transition(StateEvent.RESUME_REQUESTED);
            } else {
                LogUtils.d("Requesting PAUSE");
                StateMachine.getInstance(getApplicationContext()).transition(StateEvent.PAUSE_REQUESTED);
            }
        });

        if (btnMute != null) {
            btnMute.setOnClickListener(v -> {
                boolean currentlyEnabled = getSharedPreferences("kiosk_settings", MODE_PRIVATE)
                        .getBoolean("voice_enabled", true);
                getSharedPreferences("kiosk_settings", MODE_PRIVATE)
                        .edit().putBoolean("voice_enabled", !currentlyEnabled).apply();
                
                if (currentlyEnabled) {
                    stopAudio();
                    btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode);
                } else {
                    btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
                    lastPlayedIndex = -1; // Reset to allow replay
                    renderCurrent(); // Restart audio
                }
            });
        }

        btnNewScan.setOnClickListener(v -> {
            LogUtils.i("New scan button clicked");
            stopTimer();
            stopImageRotation();
            StateMachine.getInstance(getApplicationContext())
                    .transition(StateEvent.NEW_SCAN_REQUESTED);
            returnToScan();
        });
    }

    private void renderCurrent() {
        LogUtils.i("AUDIO_DEBUG [" + System.identityHashCode(this) + "]: renderCurrent() START. currentIndex=" + currentIndex + ", lastPlayedIndex=" + lastPlayedIndex);
        if (scanResults == null || scanResults.isEmpty()) {
            LogUtils.i("No results available for navigation");
            return;
        }

        ScanResult current = scanResults.get(currentIndex);
        renderer.render(current);

        // Audio playback - only trigger if medicine changed
        String currentId = current.medicineId != null ? current.medicineId : "unknown_" + currentIndex;
        String lastId = (lastPlayedIndex != -1 && lastPlayedIndex < scanResults.size())
                ? scanResults.get(lastPlayedIndex).medicineId : null;
        if (lastId == null && lastPlayedIndex != -1) lastId = "unknown_" + lastPlayedIndex;

        LogUtils.i("AUDIO_DEBUG [" + System.identityHashCode(this) + "]: ID Check -> current=" + currentId + ", last=" + lastId);
        if (!currentId.equals(lastId)) {
            LogUtils.i("AUDIO_DEBUG [" + System.identityHashCode(this) + "]: TRIGGERING NEW AUDIO");
            lastPlayedIndex = currentIndex;
            this.currentAudioIndex = 0;
            playCurrentAudio();
        } else {
            LogUtils.i("AUDIO_DEBUG [" + System.identityHashCode(this) + "]: SKIP AUDIO (Same Medicine)");
        }

        // Reset image rotation for new medicine
        stopImageRotation();
        if (current.resultType != ResultType.UNKNOWN) {
            this.imageUrls = current.imageUrls;
            this.currentImageIndex = 0;
            startImageRotation();
        }

        // Reset medicine auto-rotate timer
        stopTimer();
        updatePauseButtonState();

        if (isPaused) {
            LogUtils.d("Timer reset blocked due to pause. Pause timer remains active.");
            return;
        }

        if (current.resultType == ResultType.UNKNOWN) {
            timerHandler.postDelayed(unknownTimeoutRunnable, UNKNOWN_TIMEOUT_MS);
            LogUtils.d("Unknown result timer started: 3s");
        } else {
            int timeSec = getSharedPreferences("kiosk_settings", MODE_PRIVATE)
                    .getInt("RESULT_SCREEN_TIME", 30);
            timerHandler.postDelayed(autoRotateRunnable, (long) timeSec * 1000);
            LogUtils.d("Configured timer started: " + timeSec + "s");
        }

        updatePauseButtonState();
    }

    private void startImageRotation() {
        if (imageUrls == null || imageUrls.size() <= 1) {
            Log.d(TAG, "Image rotation skipped: " + (imageUrls == null ? "null" : imageUrls.size()) + " images");
            return;
        }

        Log.i(TAG, "Starting auto-rotation for " + imageUrls.size() + " images");

        imageSwitcher = new Runnable() {
            @Override
            public void run() {
                if (imagePager == null) return;

                int nextIndex = (imagePager.getCurrentItem() + 1) % imageUrls.size();
                Log.d(TAG, "Rotating image from " + imagePager.getCurrentItem() + " to " + nextIndex);
                imagePager.setCurrentItem(nextIndex, true);

                imageRotationHandler.postDelayed(this, 3000); // 3 seconds
            }
        };

        imageRotationHandler.postDelayed(imageSwitcher, 3000);
    }

    private void stopImageRotation() {
        if (imageSwitcher != null) {
            Log.d(TAG, "Stopping image rotation runnable");
            imageRotationHandler.removeCallbacksAndMessages(null);
            imageSwitcher = null;
        }
    }

    private void showNextResult() {
        if (scanResults == null || scanResults.isEmpty()) return;

        if (currentIndex + 1 < scanResults.size()) {
            currentIndex++;
            renderCurrent();
        } else {
            LogUtils.i("All results displayed. Returning to scan.");
            returnToScan();
        }
    }

    private void showPreviousResult() {
        if (scanResults == null || scanResults.isEmpty()) return;

        if (currentIndex > 0) {
            currentIndex--;
            renderCurrent();
        } else {
            LogUtils.d("Already at first result");
        }
    }

    private void updatePauseButtonState() {
        btnPause.setImageResource(isPaused
                ? android.R.drawable.ic_media_play
                : android.R.drawable.ic_media_pause);
    }

    @Override
    public void onStateChanged(AppState state) {
        runOnUiThread(() -> {
            LogUtils.d("ResultActivity observed state: " + state);
            
            // Sync local isPaused with actual StateMachine state
            boolean wasPaused = isPaused;
            isPaused = (state == AppState.RESULT_PAUSED);
            
            if (wasPaused != isPaused) {
                updatePauseButtonState();
            }

            switch (state) {
                case RESULT_PAUSED:
                    LogUtils.i("Pause mode activated - stopping timers");
                    stopTimer();
                    break;

                case RESULT_AUTO:
                case RESULT_MANUAL_NAV:
                case RESULT_UNKNOWN:
                    if (wasPaused && !isPaused) {
                        LogUtils.i("Pause mode ended - resuming timers");
                    }
                    renderCurrent();
                    break;

                case READY:
                case IDLE:
                    stopTimer();
                    stopImageRotation();
                    if (!isFinishing()) {
                        finish();
                    }
                    break;
            }
        });
    }

    private void stopTimer() {
        LogUtils.d("Stopping all timers (clearing Handler)");
        timerHandler.removeCallbacksAndMessages(null);
    }

    private void playCurrentAudio() {
        boolean voiceEnabled = getSharedPreferences("kiosk_settings", MODE_PRIVATE)
                .getBoolean("voice_enabled", true);
        LogUtils.i("AUDIO_DEBUG [" + System.identityHashCode(this) + "]: playCurrentAudio() voiceEnabled=" + voiceEnabled + ", isAudioPlaying=" + isAudioPlaying);

        if (btnMute != null) {
            btnMute.setImageResource(voiceEnabled 
                ? android.R.drawable.ic_lock_silent_mode_off 
                : android.R.drawable.ic_lock_silent_mode);
        }

        if (scanResults == null || currentIndex >= scanResults.size() || !voiceEnabled) {
            stopAudio();
            return;
        }

        ScanResult current = scanResults.get(currentIndex);
        List<String> urls = current.audioUrls;

        if (urls == null || urls.isEmpty() || currentAudioIndex >= urls.size()) {
            LogUtils.i("AUDIO_DEBUG [" + System.identityHashCode(this) + "]: No more audio segments.");
            stopAudio();
            return;
        }

        String path = urls.get(currentAudioIndex);
        LogUtils.i("AUDIO_DEBUG [" + System.identityHashCode(this) + "]: Playing segment " + currentAudioIndex + " for " + current.displayName);
        playAudio(current.medicineId, currentAudioIndex, path);
    }

    private void playAudio(String medicineId, int index, String url) {
        LogUtils.i("AUDIO_DEBUG [" + System.identityHashCode(this) + "]: playAudio() START medicineId=" + medicineId + ", index=" + index);
        PerformanceProfiler.start("Audio Ready");
        Log.d("AUDIO", "Play requested for: " + medicineId + " index: " + index);

        AudioCacheManager cacheManager = AudioCacheManager.getInstance(this);
        String cachedPath = cacheManager.getCachedAudioPath(medicineId, index);

        if (cachedPath == null) {
            LogUtils.i("AUDIO_DEBUG [" + System.identityHashCode(this) + "]: Cache Miss, downloading...");
            cacheManager.prefetchAudio(medicineId, index, url, new AudioCacheManager.Callback() {
                @Override
                public void onDownloadCompleted(String path) {
                    runOnUiThread(() -> {
                        LogUtils.i("AUDIO_DEBUG [" + System.identityHashCode(this) + "]: Download COMPLETED callback for " + medicineId);
                        if (scanResults != null && currentIndex < scanResults.size()) {
                            ScanResult current = scanResults.get(currentIndex);
                            if (current.medicineId.equals(medicineId) && currentAudioIndex == index) {
                                LogUtils.i("AUDIO_DEBUG [" + System.identityHashCode(this) + "]: Condition met, starting playback after download");
                                playAudio(medicineId, index, url); 
                            } else {
                                LogUtils.w("AUDIO_DEBUG [" + System.identityHashCode(this) + "]: Condition NOT met (medicine or index changed)");
                            }
                        }
                    });
                }

                @Override
                public void onDownloadFailed(Exception e) {
                    LogUtils.e("AUDIO_DEBUG [" + System.identityHashCode(this) + "]: Download FAILED", e);
                }
            });
            return; 
        }

        LogUtils.i("AUDIO_DEBUG [" + System.identityHashCode(this) + "]: Cache HIT, preparing player...");
        
        // 🚀 STOP PREVIOUS BEFORE STARTING NEW
        stopAudio();

        isAudioPlaying = true;
        Log.d("AUDIO", "START PLAY from local cache: " + cachedPath);
        Log.d("AUDIO", "local file path: " + cachedPath);
        Log.d("AUDIO", "MediaPlayer source path: " + cachedPath);

        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception e) {}
            mediaPlayer = null;
        }

        PerformanceProfiler.start("MediaPlayer Init");
        mediaPlayer = new MediaPlayer();
        try {
            float volume = getSharedPreferences("kiosk_settings", MODE_PRIVATE)
                    .getInt("voice_volume", 100) / 100f;
            mediaPlayer.setVolume(volume, volume);

            try {
                mediaPlayer.setDataSource(cachedPath);
                Log.d("AUDIO", "Local file set successfully: " + cachedPath);
            } catch (Exception e) {
                Log.e("AUDIO", "Local file set failed: " + cachedPath, e);
                // Step 4: DELETE CORRUPTED CACHE
                cacheManager.deleteCachedFile(medicineId, index);
                isAudioPlaying = false;
                PerformanceProfiler.end("MediaPlayer Init");
                PerformanceProfiler.end("Audio Ready");
                return;
            }

            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d("AUDIO", "COMPLETED: " + cachedPath);
                mp.release();
                if (mediaPlayer == mp) {
                    mediaPlayer = null;
                }
                isAudioPlaying = false;
                
                currentAudioIndex++;
                playCurrentAudio(); // Try next audio if any
            });

            mediaPlayer.setOnPreparedListener(mp -> {
                PerformanceProfiler.log("Audio Ready", "Playback Ready");
                PerformanceProfiler.end("Audio Ready");
                Log.d("AUDIO", "playback started");
                mp.start();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e("AUDIO", "playback failure reason: what=" + what + " extra=" + extra);
                
                // Step 4: DELETE CORRUPTED CACHE
                cacheManager.deleteCachedFile(medicineId, index);
                
                mp.release();
                if (mediaPlayer == mp) {
                    mediaPlayer = null;
                }
                isAudioPlaying = false;
                
                // Fallback: move to next audio if available
                currentAudioIndex++;
                playCurrentAudio();

                PerformanceProfiler.end("Audio Ready");
                return true;
            });

            PerformanceProfiler.start("Audio Decode");
            mediaPlayer.prepareAsync();
            PerformanceProfiler.end("Audio Decode");
            PerformanceProfiler.end("MediaPlayer Init");
            LogUtils.i("Audio prepare started (cached): " + cachedPath);
        } catch (Exception e) {
            LogUtils.e("Error setting up audio: " + cachedPath, e);
            isAudioPlaying = false;
            PerformanceProfiler.end("Audio Ready");
        }
    }

    private void stopAudio() {
        LogUtils.d("AUDIO_DEBUG: stopAudio() called");
        if (mediaPlayer != null) {
            try {
                // Remove stop() to avoid error -38 in certain states
                mediaPlayer.release();
                LogUtils.d("AUDIO_DEBUG: MediaPlayer released");
            } catch (Exception e) {
                LogUtils.e("Error releasing MediaPlayer", e);
            }
            mediaPlayer = null;
        }
        isAudioPlaying = false;
    }

    private void highlightInfoItem(int index) {
        runOnUiThread(() -> {
            if (infoList != null && infoList.getAdapter() instanceof ResultInfoAdapter) {
                ((ResultInfoAdapter) infoList.getAdapter()).setHighlightedPosition(index);
                if (index != -1) {
                    infoList.smoothScrollToPosition(index);
                }
            }
        });
    }

    private void blockBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                LogUtils.i("Back navigation is blocked in kiosk mode");
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            
            // 🚀 Support keyboard pause/play (Space, P, or Pause key)
            if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_P || 
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_BREAK) {
                LogUtils.i("Pause/Play key pressed on keyboard");
                if (btnPause != null) btnPause.performClick();
                return true;
            }

            // 🚀 Support keyboard navigation (Arrows)
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                LogUtils.d("Forward navigation key pressed");
                showNextResult();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                LogUtils.d("Backward navigation key pressed");
                showPreviousResult();
                return true;
            }

            // 🚀 Listen for HOME, ESCAPE, or BACK key on physical keyboard to return to scan
            if (keyCode == KeyEvent.KEYCODE_MOVE_HOME || keyCode == KeyEvent.KEYCODE_ESCAPE || 
                keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_BACK) {
                LogUtils.i("Navigation key pressed on keyboard - returning to scan");
                returnToScan();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
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
        stopImageRotation();
        stopAudio();
    }
}
