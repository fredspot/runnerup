package org.runnerup.analytics;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.runnerup.common.util.Constants.DB;

/**
 * Stores and retrieves 24-hour temperature and relative humidity series in {@link
 * DB.DAILY_WEATHER#TABLE}, and backfills missing days from {@link OpenMeteoWeatherClient}.
 */
public final class DailyWeatherStore {

  private static final String TAG = "DailyWeatherStore";

  private DailyWeatherStore() {}

  public static DailyWeather24h getDay(
      SQLiteDatabase db, String dateUtc, int latCell, int lonCell) {
    try (Cursor c =
        db.query(
            DB.DAILY_WEATHER.TABLE,
            new String[] {DB.DAILY_WEATHER.TEMPS_C, DB.DAILY_WEATHER.HUMIDITIES},
            DB.DAILY_WEATHER.DATE
                + " = ? AND "
                + DB.DAILY_WEATHER.LAT_CELL
                + " = ? AND "
                + DB.DAILY_WEATHER.LON_CELL
                + " = ?",
            new String[] {dateUtc, String.valueOf(latCell), String.valueOf(lonCell)},
            null,
            null,
            null)) {
      if (c.moveToFirst()) {
        return new DailyWeather24h(
            dateUtc,
            latCell,
            lonCell,
            DailyWeather24h.decode24(c.getString(0)),
            DailyWeather24h.decode24(c.getString(1)));
      }
    } catch (Exception e) {
      Log.w(TAG, "getDay failed: " + e.getMessage());
    }
    try (Cursor c =
        db.query(
            DB.DAILY_WEATHER.TABLE,
            new String[] {
              DB.DAILY_WEATHER.LAT_CELL,
              DB.DAILY_WEATHER.LON_CELL,
              DB.DAILY_WEATHER.TEMPS_C,
              DB.DAILY_WEATHER.HUMIDITIES
            },
            DB.DAILY_WEATHER.DATE + " = ?",
            new String[] {dateUtc},
            null,
            null,
            null,
            "1")) {
      if (c.moveToFirst()) {
        return new DailyWeather24h(
            dateUtc,
            c.getInt(0),
            c.getInt(1),
            DailyWeather24h.decode24(c.getString(2)),
            DailyWeather24h.decode24(c.getString(3)));
      }
    } catch (Exception e) {
      Log.w(TAG, "getDay fallback failed: " + e.getMessage());
    }
    return null;
  }

  public static void saveDay(SQLiteDatabase db, DailyWeather24h day) {
    if (day == null) return;
    ContentValues cv = new ContentValues();
    cv.put(DB.DAILY_WEATHER.DATE, day.dateUtc);
    cv.put(DB.DAILY_WEATHER.LAT_CELL, day.latCell);
    cv.put(DB.DAILY_WEATHER.LON_CELL, day.lonCell);
    cv.put(DB.DAILY_WEATHER.TEMPS_C, DailyWeather24h.encode24(day.hourlyTempC));
    cv.put(DB.DAILY_WEATHER.HUMIDITIES, DailyWeather24h.encode24(day.hourlyHumidityPct));
    db.insertWithOnConflict(
        DB.DAILY_WEATHER.TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
  }

  public static DailyWeather24h getForActivity(
      SQLiteDatabase db, long activityId, long startTimeSec) {
    if (startTimeSec <= 0L) return null;
    String dateUtc = DailyWeather24h.utcDate(startTimeSec);
    int[] cell = resolveActivityCell(db, activityId);
    if (cell == null) return null;
    return getDay(db, dateUtc, cell[0], cell[1]);
  }

  public static DailyWeather24h ensureForActivity(
      SQLiteDatabase db,
      long activityId,
      long startTimeSec,
      OpenMeteoWeatherClient client) {
    if (startTimeSec <= 0L) return null;
    String dateUtc = DailyWeather24h.utcDate(startTimeSec);
    int[] cell = resolveActivityCell(db, activityId);
    if (cell == null) return null;
    DailyWeather24h cached = getDay(db, dateUtc, cell[0], cell[1]);
    if (cached != null) return cached;
    if (client == null) return null;
    try {
      DailyWeather24h fetched = client.fetchDay(dateUtc, cell[0], cell[1]);
      if (fetched != null) {
        saveDay(db, fetched);
        return fetched;
      }
    } catch (Exception e) {
      Log.w(TAG, "Weather fetch failed for " + dateUtc + ": " + e.getMessage());
    }
    return null;
  }

  /**
   * Ensures cached 24-hour weather exists for all running activities up to {@code maxNetworkDays}
   * network requests in a single pass. Returns the number of newly fetched days.
   */
  public static int syncMissingRunDays(
      SQLiteDatabase db, OpenMeteoWeatherClient client, int maxNetworkDays) {
    if (client == null || maxNetworkDays <= 0) return 0;
    int[] fallbackCell = findAnyValidCell(db);
    Set<DayKey> needed = new LinkedHashSet<>();
    String sql =
        "SELECT a."
            + DB.PRIMARY_KEY
            + ", a."
            + DB.ACTIVITY.START_TIME
            + ", (SELECT l."
            + DB.LOCATION.LATITUDE
            + " FROM "
            + DB.LOCATION.TABLE
            + " l WHERE l."
            + DB.LOCATION.ACTIVITY
            + " = a."
            + DB.PRIMARY_KEY
            + " AND l."
            + DB.LOCATION.LATITUDE
            + " IS NOT NULL AND ABS(l."
            + DB.LOCATION.LATITUDE
            + ") > 0.001 LIMIT 1), (SELECT l."
            + DB.LOCATION.LONGITUDE
            + " FROM "
            + DB.LOCATION.TABLE
            + " l WHERE l."
            + DB.LOCATION.ACTIVITY
            + " = a."
            + DB.PRIMARY_KEY
            + " AND l."
            + DB.LOCATION.LONGITUDE
            + " IS NOT NULL AND ABS(l."
            + DB.LOCATION.LONGITUDE
            + ") > 0.001 LIMIT 1)"
            + " FROM "
            + DB.ACTIVITY.TABLE
            + " a WHERE a."
            + DB.ACTIVITY.DELETED
            + " = 0 AND a."
            + DB.ACTIVITY.START_TIME
            + " > 0 ORDER BY a."
            + DB.ACTIVITY.START_TIME
            + " DESC";
    try (Cursor c = db.rawQuery(sql, null)) {
      while (c.moveToNext()) {
        long startTime = c.getLong(1);
        int[] cell;
        if (!c.isNull(2) && !c.isNull(3)) {
          cell =
              new int[] {
                DailyWeather24h.toCell(c.getDouble(2)), DailyWeather24h.toCell(c.getDouble(3))
              };
        } else {
          cell = fallbackCell;
        }
        if (cell == null) continue;
        needed.add(new DayKey(DailyWeather24h.utcDate(startTime), cell[0], cell[1]));
      }
    }
    int fetchedCount = 0;
    for (DayKey key : needed) {
      if (fetchedCount >= maxNetworkDays) break;
      if (hasExactDay(db, key.dateUtc, key.latCell, key.lonCell)) continue;
      try {
        DailyWeather24h fetched = client.fetchDay(key.dateUtc, key.latCell, key.lonCell);
        if (fetched != null) {
          saveDay(db, fetched);
          fetchedCount++;
        }
      } catch (Exception e) {
        Log.w(TAG, "syncMissingRunDays failed on " + key.dateUtc + ": " + e.getMessage());
      }
    }
    return fetchedCount;
  }

  private static boolean hasExactDay(
      SQLiteDatabase db, String dateUtc, int latCell, int lonCell) {
    try (Cursor c =
        db.query(
            DB.DAILY_WEATHER.TABLE,
            new String[] {DB.DAILY_WEATHER.DATE},
            DB.DAILY_WEATHER.DATE
                + " = ? AND "
                + DB.DAILY_WEATHER.LAT_CELL
                + " = ? AND "
                + DB.DAILY_WEATHER.LON_CELL
                + " = ?",
            new String[] {dateUtc, String.valueOf(latCell), String.valueOf(lonCell)},
            null,
            null,
            null,
            "1")) {
      return c.moveToFirst();
    } catch (Exception e) {
      return false;
    }
  }

  public static int[] resolveActivityCell(SQLiteDatabase db, long activityId) {
    String sql =
        "SELECT "
            + DB.LOCATION.LATITUDE
            + ", "
            + DB.LOCATION.LONGITUDE
            + " FROM "
            + DB.LOCATION.TABLE
            + " WHERE "
            + DB.LOCATION.ACTIVITY
            + " = ? AND "
            + DB.LOCATION.LATITUDE
            + " IS NOT NULL AND "
            + DB.LOCATION.LONGITUDE
            + " IS NOT NULL AND (ABS("
            + DB.LOCATION.LATITUDE
            + ") > 0.001 OR ABS("
            + DB.LOCATION.LONGITUDE
            + ") > 0.001) LIMIT 1";
    try (Cursor c = db.rawQuery(sql, new String[] {String.valueOf(activityId)})) {
      if (c.moveToFirst()) {
        return new int[] {
          DailyWeather24h.toCell(c.getDouble(0)), DailyWeather24h.toCell(c.getDouble(1))
        };
      }
    } catch (Exception ignored) {
    }
    return findAnyValidCell(db);
  }

  private static int[] findAnyValidCell(SQLiteDatabase db) {
    String sql =
        "SELECT "
            + DB.LOCATION.LATITUDE
            + ", "
            + DB.LOCATION.LONGITUDE
            + " FROM "
            + DB.LOCATION.TABLE
            + " WHERE "
            + DB.LOCATION.LATITUDE
            + " IS NOT NULL AND "
            + DB.LOCATION.LONGITUDE
            + " IS NOT NULL AND (ABS("
            + DB.LOCATION.LATITUDE
            + ") > 0.001 OR ABS("
            + DB.LOCATION.LONGITUDE
            + ") > 0.001) ORDER BY "
            + DB.PRIMARY_KEY
            + " DESC LIMIT 1";
    try (Cursor c = db.rawQuery(sql, null)) {
      if (c.moveToFirst()) {
        return new int[] {
          DailyWeather24h.toCell(c.getDouble(0)), DailyWeather24h.toCell(c.getDouble(1))
        };
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private static final class DayKey {
    final String dateUtc;
    final int latCell;
    final int lonCell;

    DayKey(String dateUtc, int latCell, int lonCell) {
      this.dateUtc = dateUtc;
      this.latCell = latCell;
      this.lonCell = lonCell;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof DayKey)) return false;
      DayKey that = (DayKey) o;
      return latCell == that.latCell
          && lonCell == that.lonCell
          && Objects.equals(dateUtc, that.dateUtc);
    }

    @Override
    public int hashCode() {
      return Objects.hash(dateUtc, latCell, lonCell);
    }
  }
}
