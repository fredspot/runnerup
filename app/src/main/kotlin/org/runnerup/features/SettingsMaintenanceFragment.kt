package org.runnerup.features

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import java.util.Locale
import org.runnerup.R
import org.runnerup.core.util.AutomaticBackupManager
import org.runnerup.core.util.BgTasks
import org.runnerup.core.util.DriveBackupManager
import org.runnerup.data.DBHelper

class SettingsMaintenanceFragment : PreferenceFragmentCompat() {

  companion object {
    private const val REQUEST_ACCOUNT_PICKER = 1000
    private const val REQUEST_AUTHORIZATION = 1001
  }

  private var statusPreference: Preference? = null
  private var signInPreference: Preference? = null
  private var backupNowPreference: Preference? = null
  private var autoBackupPreference: SwitchPreference? = null
  private var progressDialog: ProgressDialog? = null
  private lateinit var folderPickerLauncher: ActivityResultLauncher<Intent>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        val treeUri = result.data!!.data
        if (treeUri != null) {
          try {
            requireContext().contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            DriveBackupManager.setBackupUri(requireContext(), treeUri)
            updateDriveBackupStatus()
            Toast.makeText(requireContext(), "Backup folder selected successfully", Toast.LENGTH_SHORT).show()
          } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Failed to grant permission. Please try again.", Toast.LENGTH_LONG).show()
          }
        } else {
          Toast.makeText(requireContext(), "Failed to select folder", Toast.LENGTH_SHORT).show()
        }
      } else {
        Toast.makeText(requireContext(), "Folder selection cancelled", Toast.LENGTH_SHORT).show()
      }
    }
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.settings_maintenance, rootKey)
    val res = resources
    findPreference<Preference>(res.getString(R.string.pref_exportdb))?.setOnPreferenceClickListener(onExportClick)
    findPreference<Preference>(res.getString(R.string.pref_importdb))?.setOnPreferenceClickListener(onImportClick)
    findPreference<Preference>(res.getString(R.string.pref_prunedb))?.setOnPreferenceClickListener(onPruneClick)

    val path = DBHelper.getDefaultBackupPath(requireContext())
    findPreference<Preference>(res.getString(org.runnerup.common.R.string.Maintenance_explanation_summary))
        ?.summary =
        String.format(
            Locale.getDefault(),
            res.getString(org.runnerup.common.R.string.Maintenance_explanation_summary),
            path)

    setupAutomaticBackupPreferences()
    setupDriveBackupPreferences()
  }

  override fun onResume() {
    super.onResume()
    updateAutomaticBackupStatus()
    updateDriveBackupStatus()
  }

  private fun setupAutomaticBackupPreferences() {
    val backupNowPref = findPreference<Preference>("pref_auto_backup_now")
    val restorePref = findPreference<Preference>("pref_auto_backup_restore")
    val sharePref = findPreference<Preference>("pref_auto_backup_share")

    backupNowPref?.setOnPreferenceClickListener {
      performAutomaticBackup()
      true
    }

    restorePref?.setOnPreferenceClickListener {
      showRestoreBackupDialog()
      true
    }

    sharePref?.setOnPreferenceClickListener {
      showShareBackupDialog()
      true
    }

    updateAutomaticBackupStatus()
  }

  private fun updateAutomaticBackupStatus() {
    val statusPref = findPreference<Preference>("pref_auto_backup_status") ?: return

    val lastBackup = AutomaticBackupManager.getLastBackupTime(requireContext())
    val backupCount = AutomaticBackupManager.getBackupCount(requireContext())
    val backups = AutomaticBackupManager.getBackups(requireContext())

    var summary = "Automatic backups are enabled\n"
    summary += "Total backups: $backupCount\n"
    summary += "Available backups: ${backups.size}\n"
    summary += if (lastBackup > 0) {
      "Last backup: ${DateFormat.format("yyyy-MM-dd HH:mm", lastBackup)}"
    } else {
      "No backups created yet"
    }

    statusPref.summary = summary
  }

  private fun performAutomaticBackup() {
    progressDialog = ProgressDialog(requireContext()).apply {
      setTitle("Creating Backup")
      setMessage("Please wait...")
      setCancelable(false)
      show()
    }

    val ctx = requireContext()
    BgTasks.runDb(
        { AutomaticBackupManager.createBackup(ctx, true) },
        { success ->
          if (!isAdded) {
            return@runDb
          }
          if (progressDialog?.isShowing == true) {
            progressDialog?.dismiss()
          }
          if (success == true) {
            Toast.makeText(ctx, "Backup created successfully", Toast.LENGTH_SHORT).show()
            updateAutomaticBackupStatus()
          } else {
            Toast.makeText(ctx, "Failed to create backup", Toast.LENGTH_LONG).show()
          }
        })
  }

  private fun showRestoreBackupDialog() {
    val backups = AutomaticBackupManager.getBackups(requireContext())

    if (backups.isEmpty()) {
      AlertDialog.Builder(requireContext())
          .setTitle("No Backups Available")
          .setMessage("No automatic backups found. Backups are created automatically after each run and before imports.")
          .setPositiveButton("OK", null)
          .show()
      return
    }

    val backupNames = Array(backups.size) { i ->
      val info = backups[i]
      "${info.formattedDate} (${info.formattedSize})"
    }

    AlertDialog.Builder(requireContext())
        .setTitle("Restore from Backup")
        .setMessage("Select a backup to restore. A new backup will be created before restoring.")
        .setItems(backupNames) { _, which ->
          confirmRestore(backups[which])
        }
        .setNegativeButton("Cancel", null)
        .show()
  }

  private fun confirmRestore(backup: AutomaticBackupManager.BackupInfo) {
    AlertDialog.Builder(requireContext())
        .setTitle("Confirm Restore")
        .setMessage(
            "Restore database from backup?\n\n" +
                "Date: ${backup.formattedDate}\n" +
                "Size: ${backup.formattedSize}\n\n" +
                "A backup of your current database will be created first.\n\n" +
                "The app will need to be restarted after restore.")
        .setPositiveButton("Restore") { _, _ ->
          performRestore(backup)
        }
        .setNegativeButton("Cancel", null)
        .show()
  }

  private fun performRestore(backup: AutomaticBackupManager.BackupInfo) {
    progressDialog = ProgressDialog(requireContext()).apply {
      setTitle("Restoring Backup")
      setMessage("Please wait...")
      setCancelable(false)
      show()
    }

    val ctx = requireContext()
    val backupFile = backup.file
    BgTasks.runDb(
        { AutomaticBackupManager.restoreBackup(ctx, backupFile) },
        { success ->
          if (!isAdded) {
            return@runDb
          }
          if (progressDialog?.isShowing == true) {
            progressDialog?.dismiss()
          }
          if (success == true) {
            AlertDialog.Builder(ctx)
                .setTitle("Restore Complete")
                .setMessage(
                    "Database restored successfully. Please restart the app to use the restored" +
                        " database.")
                .setPositiveButton("OK") { _, _ ->
                  requireActivity().finish()
                  System.exit(0)
                }
                .setCancelable(false)
                .show()
          } else {
            Toast.makeText(ctx, "Failed to restore backup", Toast.LENGTH_LONG).show()
          }
        })
  }

  private fun showShareBackupDialog() {
    val backups = AutomaticBackupManager.getBackups(requireContext())

    if (backups.isEmpty()) {
      AlertDialog.Builder(requireContext())
          .setTitle("No Backups Available")
          .setMessage("No automatic backups found. Create a backup first, or wait for automatic backup after your next run.")
          .setPositiveButton("OK", null)
          .show()
      return
    }

    val backupNames = Array(backups.size) { i ->
      val info = backups[i]
      "${info.formattedDate} (${info.formattedSize})"
    }

    AlertDialog.Builder(requireContext())
        .setTitle("Share Backup - Select a backup to share")
        .setItems(backupNames) { _, which ->
          shareBackup(backups[which])
        }
        .setNegativeButton("Cancel", null)
        .show()
  }

  private fun shareBackup(backup: AutomaticBackupManager.BackupInfo) {
    try {
      val authority = requireContext().packageName + ".backup.file.provider"
      val backupUri = Uri.parse("content://$authority/${backup.file.name}")

      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/x-sqlite3"
        putExtra(Intent.EXTRA_STREAM, backupUri)
        putExtra(Intent.EXTRA_SUBJECT, "RunnerUp Backup - ${backup.formattedDate}")
        putExtra(
            Intent.EXTRA_TEXT,
            "RunnerUp database backup\n\n" +
                "Date: ${backup.formattedDate}\n" +
                "Size: ${backup.formattedSize}\n\n" +
                "To restore this backup:\n" +
                "1. Save this file to your device\n" +
                "2. Open RunnerUp app\n" +
                "3. Go to Settings > Maintenance > Restore from Backup\n" +
                "4. Select this backup file")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      val chooser = Intent.createChooser(shareIntent, "Share Backup").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      startActivity(chooser)
    } catch (e: Exception) {
      Toast.makeText(requireContext(), "Failed to share backup: ${e.message}", Toast.LENGTH_LONG).show()
      Log.e("SettingsMaintenanceFragment", "Error sharing backup", e)
    }
  }

  private fun setupDriveBackupPreferences() {
    statusPreference = findPreference("pref_drive_backup_status")
    signInPreference = findPreference("pref_drive_backup_signin")
    backupNowPreference = findPreference("pref_drive_backup_now")
    autoBackupPreference = findPreference("pref_drive_auto_backup")

    signInPreference?.setOnPreferenceClickListener {
      chooseAccount()
      true
    }

    backupNowPreference?.setOnPreferenceClickListener {
      performBackup()
      true
    }

    autoBackupPreference?.setOnPreferenceChangeListener { _, newValue ->
      val enabled = newValue as Boolean
      DriveBackupManager.setBackupEnabled(requireContext(), enabled)
      DriveBackupManager.scheduleAutomaticBackup(requireContext(), enabled)
      updateDriveBackupStatus()
      true
    }

    updateDriveBackupStatus()
  }

  private fun updateDriveBackupStatus() {
    val backupUri = DriveBackupManager.getBackupUri(requireContext())
    val lastBackup = DriveBackupManager.getLastBackupTime(requireContext())

    if (backupUri != null) {
      val folderName = backupUri.lastPathSegment
      statusPreference?.summary =
          "Folder: ${folderName ?: "Selected"}" +
              if (lastBackup > 0) "\nLast backup: ${DateFormat.format("yyyy-MM-dd HH:mm", lastBackup)}" else ""
      signInPreference?.title = "Change folder"
      backupNowPreference?.isEnabled = true
      autoBackupPreference?.isEnabled = true
    } else {
      statusPreference?.summary = "No backup folder selected"
      signInPreference?.title = "Select backup folder"
      backupNowPreference?.isEnabled = false
      autoBackupPreference?.isEnabled = false
    }
  }

  private fun chooseAccount() {
    AlertDialog.Builder(requireContext())
        .setTitle("Select Backup Location")
        .setMessage(
            "Choose how to backup:\n\n" +
                "1. Save to Downloads folder\n" +
                "   → Then manually upload to Google Drive\n\n" +
                "2. Save directly to Google Drive\n" +
                "   → Opens Drive folder picker\n\n" +
                "3. Save to another cloud storage\n" +
                "   → Select from available options")
        .setNeutralButton("Downloads") { _, _ ->
          android.os.Environment.getExternalStoragePublicDirectory(
              android.os.Environment.DIRECTORY_DOWNLOADS).mkdirs()

          try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
              putExtra(
                  "android.provider.extra.INITIAL_URI",
                  Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload"))
              addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
              addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            folderPickerLauncher.launch(intent)
          } catch (e: Exception) {
            val intent = DriveBackupManager.createFolderPickerIntent()
            folderPickerLauncher.launch(intent)
          }
        }
        .setPositiveButton("Drive/Cloud") { _, _ ->
          val intent = DriveBackupManager.createFolderPickerIntent()
          folderPickerLauncher.launch(intent)
        }
        .setNegativeButton("Cancel", null)
        .show()
  }

  private fun signOut() {
    DriveBackupManager.setBackupUri(requireContext(), null)
    DriveBackupManager.setAccountName(requireContext(), null)
    updateDriveBackupStatus()
    Toast.makeText(requireContext(), "Backup folder cleared", Toast.LENGTH_SHORT).show()
  }

  private fun performBackup() {
    val backupUri = DriveBackupManager.getBackupUri(requireContext())
    if (backupUri == null) {
      Toast.makeText(requireContext(), "Please select a backup folder first", Toast.LENGTH_SHORT).show()
      chooseAccount()
      return
    }

    val accountName = DriveBackupManager.getAccountName(requireContext())
    startBackupProcess(accountName)
  }

  private fun startBackupProcess(accountName: String?) {
    progressDialog = ProgressDialog(requireContext()).apply {
      setTitle("Backing up to Google Drive")
      setMessage("Preparing...")
      setCancelable(false)
      show()
    }

    val backupManager = DriveBackupManager(requireContext(), accountName)
    backupManager.backupDatabase(object : DriveBackupManager.BackupCallback {
      override fun onBackupSuccess(message: String) {
        if (progressDialog?.isShowing == true) {
          progressDialog?.dismiss()
        }

        val backupUri = DriveBackupManager.getBackupUri(requireContext())
        val uriString = backupUri?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val isDownloads = uriString.contains("download")

        if (isDownloads) {
          AlertDialog.Builder(requireContext())
              .setTitle("Backup Successful")
              .setMessage(
                  "$message\n\nSaved to Downloads folder.\n\nTo upload to Google Drive:\n" +
                      "1. Open Google Drive app\n" +
                      "2. Tap '+' → 'Upload'\n" +
                      "3. Find the backup file in Downloads")
              .setPositiveButton("OK", null)
              .show()
        } else {
          Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        updateDriveBackupStatus()
      }

      override fun onBackupError(error: String) {
        if (progressDialog?.isShowing == true) {
          progressDialog?.dismiss()
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Backup Failed")
            .setMessage(error)
            .setPositiveButton("OK", null)
            .show()
      }

      override fun onProgress(message: String) {
        if (progressDialog?.isShowing == true) {
          progressDialog?.setMessage(message)
        }
      }

      override fun onAuthRequired(intent: Intent) {
        if (progressDialog?.isShowing == true) {
          progressDialog?.dismiss()
        }
        folderPickerLauncher.launch(intent)
      }
    })
  }

  private val onExportClick = Preference.OnPreferenceClickListener {
    // TODO Use picker with ACTION_CREATE_DOCUMENT
    DBHelper.exportDatabase(requireContext(), null)
    false
  }

  private val onImportClick = Preference.OnPreferenceClickListener {
    // TODO Use picker with ACTION_OPEN_DOCUMENT
    DBHelper.importDatabase(requireContext(), null)
    false
  }

  private val onPruneClick = Preference.OnPreferenceClickListener {
    val dialog = ProgressDialog(requireContext()).apply {
      setTitle(org.runnerup.common.R.string.Pruning_deleted_activities_from_database)
      show()
    }
    DBHelper.purgeDeletedActivities(requireContext(), dialog, dialog::dismiss)
    false
  }
}
