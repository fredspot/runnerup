package org.runnerup.analytics;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.runnerup.analytics.ComparableSegmentExtractor.Sample;
import org.runnerup.common.util.Constants.DB;

/** Loads running tracks and turns them into comparable 1 km windows. */
public final class ComparableSegmentLoader {

  private ComparableSegmentLoader() {}

  public static List<ComparableSegment> loadRunning(SQLiteDatabase db, int[] zoneBounds) {
    List<Long> ids = new ArrayList<>();
    try (Cursor cursor =
        db.rawQuery(
            "SELECT "
                + DB.PRIMARY_KEY
                + " FROM "
                + DB.ACTIVITY.TABLE
                + " WHERE deleted = 0 AND "
                + DB.ACTIVITY.SPORT
                + " = ?",
            new String[] {String.valueOf(DB.ACTIVITY.SPORT_RUNNING)})) {
      while (cursor.moveToNext()) {
        ids.add(cursor.getLong(0));
      }
    }
    List<ComparableSegment> all = new ArrayList<>();
    for (Long id : ids) {
      all.addAll(ComparableSegmentExtractor.extract(id, loadSamples(db, id), zoneBounds));
    }
    return all;
  }

  public static Map<Long, RunConditions> loadConditions(SQLiteDatabase db, List<Long> activityIds) {
    Map<Long, RunConditions> out = new HashMap<>();
    if (activityIds == null) {
      return out;
    }
    for (Long id : activityIds) {
      if (id == null) {
        continue;
      }
      out.put(id, loadOne(db, id));
    }
    return out;
  }

  public static List<Sample> loadSamples(SQLiteDatabase db, long activityId) {
    List<Sample> samples = new ArrayList<>();
    String sql =
        "SELECT "
            + DB.LOCATION.DISTANCE
            + ", "
            + DB.LOCATION.ELAPSED
            + ", "
            + DB.LOCATION.HR
            + ", "
            + DB.LOCATION.TOPO_ALTITUDE
            + ", "
            + DB.LOCATION.ALTITUDE
            + ", "
            + DB.LOCATION.TEMPERATURE
            + " FROM "
            + DB.LOCATION.TABLE
            + " WHERE "
            + DB.LOCATION.ACTIVITY
            + " = ? AND "
            + DB.LOCATION.DISTANCE
            + " IS NOT NULL AND "
            + DB.LOCATION.ELAPSED
            + " IS NOT NULL ORDER BY "
            + DB.LOCATION.DISTANCE
            + " ASC, "
            + DB.LOCATION.ELAPSED
            + " ASC";
    try (Cursor cursor = db.rawQuery(sql, new String[] {String.valueOf(activityId)})) {
      while (cursor.moveToNext()) {
        int hr = cursor.isNull(2) ? 0 : cursor.getInt(2);
        Double elevation = null;
        if (!cursor.isNull(3)) {
          elevation = cursor.getDouble(3);
        } else if (!cursor.isNull(4)) {
          elevation = cursor.getDouble(4);
        }
        Double temp = cursor.isNull(5) ? null : cursor.getDouble(5);
        samples.add(new Sample(cursor.getDouble(0), cursor.getLong(1), hr, elevation, temp));
      }
    }
    return samples;
  }

  private static RunConditions loadOne(SQLiteDatabase db, long activityId) {
    double gain = 0;
    try (Cursor cursor =
        db.rawQuery(
            "SELECT "
                + DB.ACTIVITY.ELEVATION_GAIN
                + " FROM "
                + DB.ACTIVITY.TABLE
                + " WHERE "
                + DB.PRIMARY_KEY
                + " = ?",
            new String[] {String.valueOf(activityId)})) {
      if (cursor.moveToFirst() && !cursor.isNull(0)) {
        gain = cursor.getDouble(0);
      }
    }
    List<Sample> samples = loadSamples(db, activityId);
    Double firstElev = null;
    Double lastElev = null;
    double tempSum = 0;
    int tempCount = 0;
    for (Sample sample : samples) {
      if (sample.elevationM != null) {
        if (firstElev == null) {
          firstElev = sample.elevationM;
        }
        lastElev = sample.elevationM;
      }
      if (sample.temperatureC != null && !sample.temperatureC.isNaN()) {
        tempSum += sample.temperatureC;
        tempCount++;
      }
    }
    double net = 0;
    if (firstElev != null && lastElev != null) {
      net = lastElev - firstElev;
    }
    Double avgTemp = tempCount > 0 ? tempSum / tempCount : null;
    return new RunConditions(gain, net, avgTemp);
  }
}
