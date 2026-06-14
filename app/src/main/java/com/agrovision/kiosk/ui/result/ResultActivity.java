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
    private static final long PAUSE_TIMEOUT_MS = 120_000;

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
        showNextResult();
    };

    private final Runnable unknownTimeoutRunnable = () -> {
        LogUtils.i("Unknown result timeout: returning to scan");
        returnToScan();
    };

    private final Runnable pauseTimeoutRunnable = () -> {
        LogUtils.i("Pause timer expired: returning to scan");
        returnToScan();
    };

    // Image Rotation System (Rotating images of the current medicine)
    private int currentImageIndex = 0;
    private List<String> imageUrls;
    private final Handler imageRotationHandler = new Handler(Looper.getMainLooper());
    private Runnable imageSwitcher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_result);

        blockBackNavigation();
        hideSystemUI();

        // 🚀 DISABLE DETECTION while result is showing
        CameraController.getInstance(this).setDetectionEnabled(false);

        bindControls();
        initRenderer();
        loadResults();

        if (scanResults == null || scanResults.isEmpty()) {
            LogUtils.i("No results available for navigation");
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
        stopTimer();
        stopImageRotation();
        stopAudio();
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
        btnMute = findViewById(R.id.btnMute);
        btnNewScan = findViewById(R.id.btnNewScan);
        btnPause = findViewById(R.id.btnPause);
        imagePager = findViewById(R.id.imagePager);
    }

    private void initRenderer() {
        View standardLayout = findViewById(R.id.standardResultLayout);
        View unknownLayout = findViewById(R.id.unknownResultLayout);
        TextView tvMedicineName = findViewById(R.id.tvMedicineName);
        infoList = findViewById(R.id.infoList);
        TextView tvUnknownHeader = findViewById(R.id.tvUnknownHeader);

        renderer = new ResultRenderer(
                standardLayout, unknownLayout, tvMedicineName,
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
        scanResults = getIntent().getParcelableArrayListExtra(EXTRA_SCAN_RESULTS);
    }

    private void returnToScan() {
        LogUtils.i("Executing deterministic navigation to HomeActivity");

        stopTimer();
        stopImageRotation();
        stopAudio();

        // Check if we should show a scan-triggered ad
        SharedPreferences prefs = getSharedPreferences("scan_stats", MODE_PRIVATE);
        int successfulCount = prefs.getInt("successful_ad_count", 0);

        if (successfulCount >= 3) {
            Log.i("AD_DEBUG", "3 successful scans reached. Triggering SCAN_AD.");
            
            // Reset counter IMMEDIATELY
            prefs.edit().putInt("successful_ad_count", 0).apply();
            
            // transition BEFORE starting activity
            StateMachine.getInstance(getApplicationContext()).transition(StateEvent.SCAN_AD_TRIGGERED);
            
            Log.d("AD_DEBUG", "Launching AdActivity (SCAN mode)");
            Intent intent = new Intent(this, AdActivity.class);
            intent.putExtra(AdActivity.EXTRA_AD_TYPE, AdActivity.AdType.SCAN);
            intent.putExtra(AdActivity.EXTRA_AD_DURATION, 8000L); // 8 seconds for scan ad
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
            return;
        }

        StateMachine.getInstance(getApplicationContext())
                .transition(StateEvent.RESULT_TIMEOUT);

        Intent intent = new Intent(ResultActivity.this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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
            StateMachine.getInstance(getApplicationContext())
                    .transition(isPaused ? StateEvent.RESUME_REQUESTED : StateEvent.PAUSE_REQUESTED);
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
        if (scanResults == null || scanResults.isEmpty()) {
            LogUtils.i("No results available for navigation");
            return;
        }

        ScanResult current = scanResults.get(currentIndex);
        renderer.render(current);

        // Audio playback - only trigger if index changed or forced
        if (lastPlayedIndex != currentIndex) {
            this.currentAudioIndex = 0;
            playCurrentAudio();
            lastPlayedIndex = currentIndex;
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

        if (current.resultType == ResultType.UNKNOWN) {
            timerHandler.postDelayed(unknownTimeoutRunnable, UNKNOWN_TIMEOUT_MS);
            LogUtils.d("Unknown result timer started: 3s");
        } else if (!isPaused) {
            int timeSec = getSharedPreferences("kiosk_settings", MODE_PRIVATE)
                    .getInt("RESULT_SCREEN_TIME", 30);
            timerHandler.postDelayed(autoRotateRunnable, (long) timeSec * 1000);
            LogUtils.d("Configured timer started: " + timeSec + "s");
        } else {
            LogUtils.d("Timer reset blocked due to pause. Pause timer remains active.");
            // We refresh the pause timer to give 2 minutes from last navigation/action
            timerHandler.postDelayed(pauseTimeoutRunnable, PAUSE_TIMEOUT_MS);
            LogUtils.d("Pause timer refreshed: 120s");
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
            imageRotationHandler.removeCallbacks(imageSwitcher);
            imageSwitcher = null;
        }
    }

    private void showNextResult() {
        if (scanResults == null || scanResults.isEmpty()) return;

        if (currentIndex + 1 < scanResults.size()) {
            currentIndex++;
            isPaused = false;
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
            isPaused = false;
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
            switch (state) {
                case RESULT_PAUSED:
                    LogUtils.i("Pause override activated - temporarily overriding timer");
                    isPaused = true;
                    stopTimer();
                    timerHandler.postDelayed(pauseTimeoutRunnable, PAUSE_TIMEOUT_MS);
                    LogUtils.d("Pause timer started: 120s");
                    updatePauseButtonState();
                    break;

                case RESULT_AUTO:
                case RESULT_MANUAL_NAV:
                    if (isPaused) {
                        LogUtils.i("Normal timer restored - pause ended");
                    }
                    isPaused = false;
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

        if (btnMute != null) {
            btnMute.setImageResource(voiceEnabled 
                ? android.R.drawable.ic_lock_silent_mode_off 
                : android.R.drawable.ic_lock_silent_mode);
        }

        if (scanResults == null || currentIndex >= scanResults.size() || isPaused || !voiceEnabled) {
            stopAudio();
            return;
        }

        ScanResult current = scanResults.get(currentIndex);
        List<String> urls = current.audioUrls;

        if (urls == null || urls.isEmpty() || currentAudioIndex >= urls.size()) {
            stopAudio();
            return;
        }

        String path = urls.get(currentAudioIndex);
        playAudio(current.medicineId, currentAudioIndex, path);
    }

    private void playAudio(String medicineId, int index, String url) {
        Log.d("AUDIO", "Play requested for: " + medicineId + " index: " + index);

        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            Log.d("AUDIO", "Skipping: already playing");
            return;
        }

        AudioCacheManager cacheManager = AudioCacheManager.getInstance(this);
        String cachedPath = cacheManager.getCachedAudioPath(medicineId, index);

        if (cachedPath == null) {
            Log.d("AUDIO", "cache miss: " + medicineId + " index: " + index);
            Log.d("AUDIO", "Cache MISS - downloading: " + url);
            cacheManager.prefetchAudio(medicineId, index, url, new AudioCacheManager.Callback() {
                @Override
                public void onDownloadCompleted(String path) {
                    // Check if this result is still the one being shown
                    runOnUiThread(() -> {
                        if (scanResults != null && currentIndex < scanResults.size()) {
                            ScanResult current = scanResults.get(currentIndex);
                            if (current.medicineId.equals(medicineId) && currentAudioIndex == index) {
                                Log.d("AUDIO", "Download completed for current result, starting playback");
                                playAudio(medicineId, index, url); // Re-call to play from cached path
                            }
                        }
                    });
                }

                @Override
                public void onDownloadFailed(Exception e) {
                    Log.e("AUDIO", "Download failed during play attempt", e);
                }
            });
            return; 
        }

        Log.d("AUDIO", "cache hit: " + medicineId + " index: " + index + " path: " + cachedPath);
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

                return true;
            });

            mediaPlayer.prepareAsync();
            LogUtils.i("Audio prepare started (cached): " + cachedPath);
        } catch (Exception e) {
            LogUtils.e("Error setting up audio: " + cachedPath, e);
            isAudioPlaying = false;
        }
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            try {
                // Remove stop() to avoid error -38 in certain states
                mediaPlayer.release();
                Log.d("AUDIO", "MediaPlayer released");
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
