/*
 * Copyright (C) 2024 RunnerUp
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

package org.runnerup.analytics;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.util.Pair;
import java.util.Calendar;
import java.util.Locale;
import org.runnerup.common.util.Constants;
import org.runnerup.core.util.HRZones;
import org.runnerup.data.ComputationTracker;
import org.runnerup.data.RunningPaceBounds;

public class MonthlyComparisonCalculator implements Constants {

  private static final String TAG = "MonthlyComparisonCalc";

  /** Minimum monthly volume to include a month in zone-pace metrics. */
  private static final double MIN_MONTH_DISTANCE_M = 50_000;

  private static final int MIN_MONTH_RUN_COUNT = 2;
  private static final double MIN_ACTIVITY_DISTANCE_M = 2_000;
  private static final double MIN_LAP_DISTANCE_M = 400;
  private static final double MIN_PACE_SEC_PER_KM = RunningPaceBounds.MONTHLY_MIN_SEC_PER_KM;
  private static final double MAX_PACE_SEC_PER_KM = RunningPaceBounds.MONTHLY_MAX_SEC_PER_KM;

  private static final String[] CURRENT_AVG_PACE_ZONE_COLUMNS = {
    Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE_ZONE_1,
    Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE_ZONE_2,
    Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE_ZONE_3,
    Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE_ZONE_4
  };
  private static final String[] OTHER_AVG_PACE_ZONE_COLUMNS = {
    Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE_ZONE_1,
    Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE_ZONE_2,
    Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE_ZONE_3,
    Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE_ZONE_4
  };
  private static final String[] BEST_AVG_PACE_ZONE_COLUMNS = {
    Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_1,
    Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_2,
    Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_3,
    Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_4
  };
  private static final String[] BEST_AVG_PACE_ZONE_MONTH_COLUMNS = {
    Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_1_MONTH,
    Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_2_MONTH,
    Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_3_MONTH,
    Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_4_MONTH
  };

  /**
   * Computes monthly comparison statistics.
   *
   * @param db Database instance
   * @return number of records computed
   */
  public static int computeComparison(SQLiteDatabase db) {
    return computeComparison(db, resolveZoneBounds(null));
  }

  public static int computeComparison(SQLiteDatabase db, int[] zoneBounds) {
    return computeComparison(
        db,
        zoneBounds[0],
        zoneBounds[1],
        zoneBounds[2],
        zoneBounds[3],
        zoneBounds[4],
        zoneBounds[5],
        zoneBounds[6],
        zoneBounds[7]);
  }

  /**
   * Resolves HR bounds for zones 1–4: user-configured zones when set, otherwise defaults (MHR 186).
   *
   * @return int[8] as z1Min, z1Max, z2Min, z2Max, z3Min, z3Max, z4Min, z4Max
   */
  public static int[] resolveZoneBounds(HRZones hrZones) {
    int[] bounds = new int[8];
    boolean anyConfigured = false;
    if (hrZones != null && hrZones.isConfigured()) {
      for (int zone = 1; zone <= 4; zone++) {
        Pair<Integer, Integer> range = hrZones.getHRValues(zone);
        if (range != null && range.second > range.first) {
          bounds[(zone - 1) * 2] = range.first;
          bounds[(zone - 1) * 2 + 1] = range.second;
          anyConfigured = true;
        }
      }
    }
    if (!anyConfigured) {
      return defaultZoneBounds();
    }
    return bounds;
  }

  private static int[] defaultZoneBounds() {
    return new int[] {
      Constants.DB.HR_ZONES.ZONE1_MIN,
      Constants.DB.HR_ZONES.ZONE1_MAX,
      Constants.DB.HR_ZONES.ZONE2_MIN,
      Constants.DB.HR_ZONES.ZONE2_MAX,
      Constants.DB.HR_ZONES.ZONE3_MIN,
      Constants.DB.HR_ZONES.ZONE3_MAX,
      Constants.DB.HR_ZONES.ZONE4_MIN,
      Constants.DB.HR_ZONES.ZONE4_MAX
    };
  }

  /**
   * @param z1Min inclusive min HR for zone 1 (0 if zone not configured)
   * @param z1Max exclusive max HR for zone 1
   * @param z2Min inclusive min HR for zone 2
   * @param z2Max exclusive max HR for zone 2
   * @param z3Min inclusive min HR for zone 3
   * @param z3Max exclusive max HR for zone 3
   * @param z4Min inclusive min HR for zone 4
   * @param z4Max exclusive max HR for zone 4
   */
  public static int computeComparison(
      SQLiteDatabase db,
      int z1Min,
      int z1Max,
      int z2Min,
      int z2Max,
      int z3Min,
      int z3Max,
      int z4Min,
      int z4Max) {
    Log.i(TAG, "=== Starting monthly comparison computation ===");

    // Clear existing data
    db.delete(Constants.DB.MONTHLY_COMPARISON.TABLE, null, null);

    // Get current month/year
    Calendar cal = Calendar.getInstance();
    int currentYear = cal.get(Calendar.YEAR);
    int currentMonth = cal.get(Calendar.MONTH) + 1; // Calendar.MONTH is 0-based

    String currentMonthYear = String.format(Locale.US, "%04d-%02d", currentYear, currentMonth);

    int[][] zoneBounds = {
      {z1Min, z1Max},
      {z2Min, z2Max},
      {z3Min, z3Max},
      {z4Min, z4Max}
    };

    // Get current month stats from monthly_stats table
    MonthlyStats currentStats = getMonthlyStats(db, currentYear, currentMonth);
    fillCurrentZonePace(db, currentStats, currentYear, currentMonth, zoneBounds);
    Log.d(TAG, "Current month stats: pace=" + currentStats.avgPace + ", km=" + currentStats.totalKm
        + ", bpm=" + currentStats.avgBpm + ", pbs=" + currentStats.pbCount);

    // Compute other months stats (average of all other months)
    MonthlyStats otherStats =
        computeOtherMonthsStats(db, currentYear, currentMonth, zoneBounds);
    Log.d(TAG, "Other months stats: pace=" + otherStats.avgPace + ", km=" + otherStats.totalKm + 
              ", bpm=" + otherStats.avgBpm + ", pbs=" + otherStats.pbCount);

    // Compute best month stats (completed months only)
    BestMonthStats bestStats =
        computeBestMonthStats(db, currentYear, currentMonth, zoneBounds);
    Log.d(TAG, "Best month stats computed");

    // Store result
    android.content.ContentValues values = new android.content.ContentValues();
    values.put(Constants.DB.MONTHLY_COMPARISON.CURRENT_MONTH_YEAR, currentMonthYear);
    values.put(Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE, currentStats.avgPace);
    values.put(Constants.DB.MONTHLY_COMPARISON.CURRENT_TOTAL_KM, currentStats.totalKm);
    values.put(Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_BPM, currentStats.avgBpm);
    values.put(Constants.DB.MONTHLY_COMPARISON.CURRENT_PB_COUNT, currentStats.pbCount);
    values.put(Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_DISTANCE_PER_RUN, currentStats.avgDistancePerRun);
    values.put(Constants.DB.MONTHLY_COMPARISON.CURRENT_TOP25_COUNT, currentStats.top25Count);
    values.put(Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE, otherStats.avgPace);
    values.put(Constants.DB.MONTHLY_COMPARISON.OTHER_TOTAL_KM, otherStats.totalKm);
    values.put(Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_BPM, otherStats.avgBpm);
    values.put(Constants.DB.MONTHLY_COMPARISON.OTHER_PB_COUNT, otherStats.pbCount);
    values.put(Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_DISTANCE_PER_RUN, otherStats.avgDistancePerRun);
    values.put(Constants.DB.MONTHLY_COMPARISON.OTHER_TOP25_COUNT, otherStats.top25Count);
    putZonePaceValues(values, currentStats, otherStats, bestStats);
    // Best month values
    values.put(Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE, bestStats.bestAvgPace);
    values.put(Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_MONTH, bestStats.bestAvgPaceMonth);
    values.put(Constants.DB.MONTHLY_COMPARISON.BEST_TOTAL_KM, bestStats.bestTotalKm);
    values.put(Constants.DB.MONTHLY_COMPARISON.BEST_TOTAL_KM_MONTH, bestStats.bestTotalKmMonth);
    values.put(Constants.DB.MONTHLY_COMPARISON.BEST_AVG_DISTANCE_PER_RUN, bestStats.bestAvgDistancePerRun);
    values.put(Constants.DB.MONTHLY_COMPARISON.BEST_AVG_DISTANCE_PER_RUN_MONTH, bestStats.bestAvgDistancePerRunMonth);
    values.put(Constants.DB.MONTHLY_COMPARISON.BEST_AVG_BPM, bestStats.bestAvgBpm);
    values.put(Constants.DB.MONTHLY_COMPARISON.BEST_AVG_BPM_MONTH, bestStats.bestAvgBpmMonth);
    values.put(Constants.DB.MONTHLY_COMPARISON.BEST_PB_COUNT, bestStats.bestPbCount);
    values.put(Constants.DB.MONTHLY_COMPARISON.BEST_PB_COUNT_MONTH, bestStats.bestPbCountMonth);
    values.put(Constants.DB.MONTHLY_COMPARISON.BEST_TOP25_COUNT, bestStats.bestTop25Count);
    values.put(Constants.DB.MONTHLY_COMPARISON.BEST_TOP25_COUNT_MONTH, bestStats.bestTop25CountMonth);
    values.put(Constants.DB.MONTHLY_COMPARISON.LAST_COMPUTED, System.currentTimeMillis());

    db.insert(Constants.DB.MONTHLY_COMPARISON.TABLE, null, values);

    Log.i(TAG, "Monthly comparison computation completed");
    return 1;
  }

  private static class MonthlyStats {
    double avgPace;
    double totalKm;
    int avgBpm;
    double pbCount; // Changed to double for decimal precision
    double avgDistancePerRun; // in meters
    double top25Count; // Changed to double for decimal precision
    /** Index 1–4: average pace (s/km) in each HR zone. */
    final double[] avgPaceZone = new double[5];
  }

  private static class BestMonthStats {
    double bestAvgPace;
    String bestAvgPaceMonth;
    double bestTotalKm;
    String bestTotalKmMonth;
    double bestAvgDistancePerRun;
    String bestAvgDistancePerRunMonth;
    int bestAvgBpm;
    String bestAvgBpmMonth;
    int bestPbCount;
    String bestPbCountMonth;
    int bestTop25Count;
    String bestTop25CountMonth;
    final double[] bestAvgPaceZone = new double[5];
    final String[] bestAvgPaceZoneMonth = new String[5];
  }

  private static MonthlyStats getMonthlyStats(SQLiteDatabase db, int year, int month) {
    MonthlyStats stats = new MonthlyStats();

    // Get stats from monthly_stats table
    String sql = "SELECT " + Constants.DB.MONTHLY_STATS.TOTAL_DISTANCE + ", " + 
                 Constants.DB.MONTHLY_STATS.AVG_PACE + ", " + Constants.DB.MONTHLY_STATS.AVG_RUN_LENGTH +
                 ", " + Constants.DB.MONTHLY_STATS.RUN_COUNT +
                 " FROM " + Constants.DB.MONTHLY_STATS.TABLE +
                 " WHERE " + Constants.DB.MONTHLY_STATS.YEAR + " = ? AND " + 
                 Constants.DB.MONTHLY_STATS.MONTH + " = ?";

    try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(year), String.valueOf(month)})) {
      if (cursor.moveToFirst()) {
        stats.totalKm = cursor.getDouble(0) / 1000.0; // convert to km
        stats.avgPace = cursor.getDouble(1); // already in seconds per km
        double avgRunLength = cursor.getDouble(2); // AVG_RUN_LENGTH is in meters
        int runCount = cursor.getInt(3);
        
        // Calculate average distance per run
        if (avgRunLength > 0) {
          stats.avgDistancePerRun = avgRunLength; // Already calculated in monthly_stats
        } else if (runCount > 0) {
          stats.avgDistancePerRun = cursor.getDouble(0) / runCount; // total_distance / run_count
        }
      }
    }

    // Get average BPM for this month
    Calendar cal = Calendar.getInstance();
    cal.set(year, month - 1, 1, 0, 0, 0);
    cal.set(Calendar.MILLISECOND, 0);
    long monthStartMillis = cal.getTimeInMillis();
    long monthStartSeconds = monthStartMillis / 1000;

    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
    cal.set(Calendar.HOUR_OF_DAY, 23);
    cal.set(Calendar.MINUTE, 59);
    cal.set(Calendar.SECOND, 59);
    cal.set(Calendar.MILLISECOND, 999);
    long monthEndMillis = cal.getTimeInMillis();
    long monthEndSeconds = monthEndMillis / 1000;

    String hrSql = "SELECT " + Constants.DB.LOCATION.HR +
                   " FROM " + Constants.DB.LOCATION.TABLE +
                   " WHERE " + Constants.DB.LOCATION.ACTIVITY + " IN (" +
                   "SELECT _id FROM " + Constants.DB.ACTIVITY.TABLE +
                   " WHERE " + Constants.DB.ACTIVITY.SPORT + " = ? AND " +
                   Constants.DB.ACTIVITY.DELETED + " = 0 AND " +
                   Constants.DB.ACTIVITY.START_TIME + " >= ? AND " + Constants.DB.ACTIVITY.START_TIME + " <= ?) AND " +
                   Constants.DB.LOCATION.HR + " > 0";

    int hrSum = 0;
    int hrCount = 0;
    try (Cursor hrCursor = db.rawQuery(hrSql, new String[]{
      String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING), String.valueOf(monthStartSeconds), String.valueOf(monthEndSeconds)})) {
      while (hrCursor.moveToNext()) {
        hrSum += hrCursor.getInt(0);
        hrCount++;
      }
    }

    if (hrCount > 0) {
      stats.avgBpm = hrSum / hrCount;
    }

    // Count PBs achieved in this month
    // A PB is counted if a best time was achieved in this month for any distance
    // We need to check if the best time for a distance was achieved in this month
    // Calculate next month start for proper range (using < instead of <=)
    Calendar nextMonthCalForPb = Calendar.getInstance();
    nextMonthCalForPb.set(year, month - 1, 1, 0, 0, 0);
    nextMonthCalForPb.set(Calendar.MILLISECOND, 0);
    nextMonthCalForPb.add(Calendar.MONTH, 1);
    long nextMonthStartSecondsForPb = nextMonthCalForPb.getTimeInMillis() / 1000;
    
    String pbSql = "SELECT COUNT(DISTINCT bt." + Constants.DB.BEST_TIMES.DISTANCE + ")" +
                   " FROM " + Constants.DB.BEST_TIMES.TABLE + " bt" +
                   " JOIN " + Constants.DB.ACTIVITY.TABLE + " a ON a." + Constants.DB.PRIMARY_KEY + " = bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID +
                   " WHERE bt." + Constants.DB.BEST_TIMES.RANK + " = 1" +
                   " AND a." + Constants.DB.ACTIVITY.SPORT + " = ?" +
                   " AND a." + Constants.DB.ACTIVITY.DELETED + " = 0" +
                   " AND a." + Constants.DB.ACTIVITY.START_TIME + " >= ? AND a." + Constants.DB.ACTIVITY.START_TIME + " < ?";
    
    Log.d(TAG, "PB SQL for current month: " + pbSql);
    Log.d(TAG, "PB params: sport=" + Constants.DB.ACTIVITY.SPORT_RUNNING + ", startTime=" + monthStartSeconds + ", endTime=" + nextMonthStartSecondsForPb);
    
    try (Cursor pbCursor = db.rawQuery(pbSql, new String[]{
      String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING),
      String.valueOf(monthStartSeconds),
      String.valueOf(nextMonthStartSecondsForPb)})) {
      if (pbCursor.moveToFirst()) {
        stats.pbCount = pbCursor.getInt(0);
        Log.d(TAG, "Found " + stats.pbCount + " PBs in current month");
        
        // Debug: List all PBs in this month
        String debugPbSql = "SELECT DISTINCT bt." + Constants.DB.BEST_TIMES.DISTANCE + " FROM " + Constants.DB.BEST_TIMES.TABLE + " bt" +
                           " JOIN " + Constants.DB.ACTIVITY.TABLE + " a ON a." + Constants.DB.PRIMARY_KEY + " = bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID +
                           " WHERE bt." + Constants.DB.BEST_TIMES.RANK + " = 1" +
                           " AND a." + Constants.DB.ACTIVITY.SPORT + " = ?" +
                           " AND a." + Constants.DB.ACTIVITY.DELETED + " = 0" +
                           " AND a." + Constants.DB.ACTIVITY.START_TIME + " >= ? AND a." + Constants.DB.ACTIVITY.START_TIME + " < ?";
        try (Cursor debugCursor = db.rawQuery(debugPbSql, new String[]{
          String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING),
          String.valueOf(monthStartSeconds),
          String.valueOf(nextMonthStartSecondsForPb)})) {
          Log.d(TAG, "PB distances in this month:");
          while (debugCursor.moveToNext()) {
            Log.d(TAG, "  - " + debugCursor.getInt(0) + "m");
          }
        }
      }
    }

    // Count runs in top 25 for all distances (activities that appear in best_times with rank <= 25)
    // Use best_times.START_TIME directly - it stores when the record was achieved
    // Calculate next month start for proper range (using < instead of <=)
    Calendar nextMonthCal = Calendar.getInstance();
    nextMonthCal.set(year, month - 1, 1, 0, 0, 0);
    nextMonthCal.set(Calendar.MILLISECOND, 0);
    nextMonthCal.add(Calendar.MONTH, 1);
    long nextMonthStartSeconds = nextMonthCal.getTimeInMillis() / 1000;
    
    // Simple query: count distinct activities in best_times where start_time is in current month
    // and the activity still exists, is running, and not deleted
    String top25Sql = "SELECT COUNT(DISTINCT bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID + ")" +
                      " FROM " + Constants.DB.BEST_TIMES.TABLE + " bt" +
                      " WHERE bt." + Constants.DB.BEST_TIMES.RANK + " <= 25" +
                      " AND bt." + Constants.DB.BEST_TIMES.START_TIME + " >= ? AND bt." + Constants.DB.BEST_TIMES.START_TIME + " < ?" +
                      " AND EXISTS (" +
                      "  SELECT 1 FROM " + Constants.DB.ACTIVITY.TABLE + " a" +
                      "  WHERE a." + Constants.DB.PRIMARY_KEY + " = bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID +
                      "  AND a." + Constants.DB.ACTIVITY.SPORT + " = ?" +
                      "  AND a." + Constants.DB.ACTIVITY.DELETED + " = 0" +
                      ")";
    
    Log.d(TAG, "Top25 SQL for current month: " + top25Sql);
    Log.d(TAG, "Top25 params: startTime=" + monthStartSeconds + ", endTime=" + nextMonthStartSeconds + ", sport=" + Constants.DB.ACTIVITY.SPORT_RUNNING);
    
    try (Cursor top25Cursor = db.rawQuery(top25Sql, new String[]{
      String.valueOf(monthStartSeconds),
      String.valueOf(nextMonthStartSeconds),
      String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING)})) {
      if (top25Cursor.moveToFirst()) {
        stats.top25Count = top25Cursor.getInt(0);
        Log.d(TAG, "Found " + stats.top25Count + " Top25 runs in current month");
        
        // Debug: List all Top25 activities in this month
        String debugTop25Sql = "SELECT DISTINCT bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID + ", bt." + Constants.DB.BEST_TIMES.START_TIME + ", bt." + Constants.DB.BEST_TIMES.RANK + ", bt." + Constants.DB.BEST_TIMES.DISTANCE +
                               ", a." + Constants.DB.ACTIVITY.SPORT + ", a." + Constants.DB.ACTIVITY.DELETED +
                               " FROM " + Constants.DB.BEST_TIMES.TABLE + " bt" +
                               " LEFT JOIN " + Constants.DB.ACTIVITY.TABLE + " a ON a." + Constants.DB.PRIMARY_KEY + " = bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID +
                               " WHERE bt." + Constants.DB.BEST_TIMES.RANK + " <= 25" +
                               " AND bt." + Constants.DB.BEST_TIMES.START_TIME + " >= ? AND bt." + Constants.DB.BEST_TIMES.START_TIME + " < ?";
        try (Cursor debugCursor = db.rawQuery(debugTop25Sql, new String[]{
          String.valueOf(monthStartSeconds),
          String.valueOf(nextMonthStartSeconds)})) {
          Log.e(TAG, "*** Top25 DEBUG: Found " + debugCursor.getCount() + " entries in best_times for month range " + monthStartSeconds + " to " + nextMonthStartSeconds + " ***");
          Log.e(TAG, "*** Current month: " + year + "-" + month + " ***");
          while (debugCursor.moveToNext()) {
            long activityId = debugCursor.getLong(0);
            long startTime = debugCursor.getLong(1);
            int rank = debugCursor.getInt(2);
            int distance = debugCursor.getInt(3);
            int sport = debugCursor.isNull(4) ? -1 : debugCursor.getInt(4);
            int deleted = debugCursor.isNull(5) ? -1 : debugCursor.getInt(5);
            
            // Check if it passes the EXISTS filter
            String checkSql = "SELECT 1 FROM " + Constants.DB.ACTIVITY.TABLE + " a" +
                             " WHERE a." + Constants.DB.PRIMARY_KEY + " = ?" +
                             " AND a." + Constants.DB.ACTIVITY.SPORT + " = ?" +
                             " AND a." + Constants.DB.ACTIVITY.DELETED + " = 0";
            boolean passesFilter = false;
            try (Cursor checkCursor = db.rawQuery(checkSql, new String[]{
              String.valueOf(activityId),
              String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING)})) {
              passesFilter = checkCursor.moveToFirst();
            }
            
            Log.e(TAG, "*** Activity ID: " + activityId + 
                      ", Start time: " + startTime + 
                      ", Rank: " + rank + 
                      ", Distance: " + distance + "m" +
                      ", Sport: " + sport + 
                      ", Deleted: " + deleted +
                      ", Passes filter: " + passesFilter + " ***");
          }
        }
      } else {
        Log.d(TAG, "No Top25 runs found in current month");
        stats.top25Count = 0;
      }
    }

    return stats;
  }

  private static MonthlyStats computeOtherMonthsStats(
      SQLiteDatabase db, int currentYear, int currentMonth, int[][] zoneBounds) {
    MonthlyStats stats = new MonthlyStats();

    // Get all monthly stats except current month
    String sql = "SELECT AVG(" + Constants.DB.MONTHLY_STATS.TOTAL_DISTANCE + ") as avg_distance, " +
                 "AVG(" + Constants.DB.MONTHLY_STATS.AVG_PACE + ") as avg_pace, " +
                 "AVG(" + Constants.DB.MONTHLY_STATS.AVG_RUN_LENGTH + ") as avg_run_length " +
                 "FROM " + Constants.DB.MONTHLY_STATS.TABLE +
                 " WHERE NOT (" + Constants.DB.MONTHLY_STATS.YEAR + " = ? AND " + 
                 Constants.DB.MONTHLY_STATS.MONTH + " = ?)";

    try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(currentYear), String.valueOf(currentMonth)})) {
      if (cursor.moveToFirst()) {
        stats.totalKm = cursor.getDouble(0) / 1000.0; // convert to km
        stats.avgPace = cursor.getDouble(1); // already in seconds per km
        double avgRunLength = cursor.getDouble(2); // AVG_RUN_LENGTH is in meters
        if (avgRunLength > 0) {
          stats.avgDistancePerRun = avgRunLength;
        }
      }
    }

    // Get average BPM for all other months
    Calendar cal = Calendar.getInstance();
    cal.set(currentYear, currentMonth - 1, 1, 0, 0, 0);
    cal.set(Calendar.MILLISECOND, 0);
    long monthStartMillis = cal.getTimeInMillis();
    long monthStartSeconds = monthStartMillis / 1000;

    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
    cal.set(Calendar.HOUR_OF_DAY, 23);
    cal.set(Calendar.MINUTE, 59);
    cal.set(Calendar.SECOND, 59);
    cal.set(Calendar.MILLISECOND, 999);
    long monthEndMillis = cal.getTimeInMillis();
    long monthEndSeconds = monthEndMillis / 1000;

    String hrSql = "SELECT " + Constants.DB.LOCATION.HR +
                   " FROM " + Constants.DB.LOCATION.TABLE +
                   " WHERE " + Constants.DB.LOCATION.ACTIVITY + " IN (" +
                   "SELECT _id FROM " + Constants.DB.ACTIVITY.TABLE +
                   " WHERE " + Constants.DB.ACTIVITY.SPORT + " = ? AND " +
                   Constants.DB.ACTIVITY.DELETED + " = 0 AND " +
                   "(" + Constants.DB.ACTIVITY.START_TIME + " < ? OR " + Constants.DB.ACTIVITY.START_TIME + " > ?)) AND " +
                   Constants.DB.LOCATION.HR + " > 0";

    int hrSum = 0;
    int hrCount = 0;
    try (Cursor hrCursor = db.rawQuery(hrSql, new String[]{
      String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING), String.valueOf(monthStartSeconds), String.valueOf(monthEndSeconds)})) {
      while (hrCursor.moveToNext()) {
        hrSum += hrCursor.getInt(0);
        hrCount++;
      }
    }

    if (hrCount > 0) {
      stats.avgBpm = hrSum / hrCount;
    }

    // Count NEW PBs achieved in other months - calculate average per month
    // Strategy: Get all months with activities, then count PBs per month (including 0 for months with no PBs)
    // First, get all unique year/month combinations from activities (excluding current month)
    String allMonthsSql = "SELECT DISTINCT " +
                          "  strftime('%Y', datetime(" + Constants.DB.ACTIVITY.START_TIME + ", 'unixepoch')) as year, " +
                          "  strftime('%m', datetime(" + Constants.DB.ACTIVITY.START_TIME + ", 'unixepoch')) as month " +
                          "FROM " + Constants.DB.ACTIVITY.TABLE +
                          " WHERE " + Constants.DB.ACTIVITY.SPORT + " = ? AND " +
                          Constants.DB.ACTIVITY.DELETED + " = 0 AND " +
                          "(" + Constants.DB.ACTIVITY.START_TIME + " < ? OR " + Constants.DB.ACTIVITY.START_TIME + " > ?)";
    
    // Get all current PBs (rank=1) with their achievement dates
    String pbSql = "SELECT " +
                   "  strftime('%Y', datetime(bt." + Constants.DB.BEST_TIMES.START_TIME + ", 'unixepoch')) as year, " +
                   "  strftime('%m', datetime(bt." + Constants.DB.BEST_TIMES.START_TIME + ", 'unixepoch')) as month, " +
                   "  bt." + Constants.DB.BEST_TIMES.DISTANCE + " " +
                   "FROM " + Constants.DB.BEST_TIMES.TABLE + " bt " +
                   "WHERE bt." + Constants.DB.BEST_TIMES.RANK + " = 1";
    
    // Count PBs per month
    java.util.Map<String, Integer> pbCountByMonth = new java.util.HashMap<>();
    try (Cursor pbCursor = db.rawQuery(pbSql, null)) {
      while (pbCursor.moveToNext()) {
        String year = pbCursor.getString(0);
        String month = pbCursor.getString(1);
        String monthKey = year + "-" + month;
        pbCountByMonth.put(monthKey, pbCountByMonth.getOrDefault(monthKey, 0) + 1);
      }
    }
    
    // Count total months and total PBs (excluding current month)
    int totalMonths = 0;
    int totalPbs = 0;
    try (Cursor monthCursor = db.rawQuery(allMonthsSql, new String[]{
      String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING),
      String.valueOf(monthStartSeconds),
      String.valueOf(monthEndSeconds)})) {
      while (monthCursor.moveToNext()) {
        String year = monthCursor.getString(0);
        String month = monthCursor.getString(1);
        String monthKey = year + "-" + month;
        totalMonths++;
        totalPbs += pbCountByMonth.getOrDefault(monthKey, 0);
      }
    }
    
    if (totalMonths > 0) {
      stats.pbCount = (double) totalPbs / totalMonths; // Keep decimal precision
    } else {
      stats.pbCount = 0.0;
    }

    // Count runs entering top 25 in other months - calculate average per month
    // Strategy: Get all months with activities, then count top25 runs per month (including 0 for months with no top25)
    // Get all top25 entries with their achievement dates
    String top25Sql = "SELECT " +
                      "  strftime('%Y', datetime(bt." + Constants.DB.BEST_TIMES.START_TIME + ", 'unixepoch')) as year, " +
                      "  strftime('%m', datetime(bt." + Constants.DB.BEST_TIMES.START_TIME + ", 'unixepoch')) as month, " +
                      "  bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID + " " +
                      "FROM " + Constants.DB.BEST_TIMES.TABLE + " bt " +
                      "WHERE bt." + Constants.DB.BEST_TIMES.RANK + " <= 25";
    
    // Count distinct activities per month
    java.util.Map<String, java.util.Set<Long>> top25ByMonth = new java.util.HashMap<>();
    try (Cursor top25Cursor = db.rawQuery(top25Sql, null)) {
      while (top25Cursor.moveToNext()) {
        String year = top25Cursor.getString(0);
        String month = top25Cursor.getString(1);
        long activityId = top25Cursor.getLong(2);
        String monthKey = year + "-" + month;
        top25ByMonth.putIfAbsent(monthKey, new java.util.HashSet<>());
        top25ByMonth.get(monthKey).add(activityId);
      }
    }
    
    // Count total months and total top25 runs (excluding current month)
    int totalMonthsTop25 = 0;
    int totalTop25Runs = 0;
    try (Cursor monthCursor = db.rawQuery(allMonthsSql, new String[]{
      String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING),
      String.valueOf(monthStartSeconds),
      String.valueOf(monthEndSeconds)})) {
      while (monthCursor.moveToNext()) {
        String year = monthCursor.getString(0);
        String month = monthCursor.getString(1);
        String monthKey = year + "-" + month;
        totalMonthsTop25++;
        java.util.Set<Long> activities = top25ByMonth.get(monthKey);
        totalTop25Runs += (activities != null ? activities.size() : 0);
      }
    }
    
    if (totalMonthsTop25 > 0) {
      stats.top25Count = Math.round((float) totalTop25Runs / totalMonthsTop25);
    } else {
      stats.top25Count = 0;
    }

    for (int zone = 1; zone <= 4; zone++) {
      stats.avgPaceZone[zone] =
          computeAvgPaceInZoneForOtherMonths(
              db, currentYear, currentMonth, zoneBounds[zone - 1][0], zoneBounds[zone - 1][1]);
    }

    return stats;
  }

  /**
   * Computes best month statistics (best value for each metric across all months).
   */
  private static BestMonthStats computeBestMonthStats(
      SQLiteDatabase db, int currentYear, int currentMonth, int[][] zoneBounds) {
    BestMonthStats best = new BestMonthStats();
    
    // Get all monthly stats to find best values
    String sql = "SELECT " + Constants.DB.MONTHLY_STATS.YEAR + ", " + 
                 Constants.DB.MONTHLY_STATS.MONTH + ", " +
                 Constants.DB.MONTHLY_STATS.AVG_PACE + ", " +
                 Constants.DB.MONTHLY_STATS.TOTAL_DISTANCE + ", " +
                 Constants.DB.MONTHLY_STATS.AVG_RUN_LENGTH +
                 " FROM " + Constants.DB.MONTHLY_STATS.TABLE +
                 " ORDER BY " + Constants.DB.MONTHLY_STATS.YEAR + ", " + Constants.DB.MONTHLY_STATS.MONTH;
    
    // Initialize best values
    best.bestAvgPace = Double.MAX_VALUE; // Lower is better
    best.bestTotalKm = 0; // Higher is better
    best.bestAvgDistancePerRun = 0; // Higher is better
    best.bestAvgBpm = Integer.MAX_VALUE; // Lower is better (assuming lower HR is better)
    best.bestPbCount = 0; // Higher is better
    best.bestTop25Count = 0; // Higher is better
    for (int zone = 1; zone <= 4; zone++) {
      best.bestAvgPaceZone[zone] = Double.MAX_VALUE;
    }
    
    try (Cursor cursor = db.rawQuery(sql, null)) {
      while (cursor.moveToNext()) {
        int year = cursor.getInt(0);
        int month = cursor.getInt(1);
        if (year == currentYear && month == currentMonth) {
          continue;
        }
        double avgPace = cursor.getDouble(2);
        double totalDistance = cursor.getDouble(3);
        double avgRunLength = cursor.getDouble(4);
        
        // Format month/year as "Sep2024"
        String monthYear = formatMonthYear(year, month);
        
        // Check best avg pace (lower is better)
        if (avgPace > 0 && avgPace < best.bestAvgPace) {
          best.bestAvgPace = avgPace;
          best.bestAvgPaceMonth = monthYear;
        }
        
        // Check best total km (higher is better)
        // Convert totalDistance from meters to km for comparison
        double totalDistanceKm = totalDistance / 1000.0;
        if (totalDistanceKm > best.bestTotalKm) {
          best.bestTotalKm = totalDistanceKm;
          best.bestTotalKmMonth = monthYear;
        }
        
        // Check best avg distance per run (higher is better)
        if (avgRunLength > best.bestAvgDistancePerRun) {
          best.bestAvgDistancePerRun = avgRunLength;
          best.bestAvgDistancePerRunMonth = monthYear;
        }
      }
    }
    
    // Get best BPM, PB count, Top25 count, and zone pace by querying per month
    // Get all unique year/month combinations from activities
    String monthSql = "SELECT DISTINCT " +
                      "  strftime('%Y', datetime(" + Constants.DB.ACTIVITY.START_TIME + ", 'unixepoch')) as year, " +
                      "  strftime('%m', datetime(" + Constants.DB.ACTIVITY.START_TIME + ", 'unixepoch')) as month " +
                      "FROM " + Constants.DB.ACTIVITY.TABLE +
                      " WHERE " + Constants.DB.ACTIVITY.SPORT + " = ? AND " +
                      Constants.DB.ACTIVITY.DELETED + " = 0 " +
                      "ORDER BY year, month";
    
    try (Cursor monthCursor = db.rawQuery(monthSql, new String[]{String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING)})) {
      while (monthCursor.moveToNext()) {
        int year = Integer.parseInt(monthCursor.getString(0));
        int month = Integer.parseInt(monthCursor.getString(1));
        if (year == currentYear && month == currentMonth) {
          continue;
        }
        String monthYear = formatMonthYear(year, month);
        
        // Calculate month start/end
        Calendar monthCal = Calendar.getInstance();
        monthCal.set(year, month - 1, 1, 0, 0, 0);
        monthCal.set(Calendar.MILLISECOND, 0);
        long monthStartSeconds = monthCal.getTimeInMillis() / 1000;
        monthCal.set(Calendar.DAY_OF_MONTH, monthCal.getActualMaximum(Calendar.DAY_OF_MONTH));
        monthCal.set(Calendar.HOUR_OF_DAY, 23);
        monthCal.set(Calendar.MINUTE, 59);
        monthCal.set(Calendar.SECOND, 59);
        monthCal.set(Calendar.MILLISECOND, 999);
        long monthEndSeconds = monthCal.getTimeInMillis() / 1000;
        
        // Get BPM for this month
        String hrSql = "SELECT AVG(" + Constants.DB.LOCATION.HR + ")" +
                       " FROM " + Constants.DB.LOCATION.TABLE +
                       " WHERE " + Constants.DB.LOCATION.ACTIVITY + " IN (" +
                       "SELECT _id FROM " + Constants.DB.ACTIVITY.TABLE +
                       " WHERE " + Constants.DB.ACTIVITY.SPORT + " = ? AND " +
                       Constants.DB.ACTIVITY.DELETED + " = 0 AND " +
                       Constants.DB.ACTIVITY.START_TIME + " >= ? AND " + Constants.DB.ACTIVITY.START_TIME + " <= ?) AND " +
                       Constants.DB.LOCATION.HR + " > 0";
        
        try (Cursor hrCursor = db.rawQuery(hrSql, new String[]{
          String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING),
          String.valueOf(monthStartSeconds),
          String.valueOf(monthEndSeconds)})) {
          if (hrCursor.moveToFirst() && !hrCursor.isNull(0)) {
            int avgBpm = (int) Math.round(hrCursor.getDouble(0));
            if (avgBpm > 0 && avgBpm < best.bestAvgBpm) {
              best.bestAvgBpm = avgBpm;
              best.bestAvgBpmMonth = monthYear;
            }
          }
        }
        
        // Get PB count for this month
        String pbSql = "SELECT COUNT(DISTINCT bt." + Constants.DB.BEST_TIMES.DISTANCE + ")" +
                       " FROM " + Constants.DB.BEST_TIMES.TABLE + " bt" +
                       " WHERE bt." + Constants.DB.BEST_TIMES.RANK + " = 1" +
                       " AND bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID + " IN (" +
                       "SELECT _id FROM " + Constants.DB.ACTIVITY.TABLE +
                       " WHERE " + Constants.DB.ACTIVITY.START_TIME + " >= ? AND " + Constants.DB.ACTIVITY.START_TIME + " <= ?)";
        
        try (Cursor pbCursor = db.rawQuery(pbSql, new String[]{String.valueOf(monthStartSeconds), String.valueOf(monthEndSeconds)})) {
          if (pbCursor.moveToFirst()) {
            int pbCount = pbCursor.getInt(0);
            if (pbCount > best.bestPbCount) {
              best.bestPbCount = pbCount;
              best.bestPbCountMonth = monthYear;
            }
          }
        }
        
        // Get Top25 count for this month
        String top25Sql = "SELECT COUNT(DISTINCT bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID + ")" +
                          " FROM " + Constants.DB.BEST_TIMES.TABLE + " bt" +
                          " WHERE bt." + Constants.DB.BEST_TIMES.RANK + " <= 25" +
                          " AND bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID + " IN (" +
                          "SELECT _id FROM " + Constants.DB.ACTIVITY.TABLE +
                          " WHERE " + Constants.DB.ACTIVITY.START_TIME + " >= ? AND " + Constants.DB.ACTIVITY.START_TIME + " <= ?)";
        
        try (Cursor top25Cursor = db.rawQuery(top25Sql, new String[]{String.valueOf(monthStartSeconds), String.valueOf(monthEndSeconds)})) {
          if (top25Cursor.moveToFirst()) {
            int top25Count = top25Cursor.getInt(0);
            if (top25Count > best.bestTop25Count) {
              best.bestTop25Count = top25Count;
              best.bestTop25CountMonth = monthYear;
            }
          }
        }
        
        for (int zone = 1; zone <= 4; zone++) {
          int hrMin = zoneBounds[zone - 1][0];
          int hrMax = zoneBounds[zone - 1][1];
          double paceZone = computeAvgPaceInZoneForMonth(db, year, month, hrMin, hrMax);
          if (paceZone > 0 && paceZone < best.bestAvgPaceZone[zone]) {
            best.bestAvgPaceZone[zone] = paceZone;
            best.bestAvgPaceZoneMonth[zone] = monthYear;
          }
        }
      }
    }
    
    // Reset to 0 if no valid values found
    if (best.bestAvgPace == Double.MAX_VALUE) best.bestAvgPace = 0;
    if (best.bestAvgBpm == Integer.MAX_VALUE) best.bestAvgBpm = 0;
    for (int zone = 1; zone <= 4; zone++) {
      if (best.bestAvgPaceZone[zone] == Double.MAX_VALUE) {
        best.bestAvgPaceZone[zone] = 0;
      }
    }
    
    return best;
  }

  private static void fillCurrentZonePace(
      SQLiteDatabase db,
      MonthlyStats stats,
      int year,
      int month,
      int[][] zoneBounds) {
    for (int zone = 1; zone <= 4; zone++) {
      stats.avgPaceZone[zone] =
          computeAvgPaceInZoneForMonth(
              db, year, month, zoneBounds[zone - 1][0], zoneBounds[zone - 1][1]);
    }
  }

  private static void putZonePaceValues(
      android.content.ContentValues values,
      MonthlyStats current,
      MonthlyStats other,
      BestMonthStats best) {
    for (int zone = 1; zone <= 4; zone++) {
      int i = zone - 1;
      values.put(CURRENT_AVG_PACE_ZONE_COLUMNS[i], current.avgPaceZone[zone]);
      values.put(OTHER_AVG_PACE_ZONE_COLUMNS[i], other.avgPaceZone[zone]);
      values.put(BEST_AVG_PACE_ZONE_COLUMNS[i], best.bestAvgPaceZone[zone]);
      values.put(BEST_AVG_PACE_ZONE_MONTH_COLUMNS[i], best.bestAvgPaceZoneMonth[zone]);
    }
  }

  private static boolean isZoneConfigured(int hrMin, int hrMax) {
    return hrMax > hrMin && hrMin >= 0;
  }

  private static boolean isMonthEligibleForZonePace(SQLiteDatabase db, int year, int month) {
    String sql =
        "SELECT "
            + Constants.DB.MONTHLY_STATS.TOTAL_DISTANCE
            + ", "
            + Constants.DB.MONTHLY_STATS.RUN_COUNT
            + " FROM "
            + Constants.DB.MONTHLY_STATS.TABLE
            + " WHERE "
            + Constants.DB.MONTHLY_STATS.YEAR
            + " = ? AND "
            + Constants.DB.MONTHLY_STATS.MONTH
            + " = ?";
    try (Cursor cursor =
        db.rawQuery(sql, new String[] {String.valueOf(year), String.valueOf(month)})) {
      if (cursor.moveToFirst()) {
        return cursor.getDouble(0) >= MIN_MONTH_DISTANCE_M
            && cursor.getInt(1) >= MIN_MONTH_RUN_COUNT;
      }
    }
    return isMonthEligibleFromActivities(db, year, month);
  }

  private static boolean isMonthEligibleFromActivities(SQLiteDatabase db, int year, int month) {
    Calendar monthCal = Calendar.getInstance();
    monthCal.set(year, month - 1, 1, 0, 0, 0);
    monthCal.set(Calendar.MILLISECOND, 0);
    long monthStartSeconds = monthCal.getTimeInMillis() / 1000;
    monthCal.add(Calendar.MONTH, 1);
    long nextMonthStartSeconds = monthCal.getTimeInMillis() / 1000;
    String sql =
        "SELECT SUM("
            + Constants.DB.ACTIVITY.DISTANCE
            + "), COUNT(*) FROM "
            + Constants.DB.ACTIVITY.TABLE
            + " WHERE "
            + Constants.DB.ACTIVITY.SPORT
            + " = ? AND "
            + Constants.DB.ACTIVITY.DELETED
            + " = 0 AND "
            + Constants.DB.ACTIVITY.START_TIME
            + " >= ? AND "
            + Constants.DB.ACTIVITY.START_TIME
            + " < ?";
    try (Cursor cursor =
        db.rawQuery(
            sql,
            new String[] {
              String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING),
              String.valueOf(monthStartSeconds),
              String.valueOf(nextMonthStartSeconds)
            })) {
      if (cursor.moveToFirst() && !cursor.isNull(0)) {
        return cursor.getDouble(0) >= MIN_MONTH_DISTANCE_M && cursor.getInt(1) >= MIN_MONTH_RUN_COUNT;
      }
    }
    return false;
  }

  private static double computeAvgPaceInZoneForMonth(
      SQLiteDatabase db, int year, int month, int hrMin, int hrMax) {
    if (!isZoneConfigured(hrMin, hrMax) || !isMonthEligibleForZonePace(db, year, month)) {
      return 0;
    }
    Calendar monthCal = Calendar.getInstance();
    monthCal.set(year, month - 1, 1, 0, 0, 0);
    monthCal.set(Calendar.MILLISECOND, 0);
    long monthStartSeconds = monthCal.getTimeInMillis() / 1000;
    monthCal.add(Calendar.MONTH, 1);
    long nextMonthStartSeconds = monthCal.getTimeInMillis() / 1000;
    return queryAvgPaceInZone(db, monthStartSeconds, nextMonthStartSeconds, hrMin, hrMax);
  }

  private static double computeAvgPaceInZoneForOtherMonths(
      SQLiteDatabase db, int currentYear, int currentMonth, int hrMin, int hrMax) {
    if (!isZoneConfigured(hrMin, hrMax)) {
      return 0;
    }
    String sql =
        "SELECT "
            + Constants.DB.MONTHLY_STATS.YEAR
            + ", "
            + Constants.DB.MONTHLY_STATS.MONTH
            + " FROM "
            + Constants.DB.MONTHLY_STATS.TABLE
            + " WHERE NOT ("
            + Constants.DB.MONTHLY_STATS.YEAR
            + " = ? AND "
            + Constants.DB.MONTHLY_STATS.MONTH
            + " = ?)";
    double sum = 0;
    int count = 0;
    try (Cursor cursor =
        db.rawQuery(
            sql,
            new String[] {String.valueOf(currentYear), String.valueOf(currentMonth)})) {
      while (cursor.moveToNext()) {
        int year = cursor.getInt(0);
        int month = cursor.getInt(1);
        double pace = computeAvgPaceInZoneForMonth(db, year, month, hrMin, hrMax);
        if (pace > 0) {
          sum += pace;
          count++;
        }
      }
    }
    return count > 0 ? sum / count : 0;
  }

  private static String lapPaceSecPerKmExpression() {
    return "(l." + Constants.DB.LAP.TIME + " * 1000.0 / l." + Constants.DB.LAP.DISTANCE + ")";
  }

  /** Lap HR from lap row (location fallback applied in Java when lap HR is missing). */
  private static String effectiveLapHrExpression() {
    return "l." + Constants.DB.LAP.AVG_HR;
  }

  private static double queryAvgPaceInZone(
      SQLiteDatabase db, long monthStartSeconds, long nextMonthStartSeconds, int hrMin, int hrMax) {
    String paceExpr = lapPaceSecPerKmExpression();
    String hrExpr = effectiveLapHrExpression();
    // Numeric filters are inlined: Android SQLite mishandles ? on repeated expressions (hr/pace).
    String sql =
        "SELECT AVG("
            + paceExpr
            + ")"
            + " FROM "
            + Constants.DB.LAP.TABLE
            + " l JOIN "
            + Constants.DB.ACTIVITY.TABLE
            + " a ON a."
            + Constants.DB.PRIMARY_KEY
            + " = l."
            + Constants.DB.LAP.ACTIVITY
            + " WHERE a."
            + Constants.DB.ACTIVITY.SPORT
            + " = ? AND a."
            + Constants.DB.ACTIVITY.DELETED
            + " = 0 AND a."
            + Constants.DB.ACTIVITY.START_TIME
            + " >= ? AND a."
            + Constants.DB.ACTIVITY.START_TIME
            + " < ? AND a."
            + Constants.DB.ACTIVITY.DISTANCE
            + " >= "
            + (long) MIN_ACTIVITY_DISTANCE_M
            + " AND l."
            + Constants.DB.LAP.DISTANCE
            + " >= "
            + (long) MIN_LAP_DISTANCE_M
            + " AND l."
            + Constants.DB.LAP.TIME
            + " > 0 AND "
            + hrExpr
            + " > 0 AND "
            + hrExpr
            + " >= "
            + hrMin
            + " AND "
            + hrExpr
            + " < "
            + hrMax
            + " AND "
            + paceExpr
            + " >= "
            + (long) MIN_PACE_SEC_PER_KM
            + " AND "
            + paceExpr
            + " <= "
            + (long) MAX_PACE_SEC_PER_KM;
    try (Cursor cursor =
        db.rawQuery(
            sql,
            new String[] {
              String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING),
              String.valueOf(monthStartSeconds),
              String.valueOf(nextMonthStartSeconds)
            })) {
      if (cursor.moveToFirst() && !cursor.isNull(0)) {
        return cursor.getDouble(0);
      }
    } catch (Exception e) {
      Log.e(TAG, "Zone pace query failed: " + e.getMessage(), e);
    }
    return 0;
  }

  /**
   * Formats year and month as "Sep24" (2-digit year)
   */
  private static String formatMonthYear(int year, int month) {
    String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                           "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    int shortYear = year % 100; // Get last 2 digits
    return monthNames[month - 1] + shortYear;
  }

  /**
   * Checks if monthly comparison data is stale.
   * 
   * @param db Database instance
   * @return true if data is stale and needs recomputation
   */
  public static boolean isDataStale(SQLiteDatabase db) {
    try {
      String sql =
          "SELECT "
              + Constants.DB.MONTHLY_COMPARISON.LAST_COMPUTED
              + " FROM "
              + Constants.DB.MONTHLY_COMPARISON.TABLE
              + " LIMIT 1";

      try (Cursor cursor = db.rawQuery(sql, null)) {
        if (!cursor.moveToFirst()) {
          Log.i(TAG, "No monthly comparison record found, data is stale");
          return true;
        }
        boolean isStale = ComputationTracker.isStaleByCalendarMonth(cursor.getLong(0));
        Log.i(TAG, "Monthly comparison staleness: stale=" + isStale);
        return isStale;
      }
    } catch (Exception e) {
      Log.e(TAG, "Error checking monthly comparison staleness: " + e.getMessage(), e);
      return true;
    }
  }
}