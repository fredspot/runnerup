package org.runnerup.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.runnerup.common.util.Constants;
import org.runnerup.data.entities.BestTimesEntity;

/**
 * Calculator for finding best times using lap data.
 * 
 * Target distances: 1km, 5km, 10km, Half Marathon (21.1km), Marathon (42.2km)
 * Uses lap data instead of GPS points for more accurate and robust calculations.
 */
public class BestTimesCalculator {

  private static final String TAG = "BestTimesCalculator";
  
  // Target distances in meters
  private static final int[] TARGET_DISTANCES = {1000, 5000, 10000, 15000, 20000, 21097, 30000, 40000, 42195};
  
  // Pace validation limits (seconds per km)
  private static final double MIN_PACE_PER_KM = 120.0; // 2:00/km
  private static final double MAX_PACE_PER_KM = 720.0; // 12:00/km

  /**
   * Checks if best times data is stale (needs recomputation).
   * 
   * @param db Database instance
   * @return true if data is stale and needs recomputation
   */
  public static boolean isDataStale(SQLiteDatabase db) {
    try {
      // Get last computation info
      String sql = "SELECT " + Constants.DB.COMPUTATION_TRACKING.LAST_ACTIVITY_ID + 
                   " FROM " + Constants.DB.COMPUTATION_TRACKING.TABLE +
                   " WHERE " + Constants.DB.COMPUTATION_TRACKING.COMPUTATION_TYPE + " = 'best_times'";
      
      try (Cursor cursor = db.rawQuery(sql, null)) {
        if (!cursor.moveToFirst()) {
          // No tracking record exists, data is stale
          Log.i(TAG, "No computation tracking record found for best_times, data is stale");
          return true;
        }
        
        long lastActivityId = cursor.getLong(0);
        
        // Check if distance list has changed by comparing computed distances with target distances
        String distanceCheckSql = "SELECT DISTINCT " + Constants.DB.BEST_TIMES.DISTANCE + 
                                " FROM " + Constants.DB.BEST_TIMES.TABLE + 
                                " ORDER BY " + Constants.DB.BEST_TIMES.DISTANCE;
        
        List<Integer> computedDistances = new ArrayList<>();
        try (Cursor distanceCursor = db.rawQuery(distanceCheckSql, null)) {
          while (distanceCursor.moveToNext()) {
            computedDistances.add(distanceCursor.getInt(0));
          }
        }
        
        // Check if all target distances are present in computed data
        boolean allDistancesPresent = true;
        for (int targetDistance : TARGET_DISTANCES) {
          if (!computedDistances.contains(targetDistance)) {
            Log.i(TAG, "Distance " + targetDistance + "m not found in computed data, data is stale");
            allDistancesPresent = false;
            break;
          }
        }
        
        if (!allDistancesPresent) {
          return true; // Distance list has changed, data is stale
        }
        
        // Get latest activity ID
        String latestSql = "SELECT MAX(" + Constants.DB.PRIMARY_KEY + ") FROM " + Constants.DB.ACTIVITY.TABLE +
                          " WHERE " + Constants.DB.ACTIVITY.SPORT + " = ? AND " + Constants.DB.ACTIVITY.DELETED + " = ?";
        
        try (Cursor latestCursor = db.rawQuery(latestSql, new String[]{
          String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING), "0"})) {
          
          if (latestCursor.moveToFirst()) {
            long latestActivityId = latestCursor.getLong(0);
            boolean isStale = latestActivityId > lastActivityId;
            Log.i(TAG, "Best times staleness check: last=" + lastActivityId + 
                      ", latest=" + latestActivityId + ", stale=" + isStale);
            return isStale;
          }
        }
      }
      
      return true; // Default to stale if we can't determine
    } catch (Exception e) {
      Log.e(TAG, "Error checking best times staleness: " + e.getMessage(), e);
      return true; // Default to stale on error
    }
  }

  /**
   * Updates computation tracking after successful computation.
   * 
   * @param db Database instance
   * @param lastActivityId ID of the last activity processed
   */
  public static void updateComputationTracking(SQLiteDatabase db, long lastActivityId) {
    try {
      long currentTime = System.currentTimeMillis() / 1000; // Unix timestamp in seconds
      
      ContentValues values = new ContentValues();
      values.put(Constants.DB.COMPUTATION_TRACKING.COMPUTATION_TYPE, "best_times");
      values.put(Constants.DB.COMPUTATION_TRACKING.LAST_COMPUTED_TIME, currentTime);
      values.put(Constants.DB.COMPUTATION_TRACKING.LAST_ACTIVITY_ID, lastActivityId);
      
      // Use INSERT OR REPLACE to handle both new and existing records
      db.replace(Constants.DB.COMPUTATION_TRACKING.TABLE, null, values);
      
      Log.i(TAG, "Updated computation tracking for best_times: activityId=" + lastActivityId + 
                ", time=" + currentTime);
    } catch (Exception e) {
      Log.e(TAG, "Error updating computation tracking: " + e.getMessage(), e);
    }
  }

  /**
   * Computes best times for all target distances using lap data.
   * 
   * @param db Database instance
   * @return Number of best times computed
   */
  public static int computeBestTimes(SQLiteDatabase db) {
    Log.i(TAG, "Starting best times computation using lap data");
    
    try {
      // Clear existing best times
      int deletedCount = db.delete(Constants.DB.BEST_TIMES.TABLE, null, null);
      Log.i(TAG, "Cleared " + deletedCount + " existing best times records");
      
      // Get all running activities
      List<Long> activityIds = getRunningActivities(db);
      Log.i(TAG, "Found " + activityIds.size() + " running activities");
      
      int totalComputed = 0;
      long lastActivityId = 0;
      
      // Process each target distance
      for (int targetDistance : TARGET_DISTANCES) {
        Log.i(TAG, "Computing best times for " + targetDistance + "m using lap data");
        
        List<BestTimeResult> results = new ArrayList<>();
        
        // Process each activity
        for (Long activityId : activityIds) {
          Log.d(TAG, "Processing activity " + activityId + " for distance " + targetDistance + "m");
          try {
            BestTimeResult result = computeBestTimeFromLaps(db, activityId, targetDistance);
            if (result != null) {
              result.targetDistance = targetDistance;
              results.add(result);
              Log.d(TAG, "Found best time for activity " + activityId + ": " + result.timeMs + "ms");
            } else {
              Log.d(TAG, "No valid best time found for activity " + activityId);
            }
          } catch (Exception e) {
            Log.w(TAG, "Error processing activity " + activityId + ": " + e.getMessage());
          }
        }
        
        // Sort by time (fastest first)
        Collections.sort(results, (a, b) -> Long.compare(a.timeMs, b.timeMs));
        
        // Store top 25 results
        for (int i = 0; i < Math.min(25, results.size()); i++) {
          storeBestTime(db, results.get(i), i + 1);
          totalComputed++;
        }
        
        Log.i(TAG, "Stored " + Math.min(25, results.size()) + " best times for " + targetDistance + "m");
      }
      
      // Update computation tracking with the latest activity ID
      if (!activityIds.isEmpty()) {
        lastActivityId = activityIds.get(0); // Activities are ordered by start_time DESC, so first is latest
        updateComputationTracking(db, lastActivityId);
      }
      
      Log.i(TAG, "Best times computation completed. Total: " + totalComputed);
      return totalComputed;
      
    } catch (Exception e) {
      Log.e(TAG, "Error computing best times: " + e.getMessage(), e);
      return 0;
    }
  }

  /**
   * Gets all running activities (SPORT_RUNNING = 0).
   */
  private static List<Long> getRunningActivities(SQLiteDatabase db) {
    List<Long> activityIds = new ArrayList<>();
    
    String[] columns = {Constants.DB.PRIMARY_KEY};
    String selection = Constants.DB.ACTIVITY.SPORT + " = ? AND " + Constants.DB.ACTIVITY.DELETED + " = ?";
    String[] selectionArgs = {String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING), "0"};
    String orderBy = Constants.DB.ACTIVITY.START_TIME + " DESC";
    
    try (Cursor cursor = db.query(Constants.DB.ACTIVITY.TABLE, columns, selection, selectionArgs, null, null, orderBy)) {
      while (cursor.moveToNext()) {
        activityIds.add(cursor.getLong(0));
      }
    }
    
    return activityIds;
  }

  /**
   * Gets activity info (start time, total distance, total time, average HR).
   */
  private static ActivityInfo getActivityInfo(SQLiteDatabase db, Long activityId) {
    String[] columns = {
      Constants.DB.ACTIVITY.START_TIME,
      Constants.DB.ACTIVITY.DISTANCE,
      Constants.DB.ACTIVITY.TIME,
      Constants.DB.ACTIVITY.AVG_HR,
      Constants.DB.ACTIVITY.MAX_HR
    };
    
    try (Cursor cursor = db.query(Constants.DB.ACTIVITY.TABLE, columns, 
        Constants.DB.PRIMARY_KEY + " = ?", 
        new String[]{String.valueOf(activityId)}, null, null, null)) {
      
      if (cursor.moveToFirst()) {
        ActivityInfo info = new ActivityInfo();
        info.activityId = activityId;
        info.startTime = cursor.getLong(0);
        info.totalDistance = cursor.getDouble(1);
        info.totalTime = cursor.getLong(2);
        info.avgHr = cursor.getInt(3);
        info.maxHr = cursor.getInt(4);
        return info;
      }
    }
    
    return null;
  }

  /**
   * Gets laps for an activity, ordered by lap number.
   */
  private static List<LapInfo> getLaps(SQLiteDatabase db, Long activityId) {
    List<LapInfo> laps = new ArrayList<>();
    
    String[] columns = {
      Constants.DB.LAP.LAP,
      Constants.DB.LAP.TIME,
      Constants.DB.LAP.DISTANCE,
      Constants.DB.LAP.AVG_HR
    };
    
    String selection = Constants.DB.LAP.ACTIVITY + " = ?";
    String[] selectionArgs = {String.valueOf(activityId)};
    String orderBy = Constants.DB.LAP.LAP + " ASC";
    
    try (Cursor cursor = db.query(Constants.DB.LAP.TABLE, columns, selection, selectionArgs, null, null, orderBy)) {
      while (cursor.moveToNext()) {
        LapInfo lap = new LapInfo();
        lap.lapNumber = cursor.getInt(0);
        lap.timeSeconds = cursor.getLong(1); // Time is already in seconds
        lap.distanceM = cursor.getDouble(2);
        lap.avgHr = cursor.getInt(3);
        laps.add(lap);
      }
    }
    
    return laps;
  }

  /**
   * Computes the best time for a target distance using a sliding cumulative lap window.
   *
   * <p>For every starting lap {@code i} we extend a window forward, accumulating distance and time
   * (including any lap intensity — rest/recovery laps in an interval workout are <em>not</em>
   * skipped, so a 10 km interval workout's overall 10 km elapsed time is fairly considered against
   * the 10 km best-time list). The window stops as soon as it covers at least {@code
   * targetDistance}; if the last included lap overshoots, the segment time is linearly interpolated
   * over that lap so it represents exactly {@code targetDistance} at the lap's average pace. The
   * best (smallest) such segment time across all start positions is the activity's best time for
   * that target.
   */
  private static BestTimeResult computeBestTimeFromLaps(
      SQLiteDatabase db, Long activityId, int targetDistance) {
    ActivityInfo activityInfo = getActivityInfo(db, activityId);
    if (activityInfo == null) {
      return null;
    }

    if (activityInfo.totalDistance < targetDistance) {
      Log.d(
          TAG,
          "Activity "
              + activityId
              + " total distance ("
              + activityInfo.totalDistance
              + "m) is less than target ("
              + targetDistance
              + "m), skipping");
      return null;
    }

    List<LapInfo> laps = getLaps(db, activityId);
    if (laps.isEmpty()) {
      return null;
    }

    return findFastestSegment(laps, targetDistance, activityInfo);
  }

  /**
   * Sliding cumulative window. Returns the fastest contiguous lap segment that covers exactly
   * {@code targetDistance} (interpolating into the final overshooting lap), or {@code null} if no
   * such segment exists with a sane pace.
   *
   * <p>Time is sacrosanct: a lap that has elapsed time but zero recorded distance (e.g. a 60 s
   * recovery during which GPS dropped out) still contributes its time to the window — silently
   * skipping it would give the run a free 60 s shortcut that did not actually happen.
   */
  private static BestTimeResult findFastestSegment(
      List<LapInfo> laps, int targetDistance, ActivityInfo activityInfo) {
    BestTimeResult bestResult = null;
    double bestTimeSec = Double.MAX_VALUE;

    int n = laps.size();
    for (int startIdx = 0; startIdx < n; startIdx++) {
      double accumDist = 0;
      double accumTime = 0;
      int accumHrSum = 0;
      int accumHrCount = 0;

      for (int j = startIdx; j < n; j++) {
        LapInfo lap = laps.get(j);
        if (lap.timeSeconds <= 0) {
          // Lap with no recorded elapsed time is a pure marker — skip it. Skipping
          // a real-time lap would artificially shorten the segment.
          continue;
        }
        // Note: we deliberately do NOT skip laps with distanceM == 0 but timeSeconds > 0.
        // These are real elapsed time the runner experienced (e.g. a 60 s recovery during
        // which GPS happened to drop out, or a paused-rest with no movement). Their time
        // must remain inside the window or the segment pace would be falsely improved.

        double remaining = targetDistance - accumDist;
        if (lap.distanceM < remaining) {
          accumDist += lap.distanceM;
          accumTime += lap.timeSeconds;
          if (lap.avgHr > 0) {
            accumHrSum += lap.avgHr;
            accumHrCount++;
          }
          continue;
        }

        // This lap completes (or overshoots) the target. Linearly interpolate its time
        // proportionally to the remaining distance covered within it.
        double partialTime = lap.timeSeconds * (remaining / lap.distanceM);
        double segmentTime = accumTime + partialTime;
        if (lap.avgHr > 0) {
          // Weight HR by the partial fraction of this lap consumed.
          accumHrSum += (int) Math.round(lap.avgHr);
          accumHrCount++;
        }

        double pacePerKm = segmentTime / (targetDistance / 1000.0);
        if (pacePerKm >= MIN_PACE_PER_KM && pacePerKm <= MAX_PACE_PER_KM) {
          if (segmentTime < bestTimeSec) {
            bestTimeSec = segmentTime;
            bestResult = new BestTimeResult();
            bestResult.activityId = activityInfo.activityId;
            bestResult.startTime = activityInfo.startTime;
            bestResult.timeMs = Math.round(segmentTime * 1000.0);
            bestResult.pacePerKm = pacePerKm;
            bestResult.avgHr =
                accumHrCount > 0 ? accumHrSum / accumHrCount : activityInfo.avgHr;
            bestResult.maxHr = activityInfo.maxHr > 0 ? activityInfo.maxHr : null;
          }
        }
        // Done with this start position.
        break;
      }
    }

    return bestResult;
  }

  /**
   * Stores a best time result in the database.
   */
  private static void storeBestTime(SQLiteDatabase db, BestTimeResult result, int rank) {
    ContentValues values = new ContentValues();
    values.put(Constants.DB.BEST_TIMES.DISTANCE, result.targetDistance);
    values.put(Constants.DB.BEST_TIMES.TIME, result.timeMs);
    values.put(Constants.DB.BEST_TIMES.PACE, result.pacePerKm);
    values.put(Constants.DB.BEST_TIMES.ACTIVITY_ID, result.activityId);
    values.put(Constants.DB.BEST_TIMES.START_TIME, result.startTime);
    values.put(Constants.DB.BEST_TIMES.AVG_HR, result.avgHr);
    if (result.maxHr != null && result.maxHr > 0) {
      values.put(Constants.DB.BEST_TIMES.MAX_HR, result.maxHr);
    }
    values.put(Constants.DB.BEST_TIMES.RANK, rank);
    
    db.insert(Constants.DB.BEST_TIMES.TABLE, null, values);
  }

  /**
   * Data classes for internal use.
   */
  private static class ActivityInfo {
    Long activityId;
    Long startTime;
    Double totalDistance;
    Long totalTime;
    Integer avgHr;
    int maxHr;
  }

  private static class LapInfo {
    int lapNumber;
    long timeSeconds; // Time in seconds
    double distanceM;
    int avgHr;
  }

  private static class BestTimeResult {
    Long activityId;
    Long startTime;
    Long timeMs;
    Double pacePerKm;
    Integer avgHr;
    Integer maxHr;
    int targetDistance;
  }
}