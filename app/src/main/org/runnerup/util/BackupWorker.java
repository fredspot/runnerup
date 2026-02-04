package org.runnerup.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.runnerup.db.DBHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BackupWorker extends Worker {
    private static final String TAG = "BackupWorker";
    
    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.d(TAG, "Starting automatic backup");
            
            // Check if backup folder is selected (SAF URI)
            Uri backupUri = DriveBackupManager.getBackupUri(getApplicationContext());
            
            if (backupUri != null) {
                // Use DriveBackupManager to backup to Google Drive
                return backupToDrive(backupUri);
            } else {
                // Fallback to local backup
                return backupLocally();
            }
        } catch (Exception e) {
            Log.e(TAG, "Backup failed", e);
            return Result.failure();
        }
    }
    
    private Result backupToDrive(Uri backupUri) {
        try {
            Log.d(TAG, "Backing up to Google Drive via SAF");
            
            // Get database path
            String dbPath = DBHelper.getDbPath(getApplicationContext());
            File dbFile = new File(dbPath);
            if (!dbFile.exists()) {
                Log.e(TAG, "Database file not found");
                return Result.failure();
            }
            
            // Create backup file name with timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String timestamp = sdf.format(new Date());
            String fileName = "runnerup_backup_" + timestamp + ".db";
            
            // Use DriveBackupManager's backup logic
            CountDownLatch latch = new CountDownLatch(1);
            final boolean[] success = {false};
            final String[] errorMessage = {null};
            
            DriveBackupManager backupManager = new DriveBackupManager(getApplicationContext(), null);
            backupManager.backupDatabase(new DriveBackupManager.BackupCallback() {
                @Override
                public void onBackupSuccess(String message) {
                    Log.d(TAG, "Backup successful: " + message);
                    success[0] = true;
                    latch.countDown();
                }
                
                @Override
                public void onBackupError(String error) {
                    Log.e(TAG, "Backup error: " + error);
                    errorMessage[0] = error;
                    success[0] = false;
                    latch.countDown();
                }
                
                @Override
                public void onProgress(String message) {
                    Log.d(TAG, "Backup progress: " + message);
                }
                
                @Override
                public void onAuthRequired(android.content.Intent intent) {
                    Log.w(TAG, "Auth required - backup folder may need to be reselected");
                    errorMessage[0] = "Backup folder permission lost. Please reselect the folder in settings.";
                    success[0] = false;
                    latch.countDown();
                }
            });
            
            // Wait for backup to complete (with timeout)
            boolean completed = latch.await(60, TimeUnit.SECONDS);
            if (!completed) {
                Log.e(TAG, "Backup timeout");
                return Result.retry(); // Retry later
            }
            
            if (success[0]) {
                return Result.success();
            } else {
                // If Drive backup fails, fallback to local backup
                Log.w(TAG, "Drive backup failed, falling back to local backup: " + errorMessage[0]);
                return backupLocally();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Backup interrupted", e);
            return Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Backup to Drive failed", e);
            // Fallback to local backup
            return backupLocally();
        }
    }
    
    private Result backupLocally() {
        try {
            Log.d(TAG, "Backing up to local storage");
            
            // Get database path
            String dbPath = DBHelper.getDbPath(getApplicationContext());
            File dbFile = new File(dbPath);
            if (!dbFile.exists()) {
                Log.e(TAG, "Database file not found");
                return Result.failure();
            }
            
            // Create backup file name with timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String timestamp = sdf.format(new Date());
            String fileName = "runnerup_backup_" + timestamp + ".db";
            
            // Save to app's external files directory (no permissions needed)
            File backupDir = getApplicationContext().getExternalFilesDir("backups");
            if (backupDir == null) {
                backupDir = new File(getApplicationContext().getExternalFilesDir(null), "backups");
            }
            backupDir.mkdirs();
            
            File backupFile = new File(backupDir, fileName);
            
            // Copy database to backup file
            try (InputStream in = new FileInputStream(dbFile);
                 OutputStream out = new FileOutputStream(backupFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }
            
            Log.d(TAG, "Backup saved locally to: " + backupFile.getAbsolutePath());
            
            // Save last backup time
            android.content.SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences("drive_backup_prefs", Context.MODE_PRIVATE);
            prefs.edit().putLong("last_backup_time", System.currentTimeMillis()).apply();
            
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Local backup failed", e);
            return Result.failure();
        }
    }
}
