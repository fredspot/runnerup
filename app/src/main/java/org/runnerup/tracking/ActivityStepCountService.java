package org.runnerup.tracking;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import org.runnerup.common.util.Constants;

/** Fills {@code activity.steps} from stored cadence for runs that have no hardware step total. */
public final class ActivityStepCountService {

  private ActivityStepCountService() {}

  public static boolean hasSteps(SQLiteDatabase db, long activityId) {
    try (Cursor cursor =
        db.query(
            Constants.DB.ACTIVITY.TABLE,
            new String[] {Constants.DB.ACTIVITY.STEPS},
            Constants.DB.PRIMARY_KEY + " = ?",
            new String[] {String.valueOf(activityId)},
            null,
            null,
            null)) {
      return cursor.moveToFirst() && !cursor.isNull(0) && cursor.getInt(0) > 0;
    }
  }

  /** Returns how many activities were written. */
  public static int fillMissing(SQLiteDatabase db) {
    List<Long> ids = missingIds(db);
    int written = 0;
    db.beginTransaction();
    try {
      for (long id : ids) {
        if (fillOne(db, id)) {
          written++;
        }
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    return written;
  }

  public static boolean ensureFilled(SQLiteDatabase db, long activityId) {
    if (hasSteps(db, activityId)) {
      return false;
    }
    return fillOne(db, activityId);
  }

  static boolean fillOne(SQLiteDatabase db, long activityId) {
    Integer steps = computeForActivity(db, activityId);
    if (steps == null) {
      return false;
    }
    ContentValues values = new ContentValues();
    values.put(Constants.DB.ACTIVITY.STEPS, steps);
    db.update(
        Constants.DB.ACTIVITY.TABLE,
        values,
        Constants.DB.PRIMARY_KEY + " = ?",
        new String[] {String.valueOf(activityId)});
    return true;
  }

  static Integer computeForActivity(SQLiteDatabase db, long activityId) {
    String sql =
        "SELECT "
            + Constants.DB.LOCATION.ELAPSED
            + ", "
            + Constants.DB.LOCATION.TIME
            + ", "
            + Constants.DB.LOCATION.CADENCE
            + " FROM "
            + Constants.DB.LOCATION.TABLE
            + " WHERE "
            + Constants.DB.LOCATION.ACTIVITY
            + " = ? ORDER BY "
            + Constants.DB.LOCATION.ELAPSED
            + " ASC, "
            + Constants.DB.LOCATION.TIME
            + " ASC";
    List<Long> elapsed = new ArrayList<>();
    List<Double> cadence = new ArrayList<>();
    try (Cursor cursor = db.rawQuery(sql, new String[] {String.valueOf(activityId)})) {
      while (cursor.moveToNext()) {
        long t;
        if (!cursor.isNull(0)) {
          t = cursor.getLong(0);
        } else if (!cursor.isNull(1)) {
          t = cursor.getLong(1);
        } else {
          continue;
        }
        elapsed.add(t);
        if (cursor.isNull(2)) {
          cadence.add(Double.NaN);
        } else {
          cadence.add(cursor.getDouble(2));
        }
      }
    }
    int n = elapsed.size();
    if (n < 2) {
      return null;
    }
    long[] elapsedMs = new long[n];
    double[] cadenceRpm = new double[n];
    for (int i = 0; i < n; i++) {
      elapsedMs[i] = elapsed.get(i);
      cadenceRpm[i] = cadence.get(i);
    }
    return StepCountFromCadence.compute(elapsedMs, cadenceRpm);
  }

  private static List<Long> missingIds(SQLiteDatabase db) {
    List<Long> ids = new ArrayList<>();
    String sql =
        "SELECT "
            + Constants.DB.PRIMARY_KEY
            + " FROM "
            + Constants.DB.ACTIVITY.TABLE
            + " WHERE "
            + Constants.DB.ACTIVITY.STEPS
            + " IS NULL AND "
            + Constants.DB.ACTIVITY.DELETED
            + " = 0";
    try (Cursor cursor = db.rawQuery(sql, null)) {
      while (cursor.moveToNext()) {
        ids.add(cursor.getLong(0));
      }
    }
    return ids;
  }
}
