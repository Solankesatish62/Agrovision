package com.agrovision.kiosk.app;

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

            if (cursor.moveToFirst()) {
                int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_SUCCESSFUL) {
                    long downloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    long total = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                    if (total > 0) {
                        int progress = (int) ((downloaded * 100L) / total);
                        handler.post(() -> {
                            progressBar.setIndeterminate(false);
                            progressBar.setProgress(progress);
                            tvProgress.setText("डाउनलोड होत आहे: " + progress + "% (" + (downloaded/1024/1024) + "MB / " + (total/1024/1024) + "MB)");
                        });
                    } else {
                        handler.post(() -> {
                            progressBar.setIndeterminate(true);
                            tvProgress.setText("डाउनलोड होत आहे... (Downloading...)");
                        });
                    }
                }
            }
            cursor.close();
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
        // Improved security check: Allow firebasestorage and our own cloud domains if added
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
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setTitle("AgroVision Update");
            request.setDescription("Downloading latest version...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            
            String fileName = "AgroVision_Update.apk";
            // Clean up any old file before starting new download
            File oldFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
            if (oldFile.exists()) {
                oldFile.delete();
            }

            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                downloadId = manager.enqueue(request);
                handler.post(progressRunnable);
                
                // Use ApplicationContext for registration to prevent Activity leaks
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
            Toast.makeText(context, "डाउनलोड सुरू करण्यात अडथळा आला (Failed to start download)", Toast.LENGTH_SHORT).show();
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

        if (cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = cursor.getInt(columnIndex);
            int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
            int reason = cursor.getInt(reasonIndex);

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                LogUtils.i("Download successful. Launching installer.");
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                installApk();
            } else if (status == DownloadManager.STATUS_FAILED) {
                LogUtils.e("Download failed. Status: " + status + ", Reason: " + reason);
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                isUpdateInProgress = false;
                Toast.makeText(context, "अपडेट डाउनलोड अयशस्वी (Download failed)", Toast.LENGTH_SHORT).show();
            }
        }
        cursor.close();
        
        try {
            context.getApplicationContext().unregisterReceiver(onDownloadComplete);
        } catch (Exception e) {
            LogUtils.w("Failed to unregister receiver: " + e.getMessage());
        }
    }

    private void installApk() {
        File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AgroVision_Update.apk");
        if (!file.exists()) {
            LogUtils.e("APK file not found at " + file.getAbsolutePath());
            isUpdateInProgress = false;
            return;
        }

        LogUtils.i("Installing APK from: " + file.getAbsolutePath());

        try {
            // Check for install permission on Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.getPackageManager().canRequestPackageInstalls()) {
                    LogUtils.w("Permission missing: REQUEST_INSTALL_PACKAGES. Opening settings.");
                    Toast.makeText(context, "कृपया 'Unknown Apps' इंस्टॉल करण्याची परवानगी द्या", Toast.LENGTH_LONG).show();
                    Intent settingsIntent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    settingsIntent.setData(Uri.parse("package:" + context.getPackageName()));
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(settingsIntent);
                    isUpdateInProgress = false;
                    return;
                }
            }

            Uri contentUri = FileProvider.getUriForFile(context, "com.agrovision.kiosk.fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (Exception e) {
            LogUtils.e("Failed to launch installer", e);
            Toast.makeText(context, "इन्स्टॉलर सुरू करण्यात अडथळा आला (Failed to start installer)", Toast.LENGTH_SHORT).show();
        } finally {
            isUpdateInProgress = false;
        }
    }
}
