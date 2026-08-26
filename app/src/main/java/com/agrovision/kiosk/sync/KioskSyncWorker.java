package com.agrovision.kiosk.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class KioskSyncWorker extends Worker {
    private static final String TAG = "KioskSyncWorker";

    public KioskSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Background sync triggered by WorkManager");
        SyncManager.getInstance(getApplicationContext()).startSync();
        return Result.success();
    }
}
