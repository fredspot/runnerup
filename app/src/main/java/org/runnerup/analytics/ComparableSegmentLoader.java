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
    List<long[]> acts = new ArrayList<>();
    try (Cursor cursor =
        db.rawQuery(
            "SELECT "
                + DB.PRIMARY_KEY
                + ", "
                + DB.ACTIVITY.START_TIME
                + " FROM "
                + DB.ACTIVITY.TABLE
                + " WHERE deleted = 0 AND "
                + DB.ACTIVITY.SPORT
                + " = ?",
            new String[] {String.valueOf(DB.ACTIVITY.SPORT_RUNNING)})) {
      while (cursor.moveToNext()) {
        acts.add(new long[] {cursor.getLong(0), cursor.isNull(1) ? 0L : cursor.getLong(1)});
      }
    }
    List<ComparableSegment> all = new ArrayList<>();
    for (long[] act : acts) {
      long id = act[0];
      long startSec = act[1];
      DailyWeather24h dayWeather = DailyWeatherStore.getForActivity(db, id, startSec);
      all.addAll(
          ComparableSegmentExtractor.extract(
              id, loadSamples(db, id), zoneBounds, startSec, dayWeather));
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
    if (samples.size() < 2) {
      samples = loadLapFallbackSamples(db, activityId);
    } else if (samples.size() > 5) {
      double[] dist = new double[samples.size()];
      double[] elev = new double[samples.size()];
      boolean anyElev = false;
      for (int i = 0; i < samples.size(); i++) {
        Sample s = samples.get(i);
        dist[i] = s.distanceM;
        if (s.elevationM != null && !s.elevationM.isNaN()) {
          elev[i] = s.elevationM;
          anyElev = true;
        } else {
          elev[i] = Double.NaN;
        }
      }
      if (anyElev) {
        double[] smoothed =
            org.runnerup.tracking.elevation.ElevationSmoother.smooth(dist, elev);
        List<Sample> smoothedSamples = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) {
          Sample s = samples.get(i);
          Double sm = Double.isNaN(smoothed[i]) ? s.elevationM : smoothed[i];
          smoothedSamples.add(new Sample(s.distanceM, s.elapsedMs, s.hr, sm, s.temperatureC));
        }
        samples = smoothedSamples;
      }
    }
    return samples;
  }

  /**
   * When a run has no per-point GPS distance/elapsed rows, synthesize boundary samples from its
   * laps so 1 km segments can still be extracted.
   */
  private static List<Sample> loadLapFallbackSamples(SQLiteDatabase db, long activityId) {
    List<Sample> out = new ArrayList<>();
    String sql =
        "SELECT "
            + DB.LAP.DISTANCE
            + ", "
            + DB.LAP.TIME
            + ", "
            + DB.LAP.AVG_HR
            + " FROM "
            + DB.LAP.TABLE
            + " WHERE "
            + DB.LAP.ACTIVITY
            + " = ? ORDER BY "
            + DB.LAP.LAP
            + " ASC";
    double cumDist = 0.0;
    long cumMs = 0L;
    try (Cursor c = db.rawQuery(sql, new String[] {String.valueOf(activityId)})) {
      while (c.moveToNext()) {
        double d = c.isNull(0) ? 0.0 : c.getDouble(0);
        long tSec = c.isNull(1) ? 0L : c.getLong(1);
        int hr = c.isNull(2) ? 0 : c.getInt(2);
        if (d <= 0.0 || tSec <= 0L) continue;
        if (out.isEmpty()) {
          out.add(new Sample(0.0, 0L, hr, null, null));
        }
        cumDist += d;
        cumMs += tSec * 1000L;
        out.add(new Sample(cumDist, cumMs, hr, null, null));
      }
    }
    return out;
  }

  public static RunConditions loadOne(SQLiteDatabase db, long activityId) {
    double gain = 0;
    long startTimeSec = 0L;
    long durationSec = 0L;
    try (Cursor cursor =
        db.rawQuery(
            "SELECT "
                + DB.ACTIVITY.ELEVATION_GAIN
                + ", "
                + DB.ACTIVITY.START_TIME
                + ", "
                + DB.ACTIVITY.TIME
                + " FROM "
                + DB.ACTIVITY.TABLE
                + " WHERE "
                + DB.PRIMARY_KEY
                + " = ?",
            new String[] {String.valueOf(activityId)})) {
      if (cursor.moveToFirst()) {
        if (!cursor.isNull(0)) gain = cursor.getDouble(0);
        if (!cursor.isNull(1)) startTimeSec = cursor.getLong(1);
        if (!cursor.isNull(2)) durationSec = cursor.getLong(2);
      }
    }
    List<Sample> samples = loadSamples(db, activityId);
    Double firstElev = null;
    Double lastElev = null;
    double elevSum = 0;
    int elevCount = 0;
    double tempSum = 0;
    int tempCount = 0;
    for (Sample sample : samples) {
      if (sample.elevationM != null && !sample.elevationM.isNaN()) {
        if (firstElev == null) {
          firstElev = sample.elevationM;
        }
        lastElev = sample.elevationM;
        elevSum += sample.elevationM;
        elevCount++;
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
    Double avgElev = elevCount > 0 ? elevSum / elevCount : null;
    if (avgElev == null) {
      avgElev = queryMeanAltitudeFallback(db, activityId);
    }
    Double avgTemp = tempCount > 0 ? tempSum / tempCount : null;
    Double avgHumidity = null;
    DailyWeather24h dayWeather = DailyWeatherStore.getForActivity(db, activityId, startTimeSec);
    if (dayWeather != null && startTimeSec > 0L) {
      DailyWeather24h.HourMatch match = dayWeather.matchWindow(startTimeSec, durationSec);
      if (avgTemp == null) {
        avgTemp = match.temperatureC;
      }
      avgHumidity = match.humidityPct;
    }
    return new RunConditions(gain, net, avgElev, avgTemp, avgHumidity);
  }

  private static Double queryMeanAltitudeFallback(SQLiteDatabase db, long activityId) {
    String sql =
        "SELECT AVG(COALESCE("
            + DB.LOCATION.TOPO_ALTITUDE
            + ", "
            + DB.LOCATION.ALTITUDE
            + ")) FROM "
            + DB.LOCATION.TABLE
            + " WHERE "
            + DB.LOCATION.ACTIVITY
            + " = ? AND ("
            + DB.LOCATION.TOPO_ALTITUDE
            + " IS NOT NULL OR "
            + DB.LOCATION.ALTITUDE
            + " IS NOT NULL)";
    try (Cursor c = db.rawQuery(sql, new String[] {String.valueOf(activityId)})) {
      if (c.moveToFirst() && !c.isNull(0)) {
        return c.getDouble(0);
      }
    } catch (Exception ignored) {
    }
    return null;
  }
}
