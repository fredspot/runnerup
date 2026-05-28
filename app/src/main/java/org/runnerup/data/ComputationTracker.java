package org.runnerup.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.runnerup.common.util.Constants;

/** Tracks when precomputed analytics were last updated. */
public final class ComputationTracker {

  private static final String TAG = "ComputationTracker";

  public static final String TYPE_BEST_TIMES = "best_times";
  public static final String TYPE_STATISTICS = "statistics";
  public static final String TYPE_HR_ZONES = "hr_zones";
  public static final String TYPE_YEARLY_CUMULATIVE = "yearly_cumulative";

  private ComputationTracker() {}

  /** True when a newer running activity exists than the last tracked ID. */
  public static boolean isStaleByLastActivityId(SQLiteDatabase db, String computationType) {
    try {
      long lastActivityId = getLastActivityId(db, computationType);
      if (lastActivityId < 0) {
        Log.i(TAG, "No computation tracking record for " + computationType + ", stale");
        return true;
      }

      long latestActivityId = RunningActivityReader.getLatestRunningActivityId(db);
      if (latestActivityId <= 0) {
        return true;
      }

      boolean isStale = latestActivityId > lastActivityId;
      Log.i(
          TAG,
          computationType
              + " staleness: last="
              + lastActivityId
              + ", latest="
              + latestActivityId
              + ", stale="
              + isStale);
      return isStale;
    } catch (Exception e) {
      Log.e(TAG, "Error checking staleness for " + computationType, e);
      return true;
    }
  }

  /** True when last computed time is older than maxAgeSeconds. */
  public static boolean isStaleByTimestamp(
      SQLiteDatabase db, String computationType, long maxAgeSeconds) {
    try {
      String sql =
          "SELECT "
              + Constants.DB.COMPUTATION_TRACKING.LAST_COMPUTED_TIME
              + " FROM "
              + Constants.DB.COMPUTATION_TRACKING.TABLE
              + " WHERE "
              + Constants.DB.COMPUTATION_TRACKING.COMPUTATION_TYPE
              + " = ?";

      try (Cursor cursor = db.rawQuery(sql, new String[] {computationType})) {
        if (!cursor.moveToFirst()) {
          return true;
        }
        long lastComputed = cursor.getLong(0);
        long now = System.currentTimeMillis() / 1000;
        return (now - lastComputed) > maxAgeSeconds;
      }
    } catch (Exception e) {
      Log.e(TAG, "Error checking timestamp staleness for " + computationType, e);
      return true;
    }
  }

  public static long getLastActivityId(SQLiteDatabase db, String computationType) {
    String sql =
        "SELECT "
            + Constants.DB.COMPUTATION_TRACKING.LAST_ACTIVITY_ID
            + " FROM "
            + Constants.DB.COMPUTATION_TRACKING.TABLE
            + " WHERE "
            + Constants.DB.COMPUTATION_TRACKING.COMPUTATION_TYPE
            + " = ?";

    try (Cursor cursor = db.rawQuery(sql, new String[] {computationType})) {
      if (cursor.moveToFirst()) {
        return cursor.getLong(0);
      }
    }
    return -1;
  }

  public static void updateLastActivityId(
      SQLiteDatabase db, String computationType, long lastActivityId) {
    try {
      long currentTime = System.currentTimeMillis() / 1000;

      ContentValues values = new ContentValues();
      values.put(Constants.DB.COMPUTATION_TRACKING.COMPUTATION_TYPE, computationType);
      values.put(Constants.DB.COMPUTATION_TRACKING.LAST_COMPUTED_TIME, currentTime);
      values.put(Constants.DB.COMPUTATION_TRACKING.LAST_ACTIVITY_ID, lastActivityId);

      db.replace(Constants.DB.COMPUTATION_TRACKING.TABLE, null, values);

      Log.i(
          TAG,
          "Updated "
              + computationType
              + ": activityId="
              + lastActivityId
              + ", time="
              + currentTime);
    } catch (Exception e) {
      Log.e(TAG, "Error updating tracking for " + computationType, e);
    }
  }

  private static final long ONE_HOUR_MS = 60 * 60 * 1000;

  /** True when {@code lastComputedMillis} is older than one hour. */
  /** True when {@code lastComputedMillis} is in a different calendar month than now. */
  public static boolean isStaleByCalendarMonth(long lastComputedMillis) {
    java.util.Calendar last = java.util.Calendar.getInstance();
    last.setTimeInMillis(lastComputedMillis);
    java.util.Calendar now = java.util.Calendar.getInstance();
    return last.get(java.util.Calendar.YEAR) != now.get(java.util.Calendar.YEAR)
        || last.get(java.util.Calendar.MONTH) != now.get(java.util.Calendar.MONTH);
  }

  public static boolean isStaleOlderThanOneHour(long lastComputedMillis) {
    return lastComputedMillis < System.currentTimeMillis() - ONE_HOUR_MS;
  }

  public static void deleteTracking(SQLiteDatabase db, String... computationTypes) {
    if (computationTypes.length == 0) {
      return;
    }
    StringBuilder placeholders = new StringBuilder();
    for (int i = 0; i < computationTypes.length; i++) {
      if (i > 0) {
        placeholders.append(", ");
      }
      placeholders.append("?");
    }
    db.delete(
        Constants.DB.COMPUTATION_TRACKING.TABLE,
        Constants.DB.COMPUTATION_TRACKING.COMPUTATION_TYPE + " IN (" + placeholders + ")",
        computationTypes);
  }
}
