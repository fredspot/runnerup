package org.runnerup.sync;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants;
import org.runnerup.core.workout.FileFormats;
import org.runnerup.data.DBHelper;

/** Default sync accounts and one-off account table migrations for {@link org.runnerup.data.DBHelper}. */
public final class AccountMigrationRegistry {

  private static final String TAG = "AccountMigrationRegistry";

  private AccountMigrationRegistry() {}

  public static void insertDefaultAccounts(SQLiteDatabase db) {
    insertAccount(db, RunKeeperSynchronizer.NAME, 1);
    insertAccount(db, RunningAHEADSynchronizer.NAME, 0);
    insertAccount(db, StravaSynchronizer.NAME, 1);
    insertAccount(db, EndurainSynchronizer.NAME, 1);
    insertAccount(db, RunnerUpLiveSynchronizer.NAME, 0);
    insertAccount(db, FileSynchronizer.NAME, 1);
    insertAccount(db, RunalyzeSynchronizer.NAME, RunalyzeSynchronizer.ENABLED);
    insertAccount(db, DropboxSynchronizer.NAME, 0);
    insertAccount(db, WebDavSynchronizer.NAME, 1);
  }

  public static void migrateFileSynchronizerInfo(SQLiteDatabase db) {
    String[] from = {"_id", Constants.DB.ACCOUNT.FORMAT, Constants.DB.ACCOUNT.AUTH_CONFIG};
    String[] args = {FileSynchronizer.NAME};
    try (Cursor c =
        db.query(
            Constants.DB.ACCOUNT.TABLE,
            from,
            Constants.DB.ACCOUNT.NAME + " = ? and " + Constants.DB.ACCOUNT.AUTH_CONFIG + " is not null",
            args,
            null,
            null,
            null)) {

      if (c.moveToFirst()) {
        ContentValues tmp = DBHelper.get(c);
        String oldAuthConfig = tmp.getAsString(Constants.DB.ACCOUNT.AUTH_CONFIG);
        if (oldAuthConfig.startsWith("/")) {
          tmp.put(Constants.DB.ACCOUNT.URL, oldAuthConfig);
          String authConfig = FileSynchronizer.contentValuesToAuthConfig(tmp);
          tmp = new ContentValues();
          tmp.put(Constants.DB.ACCOUNT.AUTH_CONFIG, authConfig);
          tmp.put(Constants.DB.ACCOUNT.FORMAT, FileFormats.DEFAULT_FORMATS.toString());
          db.update(Constants.DB.ACCOUNT.TABLE, tmp, Constants.DB.ACCOUNT.NAME + " = ?", args);
        } else {
          try {
            JSONObject authcfg = new JSONObject(oldAuthConfig);
            @SuppressWarnings("ConstantConditions")
            String format = authcfg.optString(Constants.DB.ACCOUNT.FORMAT, null);
            if (format != null) {
              authcfg.put(Constants.DB.ACCOUNT.FORMAT, null);
              tmp = new ContentValues();
              tmp.put(Constants.DB.ACCOUNT.AUTH_CONFIG, authcfg.toString());
              tmp.put(Constants.DB.ACCOUNT.FORMAT, format);
              db.update(Constants.DB.ACCOUNT.TABLE, tmp, Constants.DB.ACCOUNT.NAME + " = ?", args);
            }
          } catch (JSONException e) {
            Log.w(TAG, "Failed to parse File auth config", e);
          }
        }
      }
    }
  }

  private static void insertAccount(SQLiteDatabase db, String name, int enabled) {
    insertAccount(db, name, enabled, -1);
  }

  private static void insertAccount(SQLiteDatabase db, String name, int enabled, int flags) {
    ContentValues values = new ContentValues();
    values.put(Constants.DB.ACCOUNT.NAME, name);
    if (enabled >= 0) {
      values.put(Constants.DB.ACCOUNT.ENABLED, enabled);
    }
    if (flags >= 0) {
      values.put(Constants.DB.ACCOUNT.FLAGS, flags);
    }
    values.put(Constants.DB.ACCOUNT.FORMAT, FileFormats.DEFAULT_FORMATS.toString());
    values.put(Constants.DB.ACCOUNT.AUTH_METHOD, "dummy");

    long newId =
        db.insertWithOnConflict(
            Constants.DB.ACCOUNT.TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    if (newId == -1 && values.size() > 1) {
      String[] arr = {values.getAsString(Constants.DB.ACCOUNT.NAME)};
      values.remove(Constants.DB.ACCOUNT.FORMAT);
      values.remove(Constants.DB.ACCOUNT.AUTH_METHOD);
      db.update(Constants.DB.ACCOUNT.TABLE, values, Constants.DB.ACCOUNT.NAME + " = ?", arr);
    }
  }
}
