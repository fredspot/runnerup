package org.runnerup.analytics;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.runnerup.common.util.Constants;
import org.runnerup.data.ComputationTracker;
import org.runnerup.data.RunningActivityReader;

/**
 * Calculator for computing yearly and monthly running statistics using lap data.
 * 
 * Computes total distance, average pace, and average run length for each year and month.
 * Uses lap data instead of GPS points for more accurate and robust calculations.
 */
public class StatisticsCalculator {

  private static final String TAG = "StatisticsCalculator";

  /**
   * Checks if statistics data is stale (needs recomputation).
   * 
   * @param db Database instance
   * @return true if data is stale and needs recomputation
   */
  public static boolean isDataStale(SQLiteDatabase db) {
    return ComputationTracker.isStaleByLastActivityId(db, ComputationTracker.TYPE_STATISTICS);
  }

  /**
   * Updates computation tracking after successful computation.
   * 
   * @param db Database instance
   * @param lastActivityId ID of the last activity processed
   */
  public static void updateComputationTracking(SQLiteDatabase db, long lastActivityId) {
    ComputationTracker.updateLastActivityId(db, ComputationTracker.TYPE_STATISTICS, lastActivityId);
  }

  /**
   * Computes statistics for all years and months using lap data.
   * 
   * @param db Database instance
   * @return Number of statistics records computed
   */
  public static int computeStatistics(SQLiteDatabase db) {
    Log.i(TAG, "Starting statistics computation using lap data");
    
    try {
      // Clear existing statistics
      int yearlyDeletedCount = db.delete(Constants.DB.YEARLY_STATS.TABLE, null, null);
      int monthlyDeletedCount = db.delete(Constants.DB.MONTHLY_STATS.TABLE, null, null);
      Log.i(TAG, "Cleared " + yearlyDeletedCount + " yearly and " + monthlyDeletedCount + " monthly statistics records");
      
      // Get all running activities
      List<Long> activityIds = RunningActivityReader.getRunningActivityIds(db);
      Log.i(TAG, "Found " + activityIds.size() + " running activities");
      
      if (activityIds.isEmpty()) {
        Log.i(TAG, "No running activities found, skipping statistics computation");
        return 0;
      }
      
      long lastActivityId = 0;
      
      // Compute yearly statistics
      Map<Integer, YearlyStats> yearlyStats = computeYearlyStats(db, activityIds);
      Log.i(TAG, "Computed statistics for " + yearlyStats.size() + " years");
      
      // Compute monthly statistics
      Map<String, MonthlyStats> monthlyStats = computeMonthlyStats(db, activityIds);
      Log.i(TAG, "Computed statistics for " + monthlyStats.size() + " year-month combinations");
      
      // Store yearly statistics
      int yearlyCount = 0;
      for (YearlyStats stats : yearlyStats.values()) {
        storeYearlyStats(db, stats);
        yearlyCount++;
      }
      
      // Store monthly statistics
      int monthlyCount = 0;
      for (MonthlyStats stats : monthlyStats.values()) {
        storeMonthlyStats(db, stats);
        monthlyCount++;
      }
      
      int totalComputed = yearlyCount + monthlyCount;
      
      // Update computation tracking with the latest activity ID
      if (!activityIds.isEmpty()) {
        lastActivityId = activityIds.get(0); // Activities are ordered by start_time DESC, so first is latest
        updateComputationTracking(db, lastActivityId);
      }
      
      Log.i(TAG, "Statistics computation completed. Total: " + totalComputed + " records");
      return totalComputed;
      
    } catch (Exception e) {
      Log.e(TAG, "Error computing statistics: " + e.getMessage(), e);
      return 0;
    }
  }

  /**
   * Computes yearly statistics by aggregating lap data by year.
   */
  private static Map<Integer, YearlyStats> computeYearlyStats(SQLiteDatabase db, List<Long> activityIds) {
    Map<Integer, YearlyStats> yearlyStats = new HashMap<>();
    
    for (Long activityId : activityIds) {
      try {
        RunningActivityReader.ActivityRow activityInfo =
            RunningActivityReader.getActivity(db, activityId);
        if (activityInfo == null) {
          continue;
        }
        
        // Extract year from start_time (Unix timestamp in seconds)
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(activityInfo.startTime * 1000);
        int year = cal.get(Calendar.YEAR);
        
        // Get laps for this activity
        List<RunningActivityReader.LapRow> laps = RunningActivityReader.getLaps(db, activityId);
        if (laps.isEmpty()) {
          continue;
        }
        
        // Aggregate lap data
        double totalDistance = 0;
        long totalTime = 0;
        
        for (RunningActivityReader.LapRow lap : laps) {
          totalDistance += lap.distanceM;
          totalTime += lap.timeSeconds;
        }
        
        // Get or create yearly stats for this year
        YearlyStats stats = yearlyStats.get(year);
        if (stats == null) {
          stats = new YearlyStats();
          stats.year = year;
          stats.totalDistance = 0.0;
          stats.totalTime = 0L;
          stats.runCount = 0;
          yearlyStats.put(year, stats);
        }
        
        // Add this activity's data
        stats.totalDistance += totalDistance;
        stats.totalTime += totalTime;
        stats.runCount++;
        
        Log.d(TAG, "Added activity " + activityId + " to year " + year + ": " + totalDistance + "m, " + totalTime + "s");
        
      } catch (Exception e) {
        Log.w(TAG, "Error processing activity " + activityId + ": " + e.getMessage());
      }
    }
    
    // Calculate averages for each year
    for (YearlyStats stats : yearlyStats.values()) {
      if (stats.totalDistance > 0) {
        stats.avgPace = stats.totalTime / (stats.totalDistance / 1000.0); // seconds per km
        stats.avgRunLength = stats.totalDistance / stats.runCount; // meters per run
      }
      Log.i(TAG, "Year " + stats.year + ": " + stats.totalDistance + "m, " + stats.runCount + " runs, " + 
            String.format("%.1f", stats.avgPace) + "s/km avg pace");
    }
    
    return yearlyStats;
  }

  /**
   * Computes monthly statistics by aggregating lap data by year and month.
   */
  private static Map<String, MonthlyStats> computeMonthlyStats(SQLiteDatabase db, List<Long> activityIds) {
    Map<String, MonthlyStats> monthlyStats = new HashMap<>();
    
    for (Long activityId : activityIds) {
      try {
        RunningActivityReader.ActivityRow activityInfo =
            RunningActivityReader.getActivity(db, activityId);
        if (activityInfo == null) {
          continue;
        }
        
        // Extract year and month from start_time (Unix timestamp in seconds)
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(activityInfo.startTime * 1000);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1; // Calendar.MONTH is 0-based
        
        // Get laps for this activity
        List<RunningActivityReader.LapRow> laps = RunningActivityReader.getLaps(db, activityId);
        if (laps.isEmpty()) {
          continue;
        }
        
        // Aggregate lap data
        double totalDistance = 0;
        long totalTime = 0;
        
        for (RunningActivityReader.LapRow lap : laps) {
          totalDistance += lap.distanceM;
          totalTime += lap.timeSeconds;
        }
        
        // Create key for year-month combination
        String key = year + "-" + month;
        
        // Get or create monthly stats for this year-month
        MonthlyStats stats = monthlyStats.get(key);
        if (stats == null) {
          stats = new MonthlyStats();
          stats.year = year;
          stats.month = month;
          stats.totalDistance = 0.0;
          stats.totalTime = 0L;
          stats.runCount = 0;
          monthlyStats.put(key, stats);
        }
        
        // Add this activity's data
        stats.totalDistance += totalDistance;
        stats.totalTime += totalTime;
        stats.runCount++;
        
        Log.d(TAG, "Added activity " + activityId + " to " + year + "-" + month + ": " + totalDistance + "m, " + totalTime + "s");
        
      } catch (Exception e) {
        Log.w(TAG, "Error processing activity " + activityId + ": " + e.getMessage());
      }
    }
    
    // Calculate averages for each month
    for (MonthlyStats stats : monthlyStats.values()) {
      if (stats.totalDistance > 0) {
        stats.avgPace = stats.totalTime / (stats.totalDistance / 1000.0); // seconds per km
        stats.avgRunLength = stats.totalDistance / stats.runCount; // meters per run
      }
      Log.i(TAG, "Month " + stats.year + "-" + stats.month + ": " + stats.totalDistance + "m, " + stats.runCount + " runs, " + 
            String.format("%.1f", stats.avgPace) + "s/km avg pace");
    }
    
    return monthlyStats;
  }

  /**
   * Stores yearly statistics in the database.
   */
  private static void storeYearlyStats(SQLiteDatabase db, YearlyStats stats) {
    ContentValues values = new ContentValues();
    values.put(Constants.DB.YEARLY_STATS.YEAR, stats.year);
    values.put(Constants.DB.YEARLY_STATS.TOTAL_DISTANCE, stats.totalDistance);
    values.put(Constants.DB.YEARLY_STATS.AVG_PACE, stats.avgPace);
    values.put(Constants.DB.YEARLY_STATS.AVG_RUN_LENGTH, stats.avgRunLength);
    values.put(Constants.DB.YEARLY_STATS.RUN_COUNT, stats.runCount);
    
    db.insert(Constants.DB.YEARLY_STATS.TABLE, null, values);
  }

  /**
   * Stores monthly statistics in the database.
   */
  private static void storeMonthlyStats(SQLiteDatabase db, MonthlyStats stats) {
    ContentValues values = new ContentValues();
    values.put(Constants.DB.MONTHLY_STATS.YEAR, stats.year);
    values.put(Constants.DB.MONTHLY_STATS.MONTH, stats.month);
    values.put(Constants.DB.MONTHLY_STATS.TOTAL_DISTANCE, stats.totalDistance);
    values.put(Constants.DB.MONTHLY_STATS.AVG_PACE, stats.avgPace);
    values.put(Constants.DB.MONTHLY_STATS.AVG_RUN_LENGTH, stats.avgRunLength);
    values.put(Constants.DB.MONTHLY_STATS.RUN_COUNT, stats.runCount);
    
    db.insert(Constants.DB.MONTHLY_STATS.TABLE, null, values);
  }

  private static class YearlyStats {
    int year;
    double totalDistance;
    long totalTime;
    double avgPace;
    double avgRunLength;
    int runCount;
  }

  private static class MonthlyStats {
    int year;
    int month;
    double totalDistance;
    long totalTime;
    double avgPace;
    double avgRunLength;
    int runCount;
  }
}
