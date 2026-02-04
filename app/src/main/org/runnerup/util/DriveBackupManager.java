package org.runnerup.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.DocumentsContract;
import android.util.Log;
import org.runnerup.db.DBHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DriveBackupManager {
    private static final String TAG = "DriveBackupManager";
    private static final String PREFS_NAME = "drive_backup_prefs";
    private static final String PREF_ACCOUNT_NAME = "backup_account_name";
    private static final String PREF_AUTO_BACKUP_ENABLED = "auto_backup_enabled";
    private static final String PREF_LAST_BACKUP_TIME = "last_backup_time";
    private static final String PREF_BACKUP_URI = "backup_uri";
    
    private final Context context;
    private final String accountName;
    
    public interface BackupCallback {
        void onBackupSuccess(String message);
        void onBackupError(String error);
        void onProgress(String message);
        void onAuthRequired(Intent intent);
    }
    
    public DriveBackupManager(Context context, String accountName) {
        this.context = context;
        if (accountName != null && !accountName.isEmpty()) {
            this.accountName = accountName;
        } else {
            String storedAccount = getAccountName(context);
            this.accountName = storedAccount != null ? storedAccount : null;
        }
    }
    
    public static void setBackupUri(Context context, Uri uri) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (uri != null) {
            prefs.edit().putString(PREF_BACKUP_URI, uri.toString()).apply();
        } else {
            prefs.edit().remove(PREF_BACKUP_URI).apply();
        }
    }
    
    public static Uri getBackupUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uriString = prefs.getString(PREF_BACKUP_URI, null);
        return uriString != null ? Uri.parse(uriString) : null;
    }
    
    public static Intent createFolderPickerIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        return intent;
    }
    
    /**
     * Get the path of the last backup file for sharing
     */
    public static String getLastBackupFilePath(Context context) {
        // This would need to track the actual file path, for now return null
        // The backup callback could save this information
        return null;
    }
    
    public static boolean isBackupEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_AUTO_BACKUP_ENABLED, false);
    }
    
    public static void setBackupEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_AUTO_BACKUP_ENABLED, enabled).apply();
    }
    
    public static String getAccountName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_ACCOUNT_NAME, null);
    }
    
    public static void setAccountName(Context context, String accountName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_ACCOUNT_NAME, accountName).apply();
    }
    
    public static long getLastBackupTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(PREF_LAST_BACKUP_TIME, 0);
    }
    
    /**
     * Schedule automatic backups using WorkManager
     * Backups will be saved to app's external files directory (no permissions needed)
     */
    public static void scheduleAutomaticBackup(Context context, boolean enabled) {
        try {
            androidx.work.WorkManager workManager = androidx.work.WorkManager.getInstance(context);
            
            if (enabled) {
                // Cancel existing work
                workManager.cancelUniqueWork("auto_backup");
                
                // Create periodic work request (daily, every 24 hours with 15 minute flex window)
                androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
                    .setRequiresCharging(false)
                    .setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
                    .build();
                
                androidx.work.PeriodicWorkRequest backupRequest = 
                    new androidx.work.PeriodicWorkRequest.Builder(
                        org.runnerup.util.BackupWorker.class, 
                        24, java.util.concurrent.TimeUnit.HOURS,
                        15, java.util.concurrent.TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .addTag("backup")
                    .build();
                
                workManager.enqueueUniquePeriodicWork(
                    "auto_backup",
                    androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                    backupRequest);
                
                android.util.Log.d("DriveBackupManager", "Automatic backup scheduled (daily)");
            } else {
                workManager.cancelUniqueWork("auto_backup");
                android.util.Log.d("DriveBackupManager", "Automatic backup cancelled");
            }
        } catch (Exception e) {
            android.util.Log.e("DriveBackupManager", "Failed to schedule backup", e);
        }
    }
    
    public void backupDatabase(BackupCallback callback) {
        new BackupTask(callback).execute();
    }
    
    private class BackupTask extends AsyncTask<Void, String, Boolean> {
        private final BackupCallback callback;
        private String errorMessage = null;
        
        BackupTask(BackupCallback callback) {
            this.callback = callback;
        }
        
        @Override
        protected void onProgressUpdate(String... values) {
            if (callback != null && values.length > 0) {
                callback.onProgress(values[0]);
            }
        }
        
        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                publishProgress("Preparing backup...");
                
                Uri backupUri = getBackupUri(context);
                if (backupUri == null) {
                    errorMessage = "Backup folder not selected. Please select a Google Drive folder first.";
                    if (callback != null) {
                        callback.onAuthRequired(createFolderPickerIntent());
                    }
                    return false;
                }
                
                publishProgress("Preparing database file...");
                String dbPath = DBHelper.getDbPath(context);
                File dbFile = new File(dbPath);
                if (!dbFile.exists()) {
                    errorMessage = "Database file not found";
                    return false;
                }
                
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
                String timestamp = sdf.format(new Date());
                String fileName = "runnerup_backup_" + timestamp + ".db";
                
                publishProgress("Creating backup file...");
                
                Uri fileUri = null;
                try {
                    fileUri = DocumentsContract.createDocument(context.getContentResolver(), backupUri, 
                        "application/x-sqlite3", fileName);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to create document via DocumentsContract", e);
                    errorMessage = "Cannot create file automatically. Please select the folder again.";
                    if (callback != null) {
                        callback.onAuthRequired(createFolderPickerIntent());
                    }
                    return false;
                }
                
                if (fileUri == null) {
                    errorMessage = "Failed to create backup file. Please select the folder again.";
                    if (callback != null) {
                        callback.onAuthRequired(createFolderPickerIntent());
                    }
                    return false;
                }
                
                publishProgress("Saving to Google Drive...");
                
                try (InputStream in = new FileInputStream(dbFile);
                     OutputStream out = context.getContentResolver().openOutputStream(fileUri, "w")) {
                    if (out == null) {
                        throw new IOException("Failed to open output stream for " + fileUri);
                    }
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytes = 0;
                    long fileSize = dbFile.length();
                    
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                        if (fileSize > 0) {
                            int progress = (int) ((totalBytes * 100) / fileSize);
                            publishProgress("Uploading: " + progress + "%");
                        }
                    }
                    out.flush();
                }
                
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putLong(PREF_LAST_BACKUP_TIME, System.currentTimeMillis()).apply();
                
                publishProgress("Backup completed successfully");
                return true;
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException - permission not granted", e);
                errorMessage = "Permission denied. Please select the backup folder again.";
                if (callback != null) {
                    callback.onAuthRequired(createFolderPickerIntent());
                }
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Backup failed", e);
                errorMessage = "Backup failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                return false;
            }
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            if (callback != null) {
                if (success) {
                    callback.onBackupSuccess("Backup saved to Google Drive successfully");
                } else {
                    callback.onBackupError(errorMessage != null ? errorMessage : "Unknown error");
                }
            }
        }
    }
}
