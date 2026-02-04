# Google Drive Backup Setup Guide (FREE - No Setup Required!)

Google Drive backup now uses Android's Storage Access Framework (SAF), which allows you to backup directly to Google Drive **without any OAuth setup or Google Cloud Console configuration**. This is completely free and requires no developer credentials.

## How It Works

The app uses Android's built-in folder picker to let you select a folder in your Google Drive. Once you grant permission, the app can save backup files directly to that folder. Your Google Drive app handles the sync automatically.

## Setup Steps

1. Open RunnerUp app
2. Go to **Settings** > **Maintenance** > **Google Drive Backup**
3. Tap **"Select backup folder"**
4. In the folder picker, navigate to **Google Drive** (or your preferred cloud storage)
5. Select or create a folder for backups (e.g., "RunnerUp Backups")
6. Tap **"Allow"** to grant permission
7. You're done! The folder is now saved and you can use "Backup Now"

## Using Backup

- **Manual Backup**: Tap "Backup Now" to create an immediate backup
- **Automatic Backup**: Enable "Automatic backup" to have backups created automatically (requires WorkManager to be implemented)

## How It's Different from OAuth

**Old method (OAuth - requires setup):**
- Required Google Cloud Console project
- Required OAuth 2.0 credentials
- Required SHA-1 fingerprint registration
- Required package name configuration

**New method (SAF - free, no setup):**
- ✅ No setup required
- ✅ Works immediately
- ✅ No developer credentials needed
- ✅ Uses Android's built-in file access
- ✅ Works with any cloud storage (not just Drive)

## Troubleshooting

**"Backup folder not selected"**
- Make sure you've selected a folder using "Select backup folder"
- You may need to grant permission again if you uninstalled the app

**"Permission denied"**
- Select the folder again to re-grant permission
- Make sure you're selecting a folder, not a file

**Backups not appearing in Drive**
- Wait a few moments for Google Drive to sync
- Check your Google Drive app sync status
- Make sure you have internet connection
