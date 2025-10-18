package org.runnerup.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.runnerup.common.util.Constants;
import org.runnerup.db.entities.BestTimesEntity;

/**
 * Calculator for finding best times using lap data.
 * 
 * Target distances: 1km, 5km, 10km, Half Marathon (21.1km), Marathon (42.2km)
 * Uses lap data instead of GPS points for more accurate and robust calculations.
 */
public class BestTimesCalculator {

  private static final String TAG = "BestTimesCalculator";
  
  // Target distances in meters
  private static final int[] TARGET_DISTANCES = {1000, 5000, 10000, 21097, 42195};
  
  // Pace validation limits (seconds per km)
  private static final double MIN_PACE_PER_KM = 120.0; // 2:00/km
  private static final double MAX_PACE_PER_KM = 720.0; // 12:00/km

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
        
        // Store top 3 results
        for (int i = 0; i < Math.min(3, results.size()); i++) {
          storeBestTime(db, results.get(i), i + 1);
          totalComputed++;
        }
        
        Log.i(TAG, "Stored " + Math.min(3, results.size()) + " best times for " + targetDistance + "m");
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
      Constants.DB.ACTIVITY.AVG_HR
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
   * Computes best time for a specific distance using lap data.
   */
  private static BestTimeResult computeBestTimeFromLaps(SQLiteDatabase db, Long activityId, int targetDistance) {
    ActivityInfo activityInfo = getActivityInfo(db, activityId);
    if (activityInfo == null) {
      return null;
    }
    
    List<LapInfo> laps = getLaps(db, activityId);
    if (laps.isEmpty()) {
      return null;
    }
    
    BestTimeResult bestResult = null;
    long bestTime = Long.MAX_VALUE;
    
    // Strategy based on target distance
    if (targetDistance == 1000) {
      // For 1km: find the fastest single lap that's close to 1km
      bestResult = findFastestSingleLap(laps, targetDistance, activityInfo);
    } else if (targetDistance == 5000) {
      // For 5km: find fastest 5 consecutive laps
      bestResult = findFastestConsecutiveLaps(laps, targetDistance, activityInfo, 5);
    } else if (targetDistance == 10000) {
      // For 10km: find fastest 10 consecutive laps
      bestResult = findFastestConsecutiveLaps(laps, targetDistance, activityInfo, 10);
    } else if (targetDistance == 21097) {
      // For Half Marathon: find fastest ~21 consecutive laps
      bestResult = findFastestConsecutiveLaps(laps, targetDistance, activityInfo, 21);
    } else if (targetDistance == 42195) {
      // For Marathon: find fastest ~42 consecutive laps
      bestResult = findFastestConsecutiveLaps(laps, targetDistance, activityInfo, 42);
    }
    
    return bestResult;
  }

  /**
   * Finds the fastest single lap close to target distance.
   */
  private static BestTimeResult findFastestSingleLap(List<LapInfo> laps, int targetDistance, ActivityInfo activityInfo) {
    BestTimeResult bestResult = null;
    long bestTime = Long.MAX_VALUE;
    
    for (LapInfo lap : laps) {
      // Check if lap distance is close to target (within 5% tolerance for complete laps)
      double distanceRatio = lap.distanceM / targetDistance;
      Log.d(TAG, "Lap " + lap.lapNumber + ": " + lap.distanceM + "m, ratio: " + String.format("%.2f", distanceRatio));
      
      if (distanceRatio >= 0.95 && distanceRatio <= 1.05) {
        // Validate pace
        double pacePerKm = lap.timeSeconds / (lap.distanceM / 1000.0);
        Log.d(TAG, "Valid lap " + lap.lapNumber + ": pace " + String.format("%.1f", pacePerKm) + "s/km");
        
        if (pacePerKm >= MIN_PACE_PER_KM && pacePerKm <= MAX_PACE_PER_KM) {
          if (lap.timeSeconds < bestTime) {
            bestResult = new BestTimeResult();
            bestResult.activityId = activityInfo.activityId;
            bestResult.startTime = activityInfo.startTime;
            bestResult.timeMs = lap.timeSeconds * 1000; // Convert to milliseconds for storage
            bestResult.pacePerKm = pacePerKm;
            bestResult.avgHr = lap.avgHr > 0 ? lap.avgHr : activityInfo.avgHr;
            bestTime = lap.timeSeconds;
            Log.d(TAG, "New best time: " + lap.timeSeconds + "s for " + lap.distanceM + "m");
          }
        }
      } else {
        Log.d(TAG, "Skipping lap " + lap.lapNumber + " - distance ratio " + String.format("%.2f", distanceRatio) + " outside 0.95-1.05 range");
      }
    }
    
    return bestResult;
  }

  /**
   * Finds the fastest consecutive laps that add up to approximately target distance.
   */
  private static BestTimeResult findFastestConsecutiveLaps(List<LapInfo> laps, int targetDistance, ActivityInfo activityInfo, int expectedLapCount) {
    BestTimeResult bestResult = null;
    long bestTime = Long.MAX_VALUE;
    
    // Try different starting positions
    for (int startIdx = 0; startIdx <= laps.size() - expectedLapCount; startIdx++) {
      long totalTime = 0;
      double totalDistance = 0;
      int totalHr = 0;
      int hrCount = 0;
      
      // Sum up consecutive laps
      for (int i = 0; i < expectedLapCount && startIdx + i < laps.size(); i++) {
        LapInfo lap = laps.get(startIdx + i);
        totalTime += lap.timeSeconds;
        totalDistance += lap.distanceM;
        if (lap.avgHr > 0) {
          totalHr += lap.avgHr;
          hrCount++;
        }
      }
      
      // Check if total distance is close to target (within 5% tolerance for complete laps)
      double distanceRatio = totalDistance / targetDistance;
      if (distanceRatio >= 0.95 && distanceRatio <= 1.05) {
        // Validate pace
        double pacePerKm = totalTime / (totalDistance / 1000.0);
        if (pacePerKm >= MIN_PACE_PER_KM && pacePerKm <= MAX_PACE_PER_KM) {
          if (totalTime < bestTime) {
            bestResult = new BestTimeResult();
            bestResult.activityId = activityInfo.activityId;
            bestResult.startTime = activityInfo.startTime;
            bestResult.timeMs = totalTime * 1000; // Convert to milliseconds for storage
            bestResult.pacePerKm = pacePerKm;
            bestResult.avgHr = hrCount > 0 ? totalHr / hrCount : activityInfo.avgHr;
            bestTime = totalTime;
          }
        }
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
    int targetDistance;
  }
}