package com.agrovision.kiosk.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.agrovision.kiosk.BuildConfig;
import com.agrovision.kiosk.util.LogUtils;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;

public final class UpdateManager {

    private static final String TAG = "UpdateManager";
    private final Context context;
    private static long downloadId = -1;
    private static boolean isUpdateInProgress = false;
    private AlertDialog progressDialog;
    private android.widget.ProgressBar progressBar;
    private android.widget.TextView tvProgress;
    private final Handler handler = new Handler(android.os.Looper.getMainLooper());

    public UpdateManager(Context context) {
        this.context = context;
    }

    private void showDownloadProgressDialog() {
        if (context instanceof Activity && ((Activity) context).isFinishing()) {
            return;
        }

        // We'll create a simple custom layout programmatically for reliability
        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        tvProgress = new android.widget.TextView(context);
        tvProgress.setText("डाउनलोड होत आहे... (Downloading...)");
        tvProgress.setTextSize(18);
        tvProgress.setPadding(0, 0, 0, 30);

        progressBar = new android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setIndeterminate(true); // Start indeterminate until size is known

        layout.addView(tvProgress);
        layout.addView(progressBar);

        progressDialog = new AlertDialog.Builder(context)
                .setTitle("AgroVision Update")
                .setView(layout)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (downloadId == -1 || progressDialog == null || !progressDialog.isShowing()) return;

            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
            android.database.Cursor cursor = manager.query(query);

            if (cursor != null && cursor.moveToFirst()) {
                int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                if (statusColumn != -1) {
                    int status = cursor.getInt(statusColumn);
                    if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_SUCCESSFUL) {
                        int downloadedColumn = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int totalColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);

                        if (downloadedColumn != -1 && totalColumn != -1) {
                            long downloaded = cursor.getLong(downloadedColumn);
                            long total = cursor.getLong(totalColumn);

                            if (total > 0) {
                                int progress = (int) ((downloaded * 100L) / total);
                                handler.post(() -> {
                                    if (progressBar != null) {
                                        progressBar.setIndeterminate(false);
                                        progressBar.setProgress(progress);
                                    }
                                    if (tvProgress != null) {
                                        tvProgress.setText("डाउनलोड होत आहे: " + progress + "% (" + (downloaded / 1024 / 1024) + "MB / " + (total / 1024 / 1024) + "MB)");
                                    }
                                });
                            }
                        }
                    }
                }
                cursor.close();
            }
            handler.postDelayed(this, 1000); // Update every second
        }
    };

    public void checkForUpdates() {
        if (isUpdateInProgress) {
            LogUtils.d("Update check skipped: Update already in progress.");
            return;
        }
        LogUtils.d("Checking for updates...");
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("app_updates").document("latest")
                .get()
                .addOnSuccessListener(this::handleUpdateResponse)
                .addOnFailureListener(e -> LogUtils.e("Failed to check for updates", e));
    }

    private void handleUpdateResponse(DocumentSnapshot doc) {
        if (!doc.exists()) {
            LogUtils.d("No update configuration found in Firestore (app_updates/latest).");
            return;
        }

        Long latestVersionCode = doc.getLong("latestVersionCode");
        String latestVersionName = doc.getString("latestVersionName");
        String apkUrl = doc.getString("apkUrl");
        Boolean forceUpdate = doc.getBoolean("forceUpdate");

        if (latestVersionCode == null) {
            LogUtils.w("Update check: latestVersionCode is missing in Firestore.");
            return;
        }
        if (apkUrl == null) {
            LogUtils.w("Update check: apkUrl is missing in Firestore.");
            return;
        }

        long currentVersionCode = getAppVersionCode();

        LogUtils.i("OTA Check: Latest=" + latestVersionCode + " (" + latestVersionName + "), Current=" + currentVersionCode);

        if (latestVersionCode > currentVersionCode) {
            LogUtils.i("New version found! Triggering update dialog.");
            isUpdateInProgress = true;
            showUpdateDialog(latestVersionName, apkUrl, forceUpdate != null && forceUpdate);
        } else {
            LogUtils.d("App is up to date (Current version: " + currentVersionCode + ").");
            cleanupOldApks();
        }
    }

    private void cleanupOldApks() {
        try {
            // 1. Clear DownloadManager database entries for this app
            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                DownloadManager.Query query = new DownloadManager.Query();
                android.database.Cursor cursor = manager.query(query);
                if (cursor != null) {
                    int idColumn = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
                    if (idColumn != -1) {
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(idColumn);
                            manager.remove(id);
                        }
                    }
                    cursor.close();
                }
            }

            // 2. Manual cleanup of the download directory
            File downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloadDir != null && downloadDir.exists()) {
                File[] files = downloadDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().endsWith(".apk")) {
                            if (file.delete()) {
                                LogUtils.i("Cleanup: Deleted old update file: " + file.getName());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.e("Failed to cleanup old updates", e);
        }
    }

    private long getAppVersionCode() {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return pInfo.getLongVersionCode();
            } else {
                return pInfo.versionCode;
            }
        } catch (PackageManager.NameNotFoundException e) {
            LogUtils.e("Failed to get version code", e);
            return -1;
        }
    }

    private void showUpdateDialog(String versionName, String apkUrl, boolean isForce) {
        new AlertDialog.Builder(context)
                .setTitle("Update Available")
                .setMessage("A new version (" + versionName + ") of AgroVision is available. Would you like to update now?")
                .setCancelable(!isForce)
                .setOnCancelListener(dialog -> isUpdateInProgress = false)
                .setPositiveButton("Update", (dialog, which) -> startDownload(apkUrl))
                .setNegativeButton(isForce ? "Exit App" : "Later", (dialog, which) -> {
                    isUpdateInProgress = false;
                    if (isForce) {
                        System.exit(0);
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private void startDownload(String url) {
        if (url == null || (!url.contains("firebasestorage.googleapis.com") && !url.contains("agrovision"))) {
            LogUtils.e("Security Alert: Blocked APK download from untrusted source: " + url);
            Toast.makeText(context, "सुरक्षित नसलेला अपडेट स्रोत ब्लॉक केला (Untrusted update source blocked)", Toast.LENGTH_LONG).show();
            isUpdateInProgress = false;
            return;
        }

        LogUtils.i("Starting APK download from: " + url);
        Toast.makeText(context, "अपडेट डाउनलोड होत आहे... (Download started)", Toast.LENGTH_SHORT).show();

        showDownloadProgressDialog();

        try {
            cleanupOldApks();

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setTitle("AgroVision Update");
            request.setDescription("Downloading latest version...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            
            String fileName = "AgroVision_Update.apk";
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                downloadId = manager.enqueue(request);
                handler.post(progressRunnable);
                
                Context appContext = context.getApplicationContext();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(onDownloadComplete, 
                            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), 
                            Context.RECEIVER_EXPORTED);
                } else {
                    appContext.registerReceiver(onDownloadComplete, 
                            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
                }
            } else {
                isUpdateInProgress = false;
            }
        } catch (Exception e) {
            LogUtils.e("Failed to start download", e);
            isUpdateInProgress = false;
        }
    }

    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (downloadId == id) {
                LogUtils.i("Download complete (ID: " + id + "). Querying status...");
                checkDownloadStatus(context, id);
            }
        }
    };

    private void checkDownloadStatus(Context context, long id) {
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);
        android.database.Cursor cursor = manager.query(query);

        if (cursor != null && cursor.moveToFirst()) {
            int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int reasonColumn = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
            
            if (statusColumn != -1) {
                int status = cursor.getInt(statusColumn);
                int reason = reasonColumn != -1 ? cursor.getInt(reasonColumn) : -1;

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    LogUtils.i("Download successful (ID: " + id + "). Launching installer.");
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    installApk(context, id);
                } else if (status == DownloadManager.STATUS_FAILED) {
                    LogUtils.e("Download failed (ID: " + id + "). Status: " + status + ", Reason: " + reason);
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    isUpdateInProgress = false;
                    Toast.makeText(context, "अपडेट डाउनलोड अयशस्वी (Download failed)", Toast.LENGTH_SHORT).show();
                }
            }
            cursor.close();
        }
        
        try {
            context.getApplicationContext().unregisterReceiver(onDownloadComplete);
        } catch (Exception e) {
            LogUtils.w("Failed to unregister receiver: " + e.getMessage());
        }
    }

    private void verifyApkBeforeInstall(Context context, long downloadId) {
        try {
            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            try (android.os.ParcelFileDescriptor pfd = manager.openDownloadedFile(downloadId)) {
                if (pfd == null) {
                    LogUtils.w("Verification: Could not open downloaded file descriptor.");
                }
            } catch (Exception e) {
                LogUtils.w("Verification: Error opening file descriptor: " + e.getMessage());
            }

            // Since we can't easily get a File path from DownloadManager on all versions,
            // we'll try to find the file we saved
            File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AgroVision_Update.apk");
            if (!file.exists()) {
                LogUtils.w("Verification: APK file not found on disk for metadata check.");
                return;
            }

            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);

            if (info != null) {
                long currentVersion = getAppVersionCode();
                long newVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
                
                LogUtils.i("--- APK PRE-INSTALL VERIFICATION ---");
                LogUtils.i("Package Name: " + info.packageName);
                LogUtils.i("New Version Code: " + newVersion + " (Current: " + currentVersion + ")");
                LogUtils.i("New Version Name: " + info.versionName);
                
                // Check free space
                File externalDir = context.getExternalFilesDir(null);
                if (externalDir != null) {
                    long freeSpace = externalDir.getFreeSpace();
                    LogUtils.i("Storage: " + (freeSpace / 1024 / 1024) + "MB free");
                    if (freeSpace < (file.length() * 3)) { // Rule of thumb: need ~3x APK size to install
                        LogUtils.w("WARNING: Low storage space. Installation might fail.");
                    }
                }

                if (!context.getPackageName().equals(info.packageName)) {
                    LogUtils.e("CRITICAL: APK package name mismatch! App: " + context.getPackageName() + " vs APK: " + info.packageName);
                }
                if (newVersion <= currentVersion) {
                    LogUtils.w("WARNING: New version code is NOT greater than current version. Install might fail.");
                }
                LogUtils.i("------------------------------------");
            } else {
                LogUtils.e("CRITICAL: Failed to parse APK metadata. The file might be corrupted or incomplete.");
            }
        } catch (Exception e) {
            LogUtils.w("Could not verify APK metadata: " + e.getMessage());
        }
    }

    private void installApk(Context context, long downloadId) {
        try {
            // 1. Try to get FileProvider URI first (usually more reliable for Kiosks)
            File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AgroVision_Update.apk");
            Uri contentUri;
            
            if (file.exists()) {
                contentUri = FileProvider.getUriForFile(context, "com.agrovision.kiosk.fileprovider", file);
                LogUtils.i("Installing using FileProvider URI: " + contentUri);
            } else {
                DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                contentUri = manager.getUriForDownloadedFile(downloadId);
                LogUtils.w("APK file not found on disk, falling back to DownloadManager URI: " + contentUri);
            }

            if (contentUri == null) {
                LogUtils.e("Failed to resolve APK URI.");
                isUpdateInProgress = false;
                return;
            }

            // 🚀 NEW: Pre-install verification logs
            verifyApkBeforeInstall(context, downloadId);

            // 2. If we are in an Activity, try to stop LockTask mode temporarily
            // This is CRITICAL for kiosks, otherwise the installer is blocked.
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                try {
                    activity.stopLockTask();
                    LogUtils.i("Temporary release of LockTask for installation");
                } catch (Exception e) {
                    // Might fail if not in lock task mode, that's okay
                }
            }

            // 3. Prepare Install Intent
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            
            context.startActivity(intent);
            LogUtils.i("Installer activity started successfully.");
            
        } catch (Exception e) {
            LogUtils.e("Failed to launch installer", e);
            Toast.makeText(context, "इन्स्टॉलर सुरू करण्यात अडथळा आला (Installer failed)", Toast.LENGTH_SHORT).show();
        } finally {
            isUpdateInProgress = false;
        }
    }
}
