package com.agrovision.kiosk.sync;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.agrovision.kiosk.data.database.AppDatabase;
import com.agrovision.kiosk.data.database.dao.UnknownDetectionDao;
import com.agrovision.kiosk.data.database.entity.UnknownDetectionEntity;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public final class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting background sync for unknown detections...");

        UnknownDetectionDao dao = AppDatabase.getInstance(getApplicationContext()).unknownDetectionDao();
        List<UnknownDetectionEntity> unsynced = dao.getUnsynced();

        if (unsynced.isEmpty()) {
            Log.d(TAG, "No pending detections to sync.");
            return Result.success();
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        FirebaseStorage storage = FirebaseStorage.getInstance();

        boolean allSuccessful = true;

        for (UnknownDetectionEntity detection : unsynced) {
            try {
                String imageUrl = null;

                // 1. Upload image if it exists
                if (detection.localImagePath != null) {
                    File file = new File(detection.localImagePath);
                    if (file.exists()) {
                        StorageReference ref = storage.getReference()
                                .child("unknown_images/" + System.currentTimeMillis() + ".jpg");
                        
                        Tasks.await(ref.putFile(Uri.fromFile(file)));
                        imageUrl = Tasks.await(ref.getDownloadUrl()).toString();
                    }
                }

                // 2. Upload metadata to Firestore
                Map<String, Object> data = new HashMap<>();
                data.put("ocrText", detection.rawOcrText);
                data.put("timestamp", detection.timestamp);
                data.put("imageUrl", imageUrl);
                data.put("deviceId", android.os.Build.MODEL);

                Tasks.await(firestore.collection("unknown_detections").add(data));

                // 3. Mark as synced in local DB
                detection.isSynced = true;
                dao.update(detection);
                
                Log.i(TAG, "Successfully synced: " + detection.rawOcrText);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Failed to sync detection: " + detection.rawOcrText, e);
                allSuccessful = false;
            }
        }

        return allSuccessful ? Result.success() : Result.retry();
    }
}
