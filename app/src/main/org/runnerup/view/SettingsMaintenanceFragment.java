package org.runnerup.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.format.DateFormat;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;
import java.util.List;
import java.util.Locale;
import org.runnerup.R;
import org.runnerup.db.DBHelper;
import org.runnerup.util.DriveBackupManager;

public class SettingsMaintenanceFragment extends PreferenceFragmentCompat {
  
  private static final int REQUEST_ACCOUNT_PICKER = 1000;
  private static final int REQUEST_AUTHORIZATION = 1001;
  
  private Preference statusPreference;
  private Preference signInPreference;
  private Preference backupNowPreference;
  private SwitchPreference autoBackupPreference;
  private ProgressDialog progressDialog;
  private ActivityResultLauncher<Intent> folderPickerLauncher;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    // Register for activity result to handle folder picker (for Google Drive folder selection)
    folderPickerLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
          if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Uri treeUri = result.getData().getData();
            if (treeUri != null) {
              try {
                // Grant persistent permission
                requireContext().getContentResolver().takePersistableUriPermission(
                    treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                
                // Save the folder URI
                DriveBackupManager.setBackupUri(requireContext(), treeUri);
                updateDriveBackupStatus();
                Toast.makeText(requireContext(), "Backup folder selected successfully", Toast.LENGTH_SHORT).show();
              } catch (SecurityException e) {
                Toast.makeText(requireContext(), "Failed to grant permission. Please try again.", Toast.LENGTH_LONG).show();
              }
            } else {
              Toast.makeText(requireContext(), "Failed to select folder", Toast.LENGTH_SHORT).show();
            }
          } else {
            Toast.makeText(requireContext(), "Folder selection cancelled", Toast.LENGTH_SHORT).show();
          }
        });
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.settings_maintenance, rootKey);
    Resources res = getResources();
    {
      Preference btn = findPreference(res.getString(R.string.pref_exportdb));
      btn.setOnPreferenceClickListener(onExportClick);
    }
    {
      Preference btn = findPreference(res.getString(R.string.pref_importdb));
      btn.setOnPreferenceClickListener(onImportClick);
    }
    {
      Preference btn = findPreference(res.getString(R.string.pref_prunedb));
      btn.setOnPreferenceClickListener(onPruneClick);
    }

    String path = DBHelper.getDefaultBackupPath(requireContext());
    findPreference(res.getString(org.runnerup.common.R.string.Maintenance_explanation_summary))
        .setSummary(
            String.format(
                Locale.getDefault(),
                res.getString(org.runnerup.common.R.string.Maintenance_explanation_summary),
                path));
    
    // Setup automatic backup preferences
    setupAutomaticBackupPreferences();
    
    // Setup Drive backup preferences
    setupDriveBackupPreferences();
  }
  
  @Override
  public void onResume() {
    super.onResume();
    updateAutomaticBackupStatus();
    updateDriveBackupStatus();
  }
  
  private void setupAutomaticBackupPreferences() {
    Preference statusPref = findPreference("pref_auto_backup_status");
    Preference backupNowPref = findPreference("pref_auto_backup_now");
    Preference restorePref = findPreference("pref_auto_backup_restore");
    Preference sharePref = findPreference("pref_auto_backup_share");
    
    backupNowPref.setOnPreferenceClickListener(pref -> {
      performAutomaticBackup();
      return true;
    });
    
    restorePref.setOnPreferenceClickListener(pref -> {
      showRestoreBackupDialog();
      return true;
    });
    
    sharePref.setOnPreferenceClickListener(pref -> {
      showShareBackupDialog();
      return true;
    });
    
    updateAutomaticBackupStatus();
  }
  
  private void updateAutomaticBackupStatus() {
    Preference statusPref = findPreference("pref_auto_backup_status");
    if (statusPref == null) return;
    
    long lastBackup = org.runnerup.util.AutomaticBackupManager.getLastBackupTime(requireContext());
    int backupCount = org.runnerup.util.AutomaticBackupManager.getBackupCount(requireContext());
    List<org.runnerup.util.AutomaticBackupManager.BackupInfo> backups = 
        org.runnerup.util.AutomaticBackupManager.getBackups(requireContext());
    
    String summary = "Automatic backups are enabled\n";
    summary += "Total backups: " + backupCount + "\n";
    summary += "Available backups: " + backups.size() + "\n";
    if (lastBackup > 0) {
      summary += "Last backup: " + DateFormat.format("yyyy-MM-dd HH:mm", lastBackup);
    } else {
      summary += "No backups created yet";
    }
    
    statusPref.setSummary(summary);
  }
  
  private void performAutomaticBackup() {
    progressDialog = new ProgressDialog(requireContext());
    progressDialog.setTitle("Creating Backup");
    progressDialog.setMessage("Please wait...");
    progressDialog.setCancelable(false);
    progressDialog.show();
    
    // Run backup in background
    new Thread(() -> {
      boolean success = org.runnerup.util.AutomaticBackupManager.createBackup(requireContext(), true);
      
      requireActivity().runOnUiThread(() -> {
        if (progressDialog != null && progressDialog.isShowing()) {
          progressDialog.dismiss();
        }
        
        if (success) {
          Toast.makeText(requireContext(), "Backup created successfully", Toast.LENGTH_SHORT).show();
          updateAutomaticBackupStatus();
        } else {
          Toast.makeText(requireContext(), "Failed to create backup", Toast.LENGTH_LONG).show();
        }
      });
    }).start();
  }
  
  private void showRestoreBackupDialog() {
    List<org.runnerup.util.AutomaticBackupManager.BackupInfo> backups = 
        org.runnerup.util.AutomaticBackupManager.getBackups(requireContext());
    
    if (backups.isEmpty()) {
      new AlertDialog.Builder(requireContext())
          .setTitle("No Backups Available")
          .setMessage("No automatic backups found. Backups are created automatically after each run and before imports.")
          .setPositiveButton("OK", null)
          .show();
      return;
    }
    
    // Create list of backup names with dates
    String[] backupNames = new String[backups.size()];
    for (int i = 0; i < backups.size(); i++) {
      org.runnerup.util.AutomaticBackupManager.BackupInfo info = backups.get(i);
      backupNames[i] = info.getFormattedDate() + " (" + info.getFormattedSize() + ")";
    }
    
    new AlertDialog.Builder(requireContext())
        .setTitle("Restore from Backup")
        .setMessage("Select a backup to restore. A new backup will be created before restoring.")
        .setItems(backupNames, (dialog, which) -> {
          org.runnerup.util.AutomaticBackupManager.BackupInfo selected = backups.get(which);
          confirmRestore(selected);
        })
        .setNegativeButton("Cancel", null)
        .show();
  }
  
  private void confirmRestore(org.runnerup.util.AutomaticBackupManager.BackupInfo backup) {
    new AlertDialog.Builder(requireContext())
        .setTitle("Confirm Restore")
        .setMessage("Restore database from backup?\n\n" +
            "Date: " + backup.getFormattedDate() + "\n" +
            "Size: " + backup.getFormattedSize() + "\n\n" +
            "A backup of your current database will be created first.\n\n" +
            "The app will need to be restarted after restore.")
        .setPositiveButton("Restore", (dialog, which) -> {
          performRestore(backup);
        })
        .setNegativeButton("Cancel", null)
        .show();
  }
  
  private void performRestore(org.runnerup.util.AutomaticBackupManager.BackupInfo backup) {
    progressDialog = new ProgressDialog(requireContext());
    progressDialog.setTitle("Restoring Backup");
    progressDialog.setMessage("Please wait...");
    progressDialog.setCancelable(false);
    progressDialog.show();
    
    // Run restore in background
    new Thread(() -> {
      boolean success = org.runnerup.util.AutomaticBackupManager.restoreBackup(requireContext(), backup.file);
      
      requireActivity().runOnUiThread(() -> {
        if (progressDialog != null && progressDialog.isShowing()) {
          progressDialog.dismiss();
        }
        
        if (success) {
          new AlertDialog.Builder(requireContext())
              .setTitle("Restore Complete")
              .setMessage("Database restored successfully. Please restart the app to use the restored database.")
              .setPositiveButton("OK", (dialog, which) -> {
                // Exit app
                requireActivity().finish();
                System.exit(0);
              })
              .setCancelable(false)
              .show();
        } else {
          Toast.makeText(requireContext(), "Failed to restore backup", Toast.LENGTH_LONG).show();
        }
      });
    }).start();
  }
  
  private void showShareBackupDialog() {
    List<org.runnerup.util.AutomaticBackupManager.BackupInfo> backups = 
        org.runnerup.util.AutomaticBackupManager.getBackups(requireContext());
    
    if (backups.isEmpty()) {
      new AlertDialog.Builder(requireContext())
          .setTitle("No Backups Available")
          .setMessage("No automatic backups found. Create a backup first, or wait for automatic backup after your next run.")
          .setPositiveButton("OK", null)
          .show();
      return;
    }
    
    // Create list of backup names with dates
    String[] backupNames = new String[backups.size()];
    for (int i = 0; i < backups.size(); i++) {
      org.runnerup.util.AutomaticBackupManager.BackupInfo info = backups.get(i);
      backupNames[i] = info.getFormattedDate() + " (" + info.getFormattedSize() + ")";
    }
    
    new AlertDialog.Builder(requireContext())
        .setTitle("Share Backup - Select a backup to share")
        .setItems(backupNames, (dialog, which) -> {
          org.runnerup.util.AutomaticBackupManager.BackupInfo selected = backups.get(which);
          shareBackup(selected);
        })
        .setNegativeButton("Cancel", null)
        .show();
  }
  
  private void shareBackup(org.runnerup.util.AutomaticBackupManager.BackupInfo backup) {
    try {
      // Create URI for the backup file using BackupFileProvider
      String authority = requireContext().getPackageName() + ".backup.file.provider";
      Uri backupUri = Uri.parse("content://" + authority + "/" + backup.file.getName());
      
      // Create share intent
      Intent shareIntent = new Intent(Intent.ACTION_SEND);
      shareIntent.setType("application/x-sqlite3");
      shareIntent.putExtra(Intent.EXTRA_STREAM, backupUri);
      shareIntent.putExtra(Intent.EXTRA_SUBJECT, "RunnerUp Backup - " + backup.getFormattedDate());
      shareIntent.putExtra(Intent.EXTRA_TEXT, 
          "RunnerUp database backup\n\n" +
          "Date: " + backup.getFormattedDate() + "\n" +
          "Size: " + backup.getFormattedSize() + "\n\n" +
          "To restore this backup:\n" +
          "1. Save this file to your device\n" +
          "2. Open RunnerUp app\n" +
          "3. Go to Settings > Maintenance > Restore from Backup\n" +
          "4. Select this backup file");
      
      // Grant temporary permission to read the file
      shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      
      // Show share chooser
      Intent chooser = Intent.createChooser(shareIntent, "Share Backup");
      chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      
      startActivity(chooser);
    } catch (Exception e) {
      Toast.makeText(requireContext(), "Failed to share backup: " + e.getMessage(), Toast.LENGTH_LONG).show();
      android.util.Log.e("SettingsMaintenanceFragment", "Error sharing backup", e);
    }
  }
  
  private void setupDriveBackupPreferences() {
    statusPreference = findPreference("pref_drive_backup_status");
    signInPreference = findPreference("pref_drive_backup_signin");
    backupNowPreference = findPreference("pref_drive_backup_now");
    autoBackupPreference = findPreference("pref_drive_auto_backup");
    
    signInPreference.setOnPreferenceClickListener(pref -> {
      chooseAccount();
      return true;
    });
    
    backupNowPreference.setOnPreferenceClickListener(pref -> {
      performBackup();
      return true;
    });
    
    autoBackupPreference.setOnPreferenceChangeListener((pref, newValue) -> {
      boolean enabled = (Boolean) newValue;
      DriveBackupManager.setBackupEnabled(requireContext(), enabled);
      DriveBackupManager.scheduleAutomaticBackup(requireContext(), enabled);
      updateDriveBackupStatus();
      return true;
    });
    
    updateDriveBackupStatus();
  }
  
  private void updateDriveBackupStatus() {
    Uri backupUri = DriveBackupManager.getBackupUri(requireContext());
    boolean enabled = DriveBackupManager.isBackupEnabled(requireContext());
    long lastBackup = DriveBackupManager.getLastBackupTime(requireContext());
    
    if (backupUri != null) {
      String folderName = backupUri.getLastPathSegment();
      statusPreference.setSummary("Folder: " + (folderName != null ? folderName : "Selected") + 
          (lastBackup > 0 ? "\nLast backup: " + DateFormat.format("yyyy-MM-dd HH:mm", lastBackup) : ""));
      signInPreference.setTitle("Change folder");
      backupNowPreference.setEnabled(true);
      autoBackupPreference.setEnabled(true);
    } else {
      statusPreference.setSummary("No backup folder selected");
      signInPreference.setTitle("Select backup folder");
      backupNowPreference.setEnabled(false);
      autoBackupPreference.setEnabled(false);
    }
  }
  
  private void chooseAccount() {
    // Show options since Google Drive may not appear in folder picker
    new AlertDialog.Builder(requireContext())
        .setTitle("Select Backup Location")
        .setMessage("Choose how to backup:\n\n" +
            "1. Save to Downloads folder\n" +
            "   → Then manually upload to Google Drive\n\n" +
            "2. Save directly to Google Drive\n" +
            "   → Opens Drive folder picker\n\n" +
            "3. Save to another cloud storage\n" +
            "   → Select from available options")
        .setNeutralButton("Downloads", (dialog, which) -> {
          // Save to Downloads folder
          android.os.Environment.getExternalStoragePublicDirectory(
              android.os.Environment.DIRECTORY_DOWNLOADS).mkdirs();
          Uri downloadsUri = android.provider.DocumentsContract.buildDocumentUri(
              "com.android.externalstorage.documents",
              "primary:Download");
          
          // Try to get tree URI for Downloads
          try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.putExtra("android.provider.extra.INITIAL_URI", 
                android.net.Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload"));
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            folderPickerLauncher.launch(intent);
          } catch (Exception e) {
            // Fallback to regular picker
            Intent intent = DriveBackupManager.createFolderPickerIntent();
            folderPickerLauncher.launch(intent);
          }
        })
        .setPositiveButton("Drive/Cloud", (dialog, which) -> {
          // Try to open Drive folder picker
          Intent intent = DriveBackupManager.createFolderPickerIntent();
          folderPickerLauncher.launch(intent);
        })
        .setNegativeButton("Cancel", null)
        .show();
  }
  
  private void signOut() {
    DriveBackupManager.setBackupUri(requireContext(), null);
    DriveBackupManager.setAccountName(requireContext(), null);
    updateDriveBackupStatus();
    Toast.makeText(requireContext(), "Backup folder cleared", Toast.LENGTH_SHORT).show();
  }
  
  private void performBackup() {
    Uri backupUri = DriveBackupManager.getBackupUri(requireContext());
    if (backupUri == null) {
      Toast.makeText(requireContext(), "Please select a backup folder first", Toast.LENGTH_SHORT).show();
      chooseAccount(); // Launch folder picker
      return;
    }
    
    // Account name is optional for SAF-based backup
    String accountName = DriveBackupManager.getAccountName(requireContext());
    startBackupProcess(accountName);
  }
  
  private void startBackupProcess(String accountName) {
    
    progressDialog = new ProgressDialog(requireContext());
    progressDialog.setTitle("Backing up to Google Drive");
    progressDialog.setMessage("Preparing...");
    progressDialog.setCancelable(false);
    progressDialog.show();
    
    DriveBackupManager backupManager = new DriveBackupManager(requireContext(), accountName);
    backupManager.backupDatabase(new DriveBackupManager.BackupCallback() {
      @Override
      public void onBackupSuccess(String message) {
        if (progressDialog != null && progressDialog.isShowing()) {
          progressDialog.dismiss();
        }
        
        // Check if backup is in Downloads folder
        Uri backupUri = DriveBackupManager.getBackupUri(requireContext());
        String uriString = backupUri != null ? backupUri.toString().toLowerCase() : "";
        boolean isDownloads = uriString.contains("download");
        
        if (isDownloads) {
          // Offer guidance to upload to Drive if saved to Downloads
          new AlertDialog.Builder(requireContext())
              .setTitle("Backup Successful")
              .setMessage(message + "\n\nSaved to Downloads folder.\n\nTo upload to Google Drive:\n" +
                  "1. Open Google Drive app\n" +
                  "2. Tap '+' → 'Upload'\n" +
                  "3. Find the backup file in Downloads")
              .setPositiveButton("OK", null)
              .show();
        } else {
          Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
        updateDriveBackupStatus();
      }
      
      @Override
      public void onBackupError(String error) {
        if (progressDialog != null && progressDialog.isShowing()) {
          progressDialog.dismiss();
        }
        new AlertDialog.Builder(requireContext())
            .setTitle("Backup Failed")
            .setMessage(error)
            .setPositiveButton("OK", null)
            .show();
      }
      
      @Override
      public void onProgress(String message) {
        if (progressDialog != null && progressDialog.isShowing()) {
          progressDialog.setMessage(message);
        }
      }
      
      @Override
      public void onAuthRequired(Intent intent) {
        if (progressDialog != null && progressDialog.isShowing()) {
          progressDialog.dismiss();
        }
        // Launch folder picker to select backup folder
        folderPickerLauncher.launch(intent);
      }
    });
  }

  private final Preference.OnPreferenceClickListener onExportClick =
      preference -> {
        // TODO Use picker with ACTION_CREATE_DOCUMENT
        DBHelper.exportDatabase(requireContext(), null);
        return false;
      };

  private final Preference.OnPreferenceClickListener onImportClick =
      preference -> {
        // TODO Use picker with ACTION_OPEN_DOCUMENT
        DBHelper.importDatabase(requireContext(), null);
        return false;
      };

  private final Preference.OnPreferenceClickListener onPruneClick =
      preference -> {
        final ProgressDialog dialog = new ProgressDialog(requireContext());
        dialog.setTitle(org.runnerup.common.R.string.Pruning_deleted_activities_from_database);
        dialog.show();
        DBHelper.purgeDeletedActivities(requireContext(), dialog, dialog::dismiss);
        return false;
      };
}
