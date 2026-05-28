package org.runnerup.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import org.runnerup.common.util.Constants;

/** Shared queries for running activities and their laps. */
public final class RunningActivityReader {

  private RunningActivityReader() {}

  /** Activity summary used by analytics calculators. */
  public static final class ActivityRow {
    public final long activityId;
    public final long startTime;
    public final double totalDistance;
    public final long totalTime;
    public final int avgHr;
    public final int maxHr;

    public ActivityRow(
        long activityId,
        long startTime,
        double totalDistance,
        long totalTime,
        int avgHr,
        int maxHr) {
      this.activityId = activityId;
      this.startTime = startTime;
      this.totalDistance = totalDistance;
      this.totalTime = totalTime;
      this.avgHr = avgHr;
      this.maxHr = maxHr;
    }
  }

  /** Lap row used by analytics calculators. */
  public static final class LapRow {
    public final int lapNumber;
    public final long timeSeconds;
    public final double distanceM;
    public final int avgHr;

    public LapRow(int lapNumber, long timeSeconds, double distanceM, int avgHr) {
      this.lapNumber = lapNumber;
      this.timeSeconds = timeSeconds;
      this.distanceM = distanceM;
      this.avgHr = avgHr;
    }
  }

  /** Returns running activity IDs ordered by start time descending. */
  public static List<Long> getRunningActivityIds(SQLiteDatabase db) {
    List<Long> activityIds = new ArrayList<>();

    String[] columns = {Constants.DB.PRIMARY_KEY};
    String selection =
        Constants.DB.ACTIVITY.SPORT + " = ? AND " + Constants.DB.ACTIVITY.DELETED + " = ?";
    String[] selectionArgs = {
      String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING), "0"
    };
    String orderBy = Constants.DB.ACTIVITY.START_TIME + " DESC";

    try (Cursor cursor =
        db.query(
            Constants.DB.ACTIVITY.TABLE,
            columns,
            selection,
            selectionArgs,
            null,
            null,
            orderBy)) {
      while (cursor.moveToNext()) {
        activityIds.add(cursor.getLong(0));
      }
    }

    return activityIds;
  }

  /** Latest running activity ID, or 0 if none. */
  public static long getLatestRunningActivityId(SQLiteDatabase db) {
    String latestSql =
        "SELECT MAX("
            + Constants.DB.PRIMARY_KEY
            + ") FROM "
            + Constants.DB.ACTIVITY.TABLE
            + " WHERE "
            + Constants.DB.ACTIVITY.SPORT
            + " = ? AND "
            + Constants.DB.ACTIVITY.DELETED
            + " = ?";

    try (Cursor cursor =
        db.rawQuery(
            latestSql,
            new String[] {
              String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING), "0"
            })) {
      if (cursor.moveToFirst()) {
        return cursor.getLong(0);
      }
    }
    return 0;
  }

  /** Activity with distance, time, and heart rate. */
  public static ActivityRow getActivityWithHr(SQLiteDatabase db, long activityId) {
    String[] columns = {
      Constants.DB.ACTIVITY.START_TIME,
      Constants.DB.ACTIVITY.DISTANCE,
      Constants.DB.ACTIVITY.TIME,
      Constants.DB.ACTIVITY.AVG_HR,
      Constants.DB.ACTIVITY.MAX_HR
    };

    try (Cursor cursor =
        db.query(
            Constants.DB.ACTIVITY.TABLE,
            columns,
            Constants.DB.PRIMARY_KEY + " = ?",
            new String[] {String.valueOf(activityId)},
            null,
            null,
            null)) {

      if (cursor.moveToFirst()) {
        return new ActivityRow(
            activityId,
            cursor.getLong(0),
            cursor.getDouble(1),
            cursor.getLong(2),
            cursor.getInt(3),
            cursor.getInt(4));
      }
    }

    return null;
  }

  /** Activity with distance and time only (no HR). */
  public static ActivityRow getActivity(SQLiteDatabase db, long activityId) {
    String[] columns = {
      Constants.DB.ACTIVITY.START_TIME,
      Constants.DB.ACTIVITY.DISTANCE,
      Constants.DB.ACTIVITY.TIME
    };

    try (Cursor cursor =
        db.query(
            Constants.DB.ACTIVITY.TABLE,
            columns,
            Constants.DB.PRIMARY_KEY + " = ?",
            new String[] {String.valueOf(activityId)},
            null,
            null,
            null)) {

      if (cursor.moveToFirst()) {
        return new ActivityRow(
            activityId,
            cursor.getLong(0),
            cursor.getDouble(1),
            cursor.getLong(2),
            0,
            0);
      }
    }

    return null;
  }

  /** Laps with average HR. */
  public static List<LapRow> getLapsWithHr(SQLiteDatabase db, long activityId) {
    List<LapRow> laps = new ArrayList<>();

    String[] columns = {
      Constants.DB.LAP.LAP,
      Constants.DB.LAP.TIME,
      Constants.DB.LAP.DISTANCE,
      Constants.DB.LAP.AVG_HR
    };

    String selection = Constants.DB.LAP.ACTIVITY + " = ?";
    String[] selectionArgs = {String.valueOf(activityId)};
    String orderBy = Constants.DB.LAP.LAP + " ASC";

    try (Cursor cursor =
        db.query(
            Constants.DB.LAP.TABLE,
            columns,
            selection,
            selectionArgs,
            null,
            null,
            orderBy)) {
      while (cursor.moveToNext()) {
        laps.add(
            new LapRow(
                cursor.getInt(0),
                cursor.getLong(1),
                cursor.getDouble(2),
                cursor.getInt(3)));
      }
    }

    return laps;
  }

  /** Laps without HR. */
  public static List<LapRow> getLaps(SQLiteDatabase db, long activityId) {
    List<LapRow> laps = new ArrayList<>();

    String[] columns = {
      Constants.DB.LAP.LAP, Constants.DB.LAP.TIME, Constants.DB.LAP.DISTANCE
    };

    String selection = Constants.DB.LAP.ACTIVITY + " = ?";
    String[] selectionArgs = {String.valueOf(activityId)};
    String orderBy = Constants.DB.LAP.LAP + " ASC";

    try (Cursor cursor =
        db.query(
            Constants.DB.LAP.TABLE,
            columns,
            selection,
            selectionArgs,
            null,
            null,
            orderBy)) {
      while (cursor.moveToNext()) {
        laps.add(
            new LapRow(cursor.getInt(0), cursor.getLong(1), cursor.getDouble(2), 0));
      }
    }

    return laps;
  }

  /** Laps with positive time and distance (for distribution charts). */
  public static List<LapRow> getLapsWithDistance(SQLiteDatabase db, long activityId) {
    List<LapRow> laps = new ArrayList<>();

    String[] columns = {Constants.DB.LAP.TIME, Constants.DB.LAP.DISTANCE};
    String selection = Constants.DB.LAP.ACTIVITY + " = ?";
    String[] selectionArgs = {String.valueOf(activityId)};

    try (Cursor cursor =
        db.query(
            Constants.DB.LAP.TABLE,
            columns,
            selection,
            selectionArgs,
            null,
            null,
            Constants.DB.LAP.LAP + " ASC")) {
      int lapNumber = 0;
      while (cursor.moveToNext()) {
        long timeSeconds = cursor.getLong(0);
        double distanceM = cursor.getDouble(1);
        if (timeSeconds > 0 && distanceM > 0) {
          laps.add(new LapRow(lapNumber++, timeSeconds, distanceM, 0));
        }
      }
    }

    return laps;
  }
}
