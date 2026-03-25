package org.runnerup.core.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.runnerup.data.DBHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Simple, reliable automatic backup manager that creates local backups
 * without requiring any setup or configuration.
 */
public class AutomaticBackupManager {
    private static final String TAG = "AutomaticBackupManager";
    private static final String PREFS_NAME = "auto_backup_prefs";
    private static final String PREF_LAST_BACKUP_TIME = "last_backup_time";
    private static final String PREF_BACKUP_COUNT = "backup_count";
    
    // Keep last 10 backups or backups from last 7 days, whichever is more
    private static final int MAX_BACKUPS = 10;
    private static final long BACKUP_RETENTION_DAYS = 7L * 24 * 60 * 60 * 1000; // 7 days in milliseconds
    
    // Minimum time between backups (1 hour) to avoid too frequent backups
    private static final long MIN_BACKUP_INTERVAL = 60L * 60 * 1000; // 1 hour
    
    /**
     * Create an automatic backup if enough time has passed since last backup.
     * This is safe to call frequently - it will only backup if needed.
     */
    public static void createBackupIfNeeded(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long lastBackup = prefs.getLong(PREF_LAST_BACKUP_TIME, 0);
            long now = System.currentTimeMillis();
            
            // Only backup if at least MIN_BACKUP_INTERVAL has passed
            if (now - lastBackup < MIN_BACKUP_INTERVAL) {
                Log.d(TAG, "Skipping backup - too soon since last backup");
                return;
            }
            
            createBackup(context, false);
        } catch (Exception e) {
            Log.e(TAG, "Error checking if backup needed", e);
        }
    }
    
    /**
     * Force create a backup immediately (e.g., before import).
     */
    public static boolean createBackup(Context context, boolean force) {
        try {
            if (!force) {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                long lastBackup = prefs.getLong(PREF_LAST_BACKUP_TIME, 0);
                long now = System.currentTimeMillis();
                
                if (now - lastBackup < MIN_BACKUP_INTERVAL) {
                    Log.d(TAG, "Skipping backup - too soon since last backup");
                    return false;
                }
            }
            
            String dbPath = DBHelper.getDbPath(context);
            File dbFile = new File(dbPath);
            if (!dbFile.exists()) {
                Log.e(TAG, "Database file not found: " + dbPath);
                return false;
            }
            
            // Create backup directory
            File backupDir = getBackupDirectory(context);
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                Log.e(TAG, "Failed to create backup directory: " + backupDir.getAbsolutePath());
                return false;
            }
            
            // Create backup file with timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String timestamp = sdf.format(new Date());
            String fileName = "auto_backup_" + timestamp + ".db";
            File backupFile = new File(backupDir, fileName);
            
            // Copy database file
            try (FileInputStream in = new FileInputStream(dbFile);
                 FileOutputStream out = new FileOutputStream(backupFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                out.flush();
                Log.i(TAG, "Backup created: " + backupFile.getName() + " (" + totalBytes + " bytes)");
            }
            
            // Update last backup time
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                .putLong(PREF_LAST_BACKUP_TIME, System.currentTimeMillis())
                .putInt(PREF_BACKUP_COUNT, getBackupCount(context) + 1)
                .apply();
            
            // Clean up old backups
            cleanupOldBackups(context);
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create backup", e);
            return false;
        }
    }
    
    /**
     * Get the backup directory (in app's external files directory).
     */
    public static File getBackupDirectory(Context context) {
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir == null) {
            // Fallback to internal files directory
            return new File(context.getFilesDir(), "auto_backups");
        }
        return new File(externalFilesDir, "auto_backups");
    }
    
    /**
     * Get list of all available backups, sorted by date (newest first).
     */
    public static List<BackupInfo> getBackups(Context context) {
        List<BackupInfo> backups = new ArrayList<>();
        File backupDir = getBackupDirectory(context);
        
        if (!backupDir.exists()) {
            return backups;
        }
        
        File[] files = backupDir.listFiles((dir, name) -> name.startsWith("auto_backup_") && name.endsWith(".db"));
        if (files == null) {
            return backups;
        }
        
        for (File file : files) {
            try {
                String name = file.getName();
                // Extract timestamp from filename: auto_backup_yyyyMMdd_HHmmss.db
                String timestampStr = name.substring(12, name.length() - 3); // Remove "auto_backup_" and ".db"
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
                Date date = sdf.parse(timestampStr);
                
                backups.add(new BackupInfo(file, date != null ? date.getTime() : file.lastModified()));
            } catch (Exception e) {
                Log.w(TAG, "Error parsing backup file: " + file.getName(), e);
                // Use file modification time as fallback
                backups.add(new BackupInfo(file, file.lastModified()));
            }
        }
        
        // Sort by date (newest first)
        backups.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        
        return backups;
    }
    
    /**
     * Restore database from a backup file.
     */
    public static boolean restoreBackup(Context context, File backupFile) {
        try {
            if (!backupFile.exists()) {
                Log.e(TAG, "Backup file does not exist: " + backupFile.getAbsolutePath());
                return false;
            }
            
            String dbPath = DBHelper.getDbPath(context);
            File dbFile = new File(dbPath);
            
            // Create a backup of current database before restoring
            createBackup(context, true);
            
            // Copy backup file to database location
            try (FileInputStream in = new FileInputStream(backupFile);
                 FileOutputStream out = new FileOutputStream(dbFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }
            
            Log.i(TAG, "Database restored from: " + backupFile.getName());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore backup", e);
            return false;
        }
    }
    
    /**
     * Clean up old backups, keeping only the most recent ones.
     */
    private static void cleanupOldBackups(Context context) {
        try {
            File backupDir = getBackupDirectory(context);
            if (!backupDir.exists()) {
                return;
            }
            
            File[] files = backupDir.listFiles((dir, name) -> name.startsWith("auto_backup_") && name.endsWith(".db"));
            if (files == null || files.length <= MAX_BACKUPS) {
                return;
            }
            
            // Sort by modification time (newest first)
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            
            long cutoffTime = System.currentTimeMillis() - BACKUP_RETENTION_DAYS;
            int deleted = 0;
            
            // Delete old backups
            for (int i = MAX_BACKUPS; i < files.length; i++) {
                File file = files[i];
                // Delete if it's beyond MAX_BACKUPS or older than retention period
                if (file.lastModified() < cutoffTime || i >= MAX_BACKUPS) {
                    if (file.delete()) {
                        deleted++;
                        Log.d(TAG, "Deleted old backup: " + file.getName());
                    }
                }
            }
            
            if (deleted > 0) {
                Log.i(TAG, "Cleaned up " + deleted + " old backup(s)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up old backups", e);
        }
    }
    
    /**
     * Get the number of backups created.
     */
    public static int getBackupCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(PREF_BACKUP_COUNT, 0);
    }
    
    /**
     * Get the last backup time.
     */
    public static long getLastBackupTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(PREF_LAST_BACKUP_TIME, 0);
    }
    
    /**
     * Information about a backup file.
     */
    public static class BackupInfo {
        public final File file;
        public final long timestamp;
        public final long size;
        
        public BackupInfo(File file, long timestamp) {
            this.file = file;
            this.timestamp = timestamp;
            this.size = file.length();
        }
        
        public String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
        
        public String getFormattedSize() {
            if (size < 1024) {
                return size + " B";
            } else if (size < 1024 * 1024) {
                return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
            } else {
                return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0));
            }
        }
    }
}
