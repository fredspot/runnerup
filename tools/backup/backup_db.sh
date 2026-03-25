#!/bin/bash
# Backup script for RunnerUp database
# Run this after authorizing your device with adb

set -e

BACKUP_DIR="db_backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DB_NAME="runnerup_backup_${TIMESTAMP}.db"

echo "Creating backup directory..."
mkdir -p "$BACKUP_DIR"

echo "Checking device connection..."
if ! adb devices | grep -q "device$"; then
    echo "Error: No authorized device found. Please:"
    echo "1. Check that your device is connected"
    echo "2. Authorize the computer on your device if prompted"
    echo "3. Run 'adb devices' to verify"
    exit 1
fi

echo "Backing up database from device..."
# Try to pull the database directly
if adb pull "/data/data/org.runnerup.debug/databases/runnerup.db" "$BACKUP_DIR/$DB_NAME" 2>/dev/null; then
    echo "Database backed up to: $BACKUP_DIR/$DB_NAME"
    ls -lh "$BACKUP_DIR/$DB_NAME"
    exit 0
fi

# If direct pull fails, try using run-as
echo "Trying alternative method with run-as..."
adb shell "run-as org.runnerup.debug cp databases/runnerup.db databases/runnerup.db.backup_${TIMESTAMP}" 2>/dev/null
adb shell "run-as org.runnerup.debug cat databases/runnerup.db.backup_${TIMESTAMP}" > "$BACKUP_DIR/$DB_NAME" 2>/dev/null

if [ -f "$BACKUP_DIR/$DB_NAME" ] && [ -s "$BACKUP_DIR/$DB_NAME" ]; then
    echo "Database backed up to: $BACKUP_DIR/$DB_NAME"
    ls -lh "$BACKUP_DIR/$DB_NAME"
    # Clean up temporary file on device
    adb shell "run-as org.runnerup.debug rm databases/runnerup.db.backup_${TIMESTAMP}" 2>/dev/null
else
    echo "Error: Failed to backup database"
    echo "You can also use the app's built-in backup feature in Settings > Maintenance"
    exit 1
fi
