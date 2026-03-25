/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.text.TextUtils;
import android.util.Log;
import java.util.ArrayList;
import org.runnerup.common.util.Constants;

public class ActivityCleaner implements Constants {
  private long _totalSumHr = 0;
  private int _totalCount = 0;
  private int _totalMaxHr = 0;
  private long _totalTime = 0;
  private double _totalDistance = 0;
  private Location _lastLocation = null;
  private boolean _isActive = false;

  /** recompute laps aggregates based on locations */
  private void recomputeLaps(SQLiteDatabase db, long activityId) {
    final String[] cols = new String[] {DB.LAP.LAP};

    ArrayList<Long> laps = new ArrayList<>();
    Cursor c =
        db.query(
            DB.LAP.TABLE,
            cols,
            DB.LAP.ACTIVITY + " = " + activityId,
            null,
            null,
            null,
            "_id",
            null);
    if (c.moveToFirst()) {
      do {
        laps.add(c.getLong(0));
      } while (c.moveToNext());
    }
    c.close();

    for (long lap : laps) {
      recomputeLap(db, activityId, lap);
    }
  }

  /** recompute a lap aggregate based on locations */
  void recomputeLap(SQLiteDatabase db, long activityId, long lap) {
    // Reset lap-specific state (each lap should be processed independently)
    // Save current state to restore after processing this lap
    Location savedLastLocation = _lastLocation;
    boolean savedIsActive = _isActive;
    _lastLocation = null;
    _isActive = false;
    
    // Check if this is the last lap (might be incomplete and should use GPS distance)
    long maxLap = db.compileStatement("SELECT MAX(" + DB.LAP.LAP + ") FROM " + DB.LAP.TABLE + 
        " WHERE " + DB.LAP.ACTIVITY + " = " + activityId).simpleQueryForLong();
    boolean isLastLap = (lap == maxLap);
    
    // First, check if the lap already has a distance set (from autolap during run)
    // If so, preserve it to maintain accurate autolap distances
    // Skip recomputation entirely if distance is already set and reasonable (800-1200m range)
    // EXCEPT for the last lap, which might be incomplete and should always use GPS distance
    double originalDistance = 0;
    String[] lapCols = new String[] {DB.LAP.DISTANCE};
    Cursor lapCursor = db.query(
        DB.LAP.TABLE,
        lapCols,
        DB.LAP.ACTIVITY + " = " + activityId + " and " + DB.LAP.LAP + " = " + lap,
        null, null, null, null);
    if (lapCursor.moveToFirst() && !lapCursor.isNull(0)) {
      originalDistance = lapCursor.getDouble(0);
      
      // If distance is already set and in reasonable autolap range (800-1200m), 
      // skip recomputation UNLESS it's the last lap (which might be incomplete)
      if (originalDistance >= 800 && originalDistance <= 1200 && !isLastLap) {
        lapCursor.close();
        return; // Skip recomputation - preserve the original autolap distance
      }
      // For last lap, we'll recompute to get actual GPS distance even if it was set to ~1000m
    }
    lapCursor.close();

    long sum_time = 0;
    long sum_hr = 0;
    double sum_distance = 0;
    int count = 0;
    int max_hr = 0;
    final String[] cols =
        new String[] {
          DB.LOCATION.TIME,
          DB.LOCATION.LATITUDE,
          DB.LOCATION.LONGITUDE,
          DB.LOCATION.TYPE,
          DB.LOCATION.HR,
          // DB.LOCATION.CADENCE,
          // DB.LOCATION.TEMPERATURE,
          // DB.LOCATION.PRESSURE,
          "_id"
        };

    Cursor c =
        db.query(
            DB.LOCATION.TABLE,
            cols,
            DB.LOCATION.ACTIVITY + " = " + activityId + " and " + DB.LOCATION.LAP + " = " + lap,
            null,
            null,
            null,
            "_id",
            null);
    if (c.moveToFirst()) {
      do {
        Location l = new Location("Dill poh");
        l.setTime(c.getLong(0));
        l.setLatitude(c.getDouble(1));
        l.setLongitude(c.getDouble(2));
        l.setProvider("" + c.getLong(3));

        int type = c.getInt(3);
        switch (type) {
          case DB.LOCATION.TYPE_START:
          case DB.LOCATION.TYPE_RESUME:
            _lastLocation = l;
            _isActive = true;
            break;
          case DB.LOCATION.TYPE_END:
          case DB.LOCATION.TYPE_PAUSE:
          case DB.LOCATION.TYPE_GPS:
            if (_lastLocation == null) {
              // First location in this lap - initialize it and mark as active
              // (laps can start with GPS points if they're continuation of previous lap)
              _lastLocation = l;
              _isActive = true;
              break;
            }

            if (_isActive) {
              double diffDist = l.distanceTo(_lastLocation);
              sum_distance += diffDist;
              // Always accumulate GPS distance for total (will be recalculated from laps if needed)
              _totalDistance += diffDist;

              long diffTime = l.getTime() - _lastLocation.getTime();
              sum_time += diffTime;
              _totalTime += diffTime;

              int hr = c.getInt(4);
              sum_hr += hr;
              max_hr = Math.max(max_hr, hr);
              _totalMaxHr = Math.max(_totalMaxHr, hr);
              count++;
              _totalCount++;
              _totalSumHr += hr;
            }
            _lastLocation = l;
            if (type == DB.LOCATION.TYPE_PAUSE || type == DB.LOCATION.TYPE_END) {
              _isActive = false;
            }
            break;
        }
      } while (c.moveToNext());
    }
    c.close();

    ContentValues tmp = new ContentValues();
    // For the last lap, always use GPS-calculated distance (it might be incomplete)
    // For other laps, preserve original distance if it was set (from autolap), otherwise use GPS-calculated
    if (isLastLap) {
      tmp.put(DB.LAP.DISTANCE, sum_distance);
      Log.i("ActivityCleaner", "Last lap " + lap + ": using GPS distance " + sum_distance + "m instead of preserved " + originalDistance + "m");
    } else if (originalDistance > 0 && originalDistance >= 800 && originalDistance <= 1200) {
      // Preserve autolap distance for non-last laps
      tmp.put(DB.LAP.DISTANCE, originalDistance);
    } else {
      // Use GPS-calculated distance
      tmp.put(DB.LAP.DISTANCE, sum_distance);
    }
    tmp.put(DB.LAP.TIME, Math.round(sum_time / 1000.0d));
    if (sum_hr > 0) {
      long hr = Math.round(sum_hr / (double) count);
      tmp.put(DB.LAP.AVG_HR, hr);
      tmp.put(DB.LAP.MAX_HR, max_hr);
    }
    db.update(
        DB.LAP.TABLE,
        tmp,
        DB.LAP.ACTIVITY + " = " + activityId + " and " + DB.LAP.LAP + " = " + lap,
        null);
    
    // Restore state for next lap (though we reset it at the start of each lap anyway)
    _lastLocation = savedLastLocation;
    _isActive = savedIsActive;
  }

  /** recompute an activity summary based on laps */
  void recomputeSummary(SQLiteDatabase db, long activityId) {
    // Recalculate total distance and time from all lap distances/times to ensure accuracy
    // (since we preserve original autolap distances, the sum of laps is more accurate)
    double totalDistanceFromLaps = 0;
    long totalTimeFromLaps = 0;
    String[] lapCols = new String[] {DB.LAP.DISTANCE, DB.LAP.TIME};
    Cursor lapCursor = db.query(
        DB.LAP.TABLE,
        lapCols,
        DB.LAP.ACTIVITY + " = " + activityId,
        null, null, null, null);
    int lapCount = 0;
    if (lapCursor.moveToFirst()) {
      do {
        lapCount++;
        if (!lapCursor.isNull(0)) {
          double lapDist = lapCursor.getDouble(0);
          totalDistanceFromLaps += lapDist;
          Log.d("ActivityCleaner", "Lap " + lapCount + ": distance = " + lapDist + "m");
        }
        if (!lapCursor.isNull(1)) {
          long lapTime = lapCursor.getLong(1);
          totalTimeFromLaps += lapTime;
          Log.d("ActivityCleaner", "Lap " + lapCount + ": time = " + lapTime + "s");
        } else {
          Log.w("ActivityCleaner", "Lap " + lapCount + ": time is NULL");
        }
      } while (lapCursor.moveToNext());
    }
    lapCursor.close();

    Log.i("ActivityCleaner", "Activity " + activityId + ": totalDistanceFromLaps = " + totalDistanceFromLaps + "m, totalTimeFromLaps = " + totalTimeFromLaps + "s, _totalTime = " + _totalTime + "ms");

    ContentValues tmp = new ContentValues();
    if (_totalSumHr > 0) {
      long hr = Math.round(_totalSumHr / (double) _totalCount);
      tmp.put(DB.ACTIVITY.AVG_HR, hr);
      tmp.put(DB.ACTIVITY.MAX_HR, _totalMaxHr);
    }
    // Use sum of lap distances (which preserves original autolap distances)
    tmp.put(DB.ACTIVITY.DISTANCE, totalDistanceFromLaps > 0 ? totalDistanceFromLaps : _totalDistance);
    // Use sum of lap times (which includes all laps, even those skipped during recomputation)
    long finalTime = totalTimeFromLaps > 0 ? totalTimeFromLaps : Math.round(_totalTime / 1000.0d);
    tmp.put(DB.ACTIVITY.TIME, finalTime); // also used as a flag for conditionalRecompute
    Log.i("ActivityCleaner", "Activity " + activityId + ": Setting TIME = " + finalTime + "s");

    db.update(DB.ACTIVITY.TABLE, tmp, "_id = " + activityId, null);
  }

  /**
   * Fix corrupted lap distances by rounding laps in 800-1200m range to 1000m.
   * This fixes laps that were incorrectly recalculated from GPS points.
   */
  public static void fixCorruptedLapDistances(SQLiteDatabase db) {
    try {
      String[] cols = new String[] {DB.PRIMARY_KEY, DB.LAP.ACTIVITY, DB.LAP.LAP, DB.LAP.DISTANCE};
      Cursor c = db.query(DB.LAP.TABLE, cols, null, null, null, null, null);
      int fixedCount = 0;
      
      if (c.moveToFirst()) {
        do {
          if (!c.isNull(3)) { // DISTANCE column
            double distance = c.getDouble(3);
            // Round laps in 800-1200m range to 1000m (these are likely corrupted autolap distances)
            if (distance >= 800 && distance <= 1200 && Math.abs(distance - 1000) > 10) {
              long lapId = c.getLong(0);
              ContentValues tmp = new ContentValues();
              tmp.put(DB.LAP.DISTANCE, 1000.0);
              db.update(DB.LAP.TABLE, tmp, "_id = ?", new String[] {Long.toString(lapId)});
              fixedCount++;
            }
          }
        } while (c.moveToNext());
      }
      c.close();
      
      if (fixedCount > 0) {
        Log.i("ActivityCleaner", "Fixed " + fixedCount + " corrupted lap distances to 1000m");
        
        // Recalculate activity total distances from fixed lap distances
        String[] activityCols = new String[] {DB.PRIMARY_KEY};
        Cursor activityCursor = db.query(DB.ACTIVITY.TABLE, activityCols, null, null, null, null, null);
        if (activityCursor.moveToFirst()) {
          do {
            long activityId = activityCursor.getLong(0);
            // Recalculate total distance from lap distances
            String[] lapDistCols = new String[] {DB.LAP.DISTANCE};
            Cursor lapDistCursor = db.query(
                DB.LAP.TABLE,
                lapDistCols,
                DB.LAP.ACTIVITY + " = " + activityId,
                null, null, null, null);
            double totalDistance = 0;
            if (lapDistCursor.moveToFirst()) {
              do {
                if (!lapDistCursor.isNull(0)) {
                  totalDistance += lapDistCursor.getDouble(0);
                }
              } while (lapDistCursor.moveToNext());
            }
            lapDistCursor.close();
            
            if (totalDistance > 0) {
              ContentValues tmp = new ContentValues();
              tmp.put(DB.ACTIVITY.DISTANCE, totalDistance);
              db.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", new String[] {Long.toString(activityId)});
            }
          } while (activityCursor.moveToNext());
        }
        activityCursor.close();
      }
    } catch (Exception e) {
      Log.e("ActivityCleaner", "Error fixing corrupted lap distances: " + e.getMessage(), e);
    }
  }


  public void conditionalRecompute(SQLiteDatabase db) {
    try {
      // get last activity
      long id =
          db.compileStatement("SELECT MAX(_id) FROM " + DB.ACTIVITY.TABLE).simpleQueryForLong();

      // check its TIME field - recompute if it isn't set or is 0
      String[] cols = new String[] {DB.ACTIVITY.TIME};
      Cursor c = db.query(DB.ACTIVITY.TABLE, cols, "_id = " + id, null, null, null, null);
      if (c.moveToFirst()) {
        if (c.isNull(0) || c.getLong(0) == 0) {
          Log.i("ActivityCleaner", "Activity " + id + " has TIME = " + (c.isNull(0) ? "NULL" : c.getLong(0)) + ", recomputing...");
          recompute(db, id);
        } else {
          // Check if the last lap needs fixing (might be incomplete)
          long maxLap = db.compileStatement("SELECT MAX(" + DB.LAP.LAP + ") FROM " + DB.LAP.TABLE + 
              " WHERE " + DB.LAP.ACTIVITY + " = " + id).simpleQueryForLong();
          if (maxLap >= 0) {
            String[] lapCols = new String[] {DB.LAP.DISTANCE};
            Cursor lapCursor = db.query(DB.LAP.TABLE, lapCols, 
                DB.LAP.ACTIVITY + " = " + id + " AND " + DB.LAP.LAP + " = " + maxLap, 
                null, null, null, null);
            boolean needsFix = false;
            if (lapCursor.moveToFirst()) {
              if (lapCursor.isNull(0) || lapCursor.getDouble(0) == 0) {
                // Last lap has 0 distance - needs recompute
                needsFix = true;
                Log.i("ActivityCleaner", "Activity " + id + " last lap has 0 distance, recomputing...");
              } else {
                double lastLapDist = lapCursor.getDouble(0);
                // Check if last lap is exactly 1000m but might be incomplete
                // (we'll recompute to get actual GPS distance)
                if (lastLapDist >= 800 && lastLapDist <= 1200) {
                  needsFix = true;
                  Log.i("ActivityCleaner", "Activity " + id + " last lap is " + lastLapDist + "m, recomputing to check if incomplete...");
                }
              }
            }
            lapCursor.close();
            
            if (needsFix) {
              recompute(db, id);
            } else {
              Log.d("ActivityCleaner", "Activity " + id + " has TIME = " + c.getLong(0) + ", skipping recompute");
            }
          } else {
            Log.d("ActivityCleaner", "Activity " + id + " has TIME = " + c.getLong(0) + ", skipping recompute");
          }
        }
      }
      c.close();
    } catch (IllegalStateException e) {
      Log.e(getClass().getName(), "conditionalRecompute: " + e.getMessage());
    }
  }

  public void recompute(SQLiteDatabase db, long activityId) {
    // Init variables used over laps and communicating with summary
    _totalTime = 0;
    _totalDistance = 0;
    _totalSumHr = 0;
    _totalCount = 0;
    _totalMaxHr = 0;
    _lastLocation = null;
    _isActive = false;

    recomputeLaps(db, activityId);
    recomputeSummary(db, activityId);
  }

  /**
   * Adjust the last lap of the latest activity by adding distance and adjusting time proportionally.
   * This is useful when GPS tracking stopped but the user continued running.
   * Only adjusts if the last lap appears incomplete (distance < 1000m) and hasn't been adjusted yet.
   * 
   * @param db Database connection
   * @param additionalDistanceMeters Additional distance to add to the last lap (in meters)
   * @return true if adjustment was made, false otherwise
   */
  public static boolean adjustLastLap(SQLiteDatabase db, double additionalDistanceMeters) {
    try {
      // Get the latest activity ID
      long activityId = db.compileStatement("SELECT MAX(_id) FROM " + DB.ACTIVITY.TABLE).simpleQueryForLong();
      
      // Get the last lap for this activity
      String[] lapCols = new String[] {DB.LAP.LAP, DB.LAP.DISTANCE, DB.LAP.TIME};
      long maxLap = db.compileStatement("SELECT MAX(" + DB.LAP.LAP + ") FROM " + DB.LAP.TABLE + 
          " WHERE " + DB.LAP.ACTIVITY + " = " + activityId).simpleQueryForLong();
      
      Cursor lapCursor = db.query(
          DB.LAP.TABLE,
          lapCols,
          DB.LAP.ACTIVITY + " = " + activityId + " AND " + DB.LAP.LAP + " = " + maxLap,
          null, null, null, null);
      
      if (lapCursor.moveToFirst()) {
        double currentDistance = lapCursor.isNull(1) ? 0 : lapCursor.getDouble(1);
        long currentTime = lapCursor.isNull(2) ? 0 : lapCursor.getLong(2);
        
        // Only adjust if the lap appears incomplete (distance < 1000m) to avoid repeated adjustments
        // Also check if it's close to the expected adjusted value (within 10m) - if so, already adjusted
        double expectedAdjusted = currentDistance + additionalDistanceMeters;
        if (currentDistance >= 1000 || Math.abs(currentDistance - expectedAdjusted) < 10) {
          Log.i("ActivityCleaner", "Last lap already complete or adjusted (" + currentDistance + "m), skipping adjustment");
          lapCursor.close();
          return false;
        }
        
        // Calculate new distance
        double newDistance = currentDistance + additionalDistanceMeters;
        
        // Calculate new time based on current pace (maintain same pace)
        long newTime = currentTime;
        if (currentDistance > 0 && currentTime > 0) {
          // pace = time / distance, so newTime = newDistance * (currentTime / currentDistance)
          newTime = Math.round(newDistance * (currentTime / currentDistance));
        } else if (currentTime > 0) {
          // If distance was 0 but time exists, estimate based on average pace (e.g., 5 min/km = 300s/km)
          // Use a conservative estimate of 5:00/km pace
          newTime = currentTime + Math.round(additionalDistanceMeters * 0.3); // 300s per 1000m = 0.3s per meter
        } else if (currentDistance > 0) {
          // If time was 0 but distance exists, estimate time based on average pace
          // Use 5:00/km = 300s/km = 0.3s/m
          newTime = Math.round(newDistance * 0.3);
        } else {
          // Both are 0, estimate based on average pace
          newTime = Math.round(additionalDistanceMeters * 0.3);
        }
        
        Log.i("ActivityCleaner", "Adjusting last lap (lap " + maxLap + ") of activity " + activityId + 
            ": distance " + currentDistance + "m -> " + newDistance + "m, time " + currentTime + "s -> " + newTime + "s");
        
        // Update the lap
        ContentValues lapUpdate = new ContentValues();
        lapUpdate.put(DB.LAP.DISTANCE, newDistance);
        lapUpdate.put(DB.LAP.TIME, newTime);
        db.update(
            DB.LAP.TABLE,
            lapUpdate,
            DB.LAP.ACTIVITY + " = " + activityId + " AND " + DB.LAP.LAP + " = " + maxLap,
            null);
        
        // Recompute activity totals from all laps
        ActivityCleaner cleaner = new ActivityCleaner();
        cleaner.recomputeSummary(db, activityId);
        
        Log.i("ActivityCleaner", "Last lap adjusted successfully");
        lapCursor.close();
        return true;
      } else {
        Log.w("ActivityCleaner", "No last lap found for activity " + activityId);
        lapCursor.close();
        return false;
      }
    } catch (Exception e) {
      Log.e("ActivityCleaner", "Error adjusting last lap: " + e.getMessage(), e);
      return false;
    }
  }

  public static void trim(SQLiteDatabase db, long activityId) {
    final String[] cols = new String[] {DB.LAP.LAP};

    ArrayList<Long> laps = new ArrayList<>();
    Cursor c =
        db.query(
            DB.LOCATION.LAP,
            cols,
            DB.LAP.ACTIVITY + " = " + activityId,
            null,
            null,
            null,
            "_id",
            null);
    if (c.moveToFirst()) {
      do {
        laps.add(c.getLong(0));
      } while (c.moveToNext());
    }
    c.close();

    for (long lap : laps) {
      int res = trimLap(db, activityId, lap);
      Log.e("ActivityCleaner", "lap " + lap + " removed " + res + " locations");
    }
  }

  private static final float MIN_DISTANCE = 2f;

  private static int trimLap(SQLiteDatabase db, long activityId, long lap) {
    int cnt = 0;
    final String[] cols =
        new String[] {
          DB.LOCATION.TIME, DB.LOCATION.LATITUDE, DB.LOCATION.LONGITUDE, DB.LOCATION.TYPE, "_id"
        };

    Cursor c =
        db.query(
            DB.LOCATION.TABLE,
            cols,
            DB.LOCATION.ACTIVITY + " = " + activityId + " and " + DB.LOCATION.LAP + " = " + lap,
            null,
            null,
            null,
            "_id",
            null);
    if (c.moveToFirst()) {
      Location[] p = {null, null};
      do {
        Location l = new Location("Dill poh");
        l.setTime(c.getLong(0));
        l.setLatitude(c.getDouble(1));
        l.setLongitude(c.getDouble(2));
        l.setProvider("" + c.getLong(3));

        int type = c.getInt(3);
        switch (type) {
          case DB.LOCATION.TYPE_START:
          case DB.LOCATION.TYPE_RESUME:
            p[0] = l;
            p[1] = null;
            break;
          case DB.LOCATION.TYPE_END:
          case DB.LOCATION.TYPE_PAUSE:
          case DB.LOCATION.TYPE_GPS:
            if (p[0] == null) {
              p[0] = l;
              p[1] = null;
              break;
            } else if (p[1] == null) {
              p[1] = l;
            } else {
              float d1 = p[0].distanceTo(p[1]);
              float d2 = p[0].distanceTo(l);
              if (Math.abs(d1 - d2) <= MIN_DISTANCE) {
                // p[1] is redundant...prune it
                p[1] = l;
                cnt++;
              } else {
                p[0] = p[1];
                p[1] = null;
              }
            }
            break;
        }
      } while (c.moveToNext());
    }
    c.close();
    return cnt;
  }

  /**
   * Deletes locations with given IDs from the database.
   *
   * @param db Database.
   * @param ids ID to delete.
   */
  public static void deleteLocations(SQLiteDatabase db, ArrayList<String> ids) {
    String strIDs = TextUtils.join(",", ids);
    db.execSQL(
        "delete from "
            + DB.LOCATION.TABLE
            + " where _id in ("
            + strIDs
            + ")"
            + " and "
            + DB.LOCATION.TYPE
            + " = "
            + DB.LOCATION.TYPE_GPS);
  }
}
