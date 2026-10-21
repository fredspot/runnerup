package org.runnerup.tracking.elevation;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.runnerup.common.util.Constants;
import org.runnerup.tracking.elevation.ElevationLookup.LatLng;

/** Fills topography altitudes and activity gain/loss from a DEM lookup. */
public final class TopographyElevationService {

  private static final String TAG = "TopoElevation";
  static final int MAX_LOOKUP_POINTS = OpenTopoDataClient.MAX_LOCATIONS;

  public static final class TrackPoint {
    public final long rowId;
    public final double latitude;
    public final double longitude;
    public final double distanceM;
    public final Double gpsAltitude;

    public TrackPoint(
        long rowId, double latitude, double longitude, double distanceM, Double gpsAltitude) {
      this.rowId = rowId;
      this.latitude = latitude;
      this.longitude = longitude;
      this.distanceM = distanceM;
      this.gpsAltitude = gpsAltitude;
    }
  }

  private TopographyElevationService() {}

  /** True when gain is already stored (no network needed). */
  public static boolean hasGain(SQLiteDatabase db, long activityId) {
    try (Cursor cursor =
        db.query(
            Constants.DB.ACTIVITY.TABLE,
            new String[] {Constants.DB.ACTIVITY.ELEVATION_GAIN},
            Constants.DB.PRIMARY_KEY + " = ?",
            new String[] {String.valueOf(activityId)},
            null,
            null,
            null)) {
      return cursor.moveToFirst() && !cursor.isNull(0);
    }
  }

  /**
   * Looks up DEM elevations if needed. Returns true when gain/loss were written. Failures leave
   * GPS altitude untouched and do not write gain unless a barometer profile exists.
   */
  public static boolean ensureFilled(SQLiteDatabase db, long activityId, ElevationLookup lookup) {
    return ensureFilled(db, activityId, lookup, false);
  }

  public static boolean ensureFilled(
      SQLiteDatabase db, long activityId, ElevationLookup lookup, boolean force) {
    if (!force && hasGain(db, activityId)) {
      return false;
    }
    List<TrackPoint> points = loadTrack(db, activityId);
    if (points.isEmpty()) {
      return false;
    }

    double[] topo = null;
    try {
      topo = lookupTrack(points, lookup);
    } catch (IOException e) {
      Log.w(TAG, "DEM lookup failed for activity " + activityId, e);
    }

    if (topo != null) {
      writeTopoAltitudes(db, points, topo);
    }

    double[] gps = gpsAltitudes(points);
    double[] dist = trackDistances(points);
    boolean gpsOk = hasFinite(gps);
    boolean barometer = hasBarometer(db, activityId);
    double[] series;
    if (barometer && gpsOk) {
      series = ElevationSmoother.smooth(dist, gps);
    } else if (topo != null) {
      series = topo;
    } else if (gpsOk) {
      series = ElevationSmoother.smooth(dist, gps);
    } else {
      return false;
    }
    ElevationGain.Result gain =
        ElevationGain.compute(dist, series, ElevationGain.DEFAULT_THRESHOLD_M);
    writeGain(db, activityId, gain);
    return true;
  }

  /**
   * Re-smooths existing stored elevation (`topo_altitude` or `altitude`) and updates activity
   * `elevation_gain` / `elevation_loss` without requiring a network lookup.
   */
  public static void recomputeStoredElevation(SQLiteDatabase db, long activityId) {
    List<TrackPoint> points = loadTrackWithTopo(db, activityId);
    if (points.size() < 2) return;
    double[] dist = trackDistances(points);
    double[] raw = new double[points.size()];
    boolean anyTopo = false;
    for (int i = 0; i < points.size(); i++) {
      Double alt = points.get(i).gpsAltitude;
      raw[i] = alt == null ? Double.NaN : alt;
    }
    try (Cursor c =
        db.rawQuery(
            "SELECT COUNT(*) FROM "
                + Constants.DB.LOCATION.TABLE
                + " WHERE "
                + Constants.DB.LOCATION.ACTIVITY
                + " = ? AND "
                + Constants.DB.LOCATION.TOPO_ALTITUDE
                + " IS NOT NULL",
            new String[] {String.valueOf(activityId)})) {
      if (c.moveToFirst() && c.getInt(0) > 0) {
        anyTopo = true;
      }
    }
    if (!hasFinite(raw)) return;
    double[] smoothed = ElevationSmoother.smooth(dist, raw);
    ElevationGain.Result gain =
        ElevationGain.compute(dist, smoothed, ElevationGain.DEFAULT_THRESHOLD_M);
    writeGain(db, activityId, gain);
  }

  static double[] lookupTrack(List<TrackPoint> points, ElevationLookup lookup) throws IOException {
    int[] indices = TopographyInterpolator.downsampleIndices(points.size(), MAX_LOOKUP_POINTS);
    List<LatLng> samples = new ArrayList<>(indices.length);
    double[] sampleDistance = new double[indices.length];
    for (int i = 0; i < indices.length; i++) {
      TrackPoint p = points.get(indices[i]);
      samples.add(new LatLng(p.latitude, p.longitude));
      sampleDistance[i] = p.distanceM;
    }
    double[] sampleElev = lookup.lookup(samples);
    double[] trackX = new double[points.size()];
    double[] sampleX = sampleDistance;
    if (hasIncreasingDistance(sampleDistance)) {
      for (int i = 0; i < points.size(); i++) {
        trackX[i] = points.get(i).distanceM;
      }
    } else {
      sampleX = new double[indices.length];
      for (int i = 0; i < indices.length; i++) {
        sampleX[i] = indices[i];
      }
      for (int i = 0; i < points.size(); i++) {
        trackX[i] = i;
      }
    }
    double[] interpolated = TopographyInterpolator.interpolate(sampleX, sampleElev, trackX);
    return points.size() > 5 ? ElevationSmoother.smooth(trackX, interpolated) : interpolated;
  }

  static boolean hasIncreasingDistance(double[] sampleDistanceM) {
    if (sampleDistanceM == null || sampleDistanceM.length == 0) {
      return false;
    }
    if (sampleDistanceM.length == 1) {
      return true;
    }
    return sampleDistanceM[sampleDistanceM.length - 1] - sampleDistanceM[0] > 1.0;
  }

  static List<TrackPoint> loadTrack(SQLiteDatabase db, long activityId) {
    List<TrackPoint> points = new ArrayList<>();
    String sql =
        "SELECT "
            + Constants.DB.PRIMARY_KEY
            + ", "
            + Constants.DB.LOCATION.LATITUDE
            + ", "
            + Constants.DB.LOCATION.LONGITUDE
            + ", "
            + Constants.DB.LOCATION.DISTANCE
            + ", "
            + Constants.DB.LOCATION.ALTITUDE
            + " FROM "
            + Constants.DB.LOCATION.TABLE
            + " WHERE "
            + Constants.DB.LOCATION.ACTIVITY
            + " = ? ORDER BY "
            + Constants.DB.LOCATION.ELAPSED
            + " ASC, "
            + Constants.DB.LOCATION.TIME
            + " ASC";
    try (Cursor cursor = db.rawQuery(sql, new String[] {String.valueOf(activityId)})) {
      while (cursor.moveToNext()) {
        if (cursor.isNull(1) || cursor.isNull(2)) {
          continue;
        }
        Double alt = cursor.isNull(4) ? null : cursor.getDouble(4);
        points.add(
            new TrackPoint(
                cursor.getLong(0),
                cursor.getDouble(1),
                cursor.getDouble(2),
                cursor.isNull(3) ? 0 : cursor.getDouble(3),
                alt));
      }
    }
    return points;
  }

  static boolean hasBarometer(SQLiteDatabase db, long activityId) {
    try (Cursor cursor =
        db.query(
            Constants.DB.ACTIVITY.TABLE,
            new String[] {Constants.DB.ACTIVITY.META_DATA},
            Constants.DB.PRIMARY_KEY + " = ?",
            new String[] {String.valueOf(activityId)},
            null,
            null,
            null)) {
      if (!cursor.moveToFirst() || cursor.isNull(0)) {
        return false;
      }
      String meta = cursor.getString(0);
      return meta != null && meta.contains(Constants.DB.ACTIVITY.WITH_BAROMETER);
    }
  }

  static boolean hasFinite(double[] values) {
    if (values == null) {
      return false;
    }
    for (double v : values) {
      if (!Double.isNaN(v) && !Double.isInfinite(v)) {
        return true;
      }
    }
    return false;
  }

  private static double[] gpsAltitudes(List<TrackPoint> points) {
    double[] series = new double[points.size()];
    for (int i = 0; i < points.size(); i++) {
      Double alt = points.get(i).gpsAltitude;
      series[i] = alt == null ? Double.NaN : alt;
    }
    return series;
  }

  private static double[] trackDistances(List<TrackPoint> points) {
    double[] dist = new double[points.size()];
    for (int i = 0; i < points.size(); i++) {
      dist[i] = points.get(i).distanceM;
    }
    return dist;
  }

  static List<TrackPoint> loadTrackWithTopo(SQLiteDatabase db, long activityId) {
    List<TrackPoint> points = new ArrayList<>();
    String sql =
        "SELECT "
            + Constants.DB.PRIMARY_KEY
            + ", "
            + Constants.DB.LOCATION.LATITUDE
            + ", "
            + Constants.DB.LOCATION.LONGITUDE
            + ", "
            + Constants.DB.LOCATION.DISTANCE
            + ", COALESCE("
            + Constants.DB.LOCATION.TOPO_ALTITUDE
            + ", "
            + Constants.DB.LOCATION.ALTITUDE
            + ")"
            + " FROM "
            + Constants.DB.LOCATION.TABLE
            + " WHERE "
            + Constants.DB.LOCATION.ACTIVITY
            + " = ? ORDER BY "
            + Constants.DB.LOCATION.ELAPSED
            + " ASC, "
            + Constants.DB.LOCATION.TIME
            + " ASC";
    try (Cursor cursor = db.rawQuery(sql, new String[] {String.valueOf(activityId)})) {
      while (cursor.moveToNext()) {
        if (cursor.isNull(1) || cursor.isNull(2)) {
          continue;
        }
        Double alt = cursor.isNull(4) ? null : cursor.getDouble(4);
        points.add(
            new TrackPoint(
                cursor.getLong(0),
                cursor.getDouble(1),
                cursor.getDouble(2),
                cursor.isNull(3) ? 0 : cursor.getDouble(3),
                alt));
      }
    }
    return points;
  }

  private static void writeTopoAltitudes(
      SQLiteDatabase db, List<TrackPoint> points, double[] topo) {
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      for (int i = 0; i < points.size(); i++) {
        if (Double.isNaN(topo[i])) {
          continue;
        }
        values.clear();
        values.put(Constants.DB.LOCATION.TOPO_ALTITUDE, topo[i]);
        db.update(
            Constants.DB.LOCATION.TABLE,
            values,
            Constants.DB.PRIMARY_KEY + " = ?",
            new String[] {String.valueOf(points.get(i).rowId)});
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  private static void writeGain(SQLiteDatabase db, long activityId, ElevationGain.Result gain) {
    ContentValues values = new ContentValues();
    values.put(Constants.DB.ACTIVITY.ELEVATION_GAIN, gain.gainM);
    values.put(Constants.DB.ACTIVITY.ELEVATION_LOSS, gain.lossM);
    db.update(
        Constants.DB.ACTIVITY.TABLE,
        values,
        Constants.DB.PRIMARY_KEY + " = ?",
        new String[] {String.valueOf(activityId)});
  }
}
