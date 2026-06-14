package com.agrovision.kiosk.util;

import android.content.Context;
import android.util.Log;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages local caching of audio files from Firebase Storage.
 * Ensures audio is played from local storage to eliminate streaming delays.
 * Implements temp-file strategy and size verification to prevent "Premature end of file" errors.
 */
public class AudioCacheManager {
    private static final String TAG = "AudioCacheManager";
    private static AudioCacheManager instance;
    private final Context context;
    private final File audioCacheDir;
    private final Map<String, Boolean> downloadInProgress = new HashMap<>();
    
    // Minimum valid MP3 file size in bytes (e.g., 5KB)
    private static final long MIN_FILE_SIZE = 1024 * 5; 

    private AudioCacheManager(Context context) {
        this.context = context.getApplicationContext();
        this.audioCacheDir = new File(this.context.getCacheDir(), "audio_cache");
        if (!audioCacheDir.exists()) {
            audioCacheDir.mkdirs();
        }
    }

    public static synchronized AudioCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new AudioCacheManager(context);
        }
        return instance;
    }

    /**
     * Gets the local file path for a medicine's audio.
     * @param medicineId The unique ID of the medicine.
     * @param index The index of the audio URL in the list.
     * @return Absolute path to local file, or null if not cached or invalid.
     */
    public String getCachedAudioPath(String medicineId, int index) {
        String fileName = medicineId + "_" + index + ".mp3";
        File file = new File(audioCacheDir, fileName);
        if (file.exists()) {
            long size = file.length();
            if (size >= MIN_FILE_SIZE) {
                Log.d(TAG, "cache hit: " + medicineId + " index: " + index + " path: " + file.getAbsolutePath() + " size: " + size);
                return file.getAbsolutePath();
            } else {
                Log.w(TAG, "cache hit but file too small/corrupted: " + size + " bytes. Deleting.");
                file.delete();
            }
        }
        Log.d(TAG, "cache miss: " + medicineId + " index: " + index);
        return null;
    }

    /**
     * Deletes a cached audio file if it's found to be corrupted during playback.
     */
    public void deleteCachedFile(String medicineId, int index) {
        String fileName = medicineId + "_" + index + ".mp3";
        File file = new File(audioCacheDir, fileName);
        if (file.exists()) {
            Log.w(TAG, "Deleting corrupted cache file: " + fileName);
            file.delete();
        }
    }

    public interface Callback {
        void onDownloadCompleted(String path);
        void onDownloadFailed(Exception e);
    }

    /**
     * Prefetches audio from Firebase if not already cached.
     */
    public void prefetchAudio(String medicineId, int index, String audioUrl, Callback callback) {
        if (audioUrl == null || audioUrl.isEmpty()) {
            if (callback != null) callback.onDownloadFailed(new Exception("Empty URL"));
            return;
        }
        
        String localPath = getCachedAudioPath(medicineId, index);
        if (localPath != null) {
            if (callback != null) callback.onDownloadCompleted(localPath);
            return; // Already cached and verified
        }

        String cacheKey = medicineId + "_" + index;
        synchronized (downloadInProgress) {
            if (Boolean.TRUE.equals(downloadInProgress.get(cacheKey))) {
                Log.d(TAG, "Download already in progress for: " + cacheKey);
                return; 
            }
            downloadInProgress.put(cacheKey, true);
        }

        Log.d(TAG, "cache lookup started: " + medicineId + " index: " + index);
        Log.d(TAG, "Firebase download started: " + medicineId + " index: " + index + " URL: " + audioUrl);
        
        try {
            StorageReference ref = FirebaseStorage.getInstance().getReferenceFromUrl(audioUrl);
            
            // Step 2: TEMP FILE DOWNLOAD strategy
            File tempFile = new File(audioCacheDir, cacheKey + ".tmp");
            File finalFile = new File(audioCacheDir, cacheKey + ".mp3");
            
            if (tempFile.exists()) tempFile.delete();

            ref.getFile(tempFile).addOnSuccessListener(taskSnapshot -> {
                Log.d(TAG, "Firebase download completed to temp: " + tempFile.getAbsolutePath());
                
                // Step 1: VERIFY FILE SIZE
                long size = tempFile.length();
                if (size >= MIN_FILE_SIZE) {
                    if (tempFile.renameTo(finalFile)) {
                        Log.d(TAG, "download complete: " + medicineId + " -> " + finalFile.getAbsolutePath());
                        Log.d(TAG, "local file path: " + finalFile.getAbsolutePath());
                        Log.d(TAG, "file size: " + size + " bytes");
                        
                        synchronized (downloadInProgress) {
                            downloadInProgress.remove(cacheKey);
                        }
                        if (callback != null) callback.onDownloadCompleted(finalFile.getAbsolutePath());
                    } else {
                        Log.e(TAG, "Failed to rename temp file to final file");
                        tempFile.delete();
                        synchronized (downloadInProgress) {
                            downloadInProgress.remove(cacheKey);
                        }
                        if (callback != null) callback.onDownloadFailed(new Exception("Rename failed"));
                    }
                } else {
                    Log.e(TAG, "Downloaded file too small: " + size + " bytes. Rejecting.");
                    tempFile.delete();
                    synchronized (downloadInProgress) {
                        downloadInProgress.remove(cacheKey);
                    }
                    if (callback != null) callback.onDownloadFailed(new Exception("File too small"));
                }
            }).addOnFailureListener(exception -> {
                Log.e(TAG, "Firebase download failed: " + medicineId + " index: " + index, exception);
                synchronized (downloadInProgress) {
                    downloadInProgress.remove(cacheKey);
                }
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                if (callback != null) callback.onDownloadFailed(exception);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing download for: " + medicineId + " index: " + index, e);
            synchronized (downloadInProgress) {
                downloadInProgress.remove(cacheKey);
            }
            if (callback != null) callback.onDownloadFailed(e);
        }
    }
}
