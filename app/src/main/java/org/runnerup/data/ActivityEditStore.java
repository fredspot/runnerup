package org.runnerup.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.data.ActivityTrackEdit.Point;
import org.runnerup.data.ActivityTrackEdit.TrimPlan;

/** Writes name, clock, trim, and optional distance/duration overrides for one activity. */
public final class ActivityEditStore {

  public enum Status {
    OK,
    NOT_FOUND,
    TRIM_TOO_MUCH
  }

  public static final class Request {
    public final String name;
    public final String comment;
    public final int sport;
    /** Desired start of the original activity, unix seconds, before any trim. */
    public final long newStartSec;
    public final boolean distanceOverride;
    public final double distanceM;
    public final boolean durationOverride;
    public final long durationSec;
    public final double trimStartM;
    public final double trimEndM;

    public Request(
        String name,
        String comment,
        int sport,
        long newStartSec,
        boolean distanceOverride,
        double distanceM,
        boolean durationOverride,
        long durationSec,
        double trimStartM,
        double trimEndM) {
      this.name = name;
      this.comment = comment;
      this.sport = sport;
      this.newStartSec = newStartSec;
      this.distanceOverride = distanceOverride;
      this.distanceM = distanceM;
      this.durationOverride = durationOverride;
      this.durationSec = durationSec;
      this.trimStartM = trimStartM;
      this.trimEndM = trimEndM;
    }
  }

  private ActivityEditStore() {}

  public static Status apply(SQLiteDatabase db, long activityId, Request request) {
    Long currentStart = readStart(db, activityId);
    if (currentStart == null) {
      return Status.NOT_FOUND;
    }
    db.beginTransaction();
    try {
      ContentValues meta = new ContentValues();
      meta.put(DB.ACTIVITY.NAME, request.name == null ? "" : request.name);
      meta.put(DB.ACTIVITY.COMMENT, request.comment == null ? "" : request.comment);
      meta.put(DB.ACTIVITY.SPORT, request.sport);
      db.update(DB.ACTIVITY.TABLE, meta, DB.PRIMARY_KEY + " = ?", new String[] {String.valueOf(activityId)});

      long deltaSec = request.newStartSec - currentStart;
      if (deltaSec != 0) {
        db.execSQL(
            "UPDATE "
                + DB.ACTIVITY.TABLE
                + " SET "
                + DB.ACTIVITY.START_TIME
                + " = "
                + DB.ACTIVITY.START_TIME
                + " + ? WHERE "
                + DB.PRIMARY_KEY
                + " = ?",
            new Object[] {deltaSec, activityId});
        db.execSQL(
            "UPDATE "
                + DB.LOCATION.TABLE
                + " SET "
                + DB.LOCATION.TIME
                + " = "
                + DB.LOCATION.TIME
                + " + ? WHERE "
                + DB.LOCATION.ACTIVITY
                + " = ? AND "
                + DB.LOCATION.TIME
                + " IS NOT NULL",
            new Object[] {deltaSec * 1000L, activityId});
      }

      TrimPlan plan = ActivityTrackEdit.plan(readPoints(db, activityId), request.trimStartM, request.trimEndM);
      if (!plan.ok) {
        return Status.TRIM_TOO_MUCH;
      }
      if (plan.applied) {
        db.execSQL(
            "DELETE FROM "
                + DB.LOCATION.TABLE
                + " WHERE "
                + DB.LOCATION.ACTIVITY
                + " = ? AND "
                + DB.LOCATION.ELAPSED
                + " IS NOT NULL AND ("
                + DB.LOCATION.ELAPSED
                + " < ? OR "
                + DB.LOCATION.ELAPSED
                + " > ?)",
            new Object[] {activityId, plan.minElapsedMs, plan.maxElapsedMs});
        db.execSQL(
            "DELETE FROM "
                + DB.LOCATION.TABLE
                + " WHERE "
                + DB.LOCATION.ACTIVITY
                + " = ? AND "
                + DB.LOCATION.DISTANCE
                + " IS NOT NULL AND ("
                + DB.LOCATION.DISTANCE
                + " < ? OR "
                + DB.LOCATION.DISTANCE
                + " > ?)",
            new Object[] {activityId, plan.keepFromM, plan.keepToM});
        db.execSQL(
            "UPDATE "
                + DB.LOCATION.TABLE
                + " SET "
                + DB.LOCATION.ELAPSED
                + " = "
                + DB.LOCATION.ELAPSED
                + " - ? WHERE "
                + DB.LOCATION.ACTIVITY
                + " = ? AND "
                + DB.LOCATION.ELAPSED
                + " IS NOT NULL",
            new Object[] {plan.minElapsedMs, activityId});
        long shiftedStart = request.newStartSec;
        long newStart =
            plan.firstEpochMs != null ? plan.firstEpochMs / 1000L : shiftedStart + plan.startShiftSec;
        ContentValues start = new ContentValues();
        start.put(DB.ACTIVITY.START_TIME, newStart);
        db.update(
            DB.ACTIVITY.TABLE, start, DB.PRIMARY_KEY + " = ?", new String[] {String.valueOf(activityId)});
        new ActivityCleaner().recompute(db, activityId, true);
      }

      if (request.distanceOverride || request.durationOverride) {
        ContentValues totals = new ContentValues();
        if (request.distanceOverride) {
          totals.put(DB.ACTIVITY.DISTANCE, request.distanceM);
        }
        if (request.durationOverride) {
          totals.put(DB.ACTIVITY.TIME, request.durationSec);
        }
        db.update(
            DB.ACTIVITY.TABLE,
            totals,
            DB.PRIMARY_KEY + " = ?",
            new String[] {String.valueOf(activityId)});
      }
      db.setTransactionSuccessful();
      return Status.OK;
    } finally {
      db.endTransaction();
    }
  }

  private static Long readStart(SQLiteDatabase db, long activityId) {
    try (Cursor cursor =
        db.rawQuery(
            "SELECT "
                + DB.ACTIVITY.START_TIME
                + " FROM "
                + DB.ACTIVITY.TABLE
                + " WHERE "
                + DB.PRIMARY_KEY
                + " = ?",
            new String[] {String.valueOf(activityId)})) {
      if (!cursor.moveToFirst() || cursor.isNull(0)) {
        return null;
      }
      return cursor.getLong(0);
    }
  }

  private static List<Point> readPoints(SQLiteDatabase db, long activityId) {
    List<Point> points = new ArrayList<>();
    String sql =
        "SELECT "
            + DB.PRIMARY_KEY
            + ", "
            + DB.LOCATION.DISTANCE
            + ", "
            + DB.LOCATION.ELAPSED
            + ", "
            + DB.LOCATION.TIME
            + " FROM "
            + DB.LOCATION.TABLE
            + " WHERE "
            + DB.LOCATION.ACTIVITY
            + " = ? AND "
            + DB.LOCATION.DISTANCE
            + " IS NOT NULL AND "
            + DB.LOCATION.ELAPSED
            + " IS NOT NULL";
    try (Cursor cursor = db.rawQuery(sql, new String[] {String.valueOf(activityId)})) {
      while (cursor.moveToNext()) {
        Long epoch = cursor.isNull(3) ? null : cursor.getLong(3);
        points.add(new Point(cursor.getLong(0), cursor.getDouble(1), cursor.getLong(2), epoch));
      }
    }
    return points;
  }
}
