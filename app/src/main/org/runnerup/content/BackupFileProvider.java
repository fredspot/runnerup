package org.runnerup.content;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;
import org.runnerup.BuildConfig;
import org.runnerup.util.AutomaticBackupManager;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * ContentProvider for sharing backup files via email, cloud storage, etc.
 */
public class BackupFileProvider extends ContentProvider {

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".backup.file.provider";
    public static final String MIME = "application/x-sqlite3";

    private UriMatcher uriMatcher;

    @Override
    public boolean onCreate() {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, "*", 1);
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {

        if (uriMatcher.match(uri) == 1) {
            // Get the filename from the URI
            String fileName = uri.getLastPathSegment();
            if (fileName == null) {
                throw new FileNotFoundException("No filename in URI: " + uri);
            }

            // Get the backup file
            File backupDir = AutomaticBackupManager.getBackupDirectory(getContext());
            File file = new File(backupDir, fileName);

            if (!file.exists()) {
                throw new FileNotFoundException("Backup file not found: " + file.getAbsolutePath());
            }

            // Return read-only access
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        }

        throw new FileNotFoundException("Unsupported uri: " + uri);
    }

    // Not used for this provider
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return MIME;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
