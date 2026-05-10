package com.agrovision.kiosk.ui.result;

import android.content.Intent;
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
import com.agrovision.kiosk.state.AppState;
import com.agrovision.kiosk.state.StateEvent;
import com.agrovision.kiosk.state.StateMachine;
import com.agrovision.kiosk.state.StateObserver;
import com.agrovision.kiosk.ui.home.HomeActivity;
import com.agrovision.kiosk.ui.result.adapter.ResultInfoAdapter;
import com.agrovision.kiosk.ui.result.model.ResultType;
import com.agrovision.kiosk.ui.result.model.ScanResult;
import com.agrovision.kiosk.util.LogUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

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
    private List<String> audioUrls;
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

        StateMachine.getInstance(getApplicationContext())
                .transition(StateEvent.RESULT_TIMEOUT);

        Intent intent = new Intent(ResultActivity.this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
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
            this.audioUrls = current.audioUrls;
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
        } else if (!isPaused) {
            int timeSec = getSharedPreferences("kiosk_settings", MODE_PRIVATE)
                    .getInt("RESULT_SCREEN_TIME", 30);
            timerHandler.postDelayed(autoRotateRunnable, (long) timeSec * 1000);
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
                    LogUtils.i("Result paused");
                    isPaused = true;
                    stopTimer();
                    updatePauseButtonState();
                    break;

                case RESULT_AUTO:
                case RESULT_MANUAL_NAV:
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
        timerHandler.removeCallbacks(autoRotateRunnable);
        timerHandler.removeCallbacks(unknownTimeoutRunnable);
    }

    private void playCurrentAudio() {
        boolean voiceEnabled = getSharedPreferences("kiosk_settings", MODE_PRIVATE)
                .getBoolean("voice_enabled", true);
        int volumePercent = getSharedPreferences("kiosk_settings", MODE_PRIVATE)
                .getInt("voice_volume", 100);

        if (btnMute != null) {
            btnMute.setImageResource(voiceEnabled 
                ? android.R.drawable.ic_lock_silent_mode_off 
                : android.R.drawable.ic_lock_silent_mode);
        }

        if (audioUrls == null || audioUrls.isEmpty() || isPaused || !voiceEnabled) {
            stopAudio();
            return;
        }

        if (currentAudioIndex >= audioUrls.size()) {
            return;
        }

        String path = audioUrls.get(currentAudioIndex);
        playAudio(path);
    }

    private void playAudio(String path) {
        Log.d("AUDIO", "Play requested: " + path);

        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            Log.d("AUDIO", "Skipping: already playing");
            return;
        }

        isAudioPlaying = true;
        Log.d("AUDIO", "START PLAY: " + path);

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

            String assetPath = path.replace("file:///android_asset/", "");
            
            try {
                AssetFileDescriptor afd = getAssets().openFd(assetPath);
                mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                Log.d("AUDIO", "File loaded successfully: " + assetPath);
            } catch (Exception e) {
                Log.e("AUDIO", "File load failed: " + assetPath, e);
                isAudioPlaying = false;
                return;
            }

            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d("AUDIO", "COMPLETED: " + path);
                mp.release();
                if (mediaPlayer == mp) {
                    mediaPlayer = null;
                }
                isAudioPlaying = false;
                
                currentAudioIndex++;
                if (currentAudioIndex < audioUrls.size()) {
                    playCurrentAudio();
                }
            });

            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d("AUDIO", "Prepared, starting playback");
                mp.start();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e("AUDIO", "Error: " + what + " extra: " + extra);
                mp.release();
                if (mediaPlayer == mp) {
                    mediaPlayer = null;
                }
                isAudioPlaying = false;
                return true;
            });

            mediaPlayer.prepareAsync();
            LogUtils.i("Audio prepare started: " + path);
        } catch (Exception e) {
            LogUtils.e("Error setting up audio: " + path, e);
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
