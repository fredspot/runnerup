package org.runnerup.analytics;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.runnerup.common.util.Constants;
import org.runnerup.data.BestTimesDistances;
import org.runnerup.data.ComputationTracker;
import org.runnerup.data.RunningActivityReader;
import org.runnerup.data.RunningPaceBounds;
import org.runnerup.data.entities.BestTimesEntity;

/**
 * Calculator for finding best times using lap data.
 * 
 * Target distances: 1km, 5km, 10km, Half Marathon (21.1km), Marathon (42.2km)
 * Uses lap data instead of GPS points for more accurate and robust calculations.
 */
public class BestTimesCalculator {

  private static final String TAG = "BestTimesCalculator";
  
  private static final int[] TARGET_DISTANCES = BestTimesDistances.TARGET_DISTANCES;
  
  private static final double MIN_PACE_PER_KM = RunningPaceBounds.BEST_TIMES_MIN_SEC_PER_KM;
  private static final double MAX_PACE_PER_KM = RunningPaceBounds.BEST_TIMES_MAX_SEC_PER_KM;

  /**
   * Checks if best times data is stale (needs recomputation).
   * 
   * @param db Database instance
   * @return true if data is stale and needs recomputation
   */
  public static boolean isDataStale(SQLiteDatabase db) {
    if (!allTargetDistancesPresent(db)) {
      return true;
    }
    return ComputationTracker.isStaleByLastActivityId(db, ComputationTracker.TYPE_BEST_TIMES);
  }

  private static boolean allTargetDistancesPresent(SQLiteDatabase db) {
    String distanceCheckSql =
        "SELECT DISTINCT "
            + Constants.DB.BEST_TIMES.DISTANCE
            + " FROM "
            + Constants.DB.BEST_TIMES.TABLE
            + " ORDER BY "
            + Constants.DB.BEST_TIMES.DISTANCE;

    List<Integer> computedDistances = new ArrayList<>();
    try (Cursor distanceCursor = db.rawQuery(distanceCheckSql, null)) {
      while (distanceCursor.moveToNext()) {
        computedDistances.add(distanceCursor.getInt(0));
      }
    }

    for (int targetDistance : TARGET_DISTANCES) {
      if (!computedDistances.contains(targetDistance)) {
        Log.i(TAG, "Distance " + targetDistance + "m not found in computed data, data is stale");
        return false;
      }
    }
    return true;
  }

  /**
   * Updates computation tracking after successful computation.
   * 
   * @param db Database instance
   * @param lastActivityId ID of the last activity processed
   */
  public static void updateComputationTracking(SQLiteDatabase db, long lastActivityId) {
    ComputationTracker.updateLastActivityId(db, ComputationTracker.TYPE_BEST_TIMES, lastActivityId);
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
      List<Long> activityIds = RunningActivityReader.getRunningActivityIds(db);
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
    RunningActivityReader.ActivityRow activityInfo =
        RunningActivityReader.getActivityWithHr(db, activityId);
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

    List<RunningActivityReader.LapRow> laps = RunningActivityReader.getLapsWithHr(db, activityId);
    if (laps.isEmpty()) {
      return null;
    }

    return BestTimesSegmentFinder.findFastestSegment(laps, targetDistance, activityInfo);
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

  static class BestTimeResult {
    Long activityId;
    Long startTime;
    Long timeMs;
    Double pacePerKm;
    Integer avgHr;
    Integer maxHr;
    int targetDistance;
  }
}