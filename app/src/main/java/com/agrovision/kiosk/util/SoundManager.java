package com.agrovision.kiosk.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import com.agrovision.kiosk.R;

public class SoundManager {
    private static SoundManager instance;
    private SoundPool soundPool;
    private int successSoundId;
    private int errorSoundId;
    private boolean loaded = false;

    private SoundManager(Context context) {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(audioAttributes)
                .build();

        successSoundId = soundPool.load(context, R.raw.success, 1);
        errorSoundId = soundPool.load(context, R.raw.error, 1);

        soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (status == 0) {
                loaded = true;
            }
        });
    }

    public static synchronized SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context.getApplicationContext());
        }
        return instance;
    }

    public void playSuccess() {
        if (loaded) {
            soundPool.play(successSoundId, 0.8f, 0.8f, 1, 0, 1f);
        }
    }

    public void playError() {
        if (loaded) {
            soundPool.play(errorSoundId, 0.8f, 0.8f, 1, 0, 1f);
        }
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
            instance = null;
        }
    }
}
