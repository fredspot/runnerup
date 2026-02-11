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

package org.runnerup.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.util.Calendar;
import java.util.Locale;
import org.runnerup.common.util.Constants;

public class MonthlyComparisonCalculator implements Constants {

  private static final String TAG = "MonthlyComparisonCalc";

  /**
   * Computes monthly comparison statistics.
   * 
   * @param db Database instance
   * @return number of records computed
   */
  public static int computeComparison(SQLiteDatabase db) {
    Log.i(TAG, "=== Starting monthly comparison computation ===");

    // Clear existing data
    db.delete(Constants.DB.MONTHLY_COMPARISON.TABLE, null, null);

    // Get current month/year
    Calendar cal = Calendar.getInstance();
    int currentYear = cal.get(Calendar.YEAR);
    int currentMonth = cal.get(Calendar.MONTH) + 1; // Calendar.MONTH is 0-based

    String currentMonthYear = String.format(Locale.US, "%04d-%02d", currentYear, currentMonth);

    // Get current month stats from monthly_stats table
    MonthlyStats currentStats = getMonthlyStats(db, currentYear, currentMonth);
    Log.d(TAG, "Current month stats: pace=" + currentStats.avgPace + ", km=" + currentStats.totalKm + 
              ", bpm=" + currentStats.avgBpm + ", pbs=" + currentStats.pbCount);

    // Compute other months stats (average of all other months)
    MonthlyStats otherStats = computeOtherMonthsStats(db, currentYear, currentMonth);
    Log.d(TAG, "Other months stats: pace=" + otherStats.avgPace + ", km=" + otherStats.totalKm + 
              ", bpm=" + otherStats.avgBpm + ", pbs=" + otherStats.pbCount);

    // Compute best month stats (best value across all months)
    BestMonthStats bestStats = computeBestMonthStats(db);
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
    values.put(Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_BPM_5MIN_KM, currentStats.avgBpm5MinKm);
    values.put(Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_BPM_5MIN_KM, otherStats.avgBpm5MinKm);
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
    values.put(Constants.DB.MONTHLY_COMPARISON.BEST_AVG_BPM_5MIN_KM, bestStats.bestAvgBpm5MinKm);
    values.put(Constants.DB.MONTHLY_COMPARISON.BEST_AVG_BPM_5MIN_KM_MONTH, bestStats.bestAvgBpm5MinKmMonth);
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
    int avgBpm5MinKm; // average BPM for laps run at ~5min/km pace (4:50-5:10)
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
    int bestAvgBpm5MinKm;
    String bestAvgBpm5MinKmMonth;
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

    // Calculate average BPM for laps run at 5min/km pace (between 4:50 and 5:10)
    // Pace = time (ms) / distance (m) * 1000 = seconds per km
    // Target: 290-310 seconds per km (4:50-5:10)
    Log.i(TAG, "=== Calculating BPM @ 5min/km for current month ===");
    Log.i(TAG, "Month start: " + monthStartSeconds + ", Month end: " + monthEndSeconds);
    String bpm5MinKmSql = "SELECT AVG(l." + Constants.DB.LAP.AVG_HR + ")" +
                          " FROM " + Constants.DB.LAP.TABLE + " l" +
                          " WHERE l." + Constants.DB.LAP.ACTIVITY + " IN (" +
                          "SELECT _id FROM " + Constants.DB.ACTIVITY.TABLE +
                          " WHERE " + Constants.DB.ACTIVITY.SPORT + " = ? AND " +
                          Constants.DB.ACTIVITY.DELETED + " = 0 AND " +
                          Constants.DB.ACTIVITY.START_TIME + " >= ? AND " + Constants.DB.ACTIVITY.START_TIME + " <= ?)" +
                          " AND l." + Constants.DB.LAP.DISTANCE + " > 0" +
                          " AND l." + Constants.DB.LAP.TIME + " > 0" +
                          " AND l." + Constants.DB.LAP.AVG_HR + " > 0" +
                          " AND ((l." + Constants.DB.LAP.TIME + " / 1000.0 / l." + Constants.DB.LAP.DISTANCE + " * 1000.0) >= 290.0)" +
                          " AND ((l." + Constants.DB.LAP.TIME + " / 1000.0 / l." + Constants.DB.LAP.DISTANCE + " * 1000.0) <= 310.0)";
    
    try (Cursor bpm5MinKmCursor = db.rawQuery(bpm5MinKmSql, new String[]{
      String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING),
      String.valueOf(monthStartSeconds),
      String.valueOf(monthEndSeconds)})) {
      Log.i(TAG, "Query executed, cursor row count: " + bpm5MinKmCursor.getCount());
      if (bpm5MinKmCursor.moveToFirst() && !bpm5MinKmCursor.isNull(0)) {
        double avgBpm = bpm5MinKmCursor.getDouble(0);
        stats.avgBpm5MinKm = (int) Math.round(avgBpm);
        Log.i(TAG, "*** SUCCESS: Current month BPM @ 5min/km: " + stats.avgBpm5MinKm + " (from " + avgBpm + ") ***");
      } else {
        Log.i(TAG, "*** No laps found at 5min/km pace for current month (cursor empty or null) ***");
      }
    } catch (Exception e) {
      Log.e(TAG, "*** ERROR calculating BPM @ 5min/km for current month: " + e.getMessage(), e);
    }

    return stats;
  }

  private static MonthlyStats computeOtherMonthsStats(SQLiteDatabase db, int currentYear, int currentMonth) {
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

    // Calculate average BPM for laps run at 5min/km pace (between 4:50 and 5:10)
    // Pace = time (ms) / distance (m) * 1000 = seconds per km
    // Target: 290-310 seconds per km (4:50-5:10)
    String bpm5MinKmSql = "SELECT AVG(l." + Constants.DB.LAP.AVG_HR + ")" +
                          " FROM " + Constants.DB.LAP.TABLE + " l" +
                          " WHERE l." + Constants.DB.LAP.ACTIVITY + " IN (" +
                          "SELECT _id FROM " + Constants.DB.ACTIVITY.TABLE +
                          " WHERE " + Constants.DB.ACTIVITY.SPORT + " = ? AND " +
                          Constants.DB.ACTIVITY.DELETED + " = 0 AND " +
                          "(" + Constants.DB.ACTIVITY.START_TIME + " < ? OR " + Constants.DB.ACTIVITY.START_TIME + " > ?))" +
                          " AND l." + Constants.DB.LAP.DISTANCE + " > 0" +
                          " AND l." + Constants.DB.LAP.TIME + " > 0" +
                          " AND l." + Constants.DB.LAP.AVG_HR + " > 0" +
                          " AND ((l." + Constants.DB.LAP.TIME + " / 1000.0 / l." + Constants.DB.LAP.DISTANCE + " * 1000.0) >= 290.0)" +
                          " AND ((l." + Constants.DB.LAP.TIME + " / 1000.0 / l." + Constants.DB.LAP.DISTANCE + " * 1000.0) <= 310.0)";
    
    try (Cursor bpm5MinKmCursor = db.rawQuery(bpm5MinKmSql, new String[]{
      String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING),
      String.valueOf(monthStartSeconds),
      String.valueOf(monthEndSeconds)})) {
      if (bpm5MinKmCursor.moveToFirst() && !bpm5MinKmCursor.isNull(0)) {
        double avgBpm = bpm5MinKmCursor.getDouble(0);
        stats.avgBpm5MinKm = (int) Math.round(avgBpm);
        Log.i(TAG, "*** SUCCESS: Other months BPM @ 5min/km: " + stats.avgBpm5MinKm + " (from " + avgBpm + ") ***");
      } else {
        Log.i(TAG, "*** No laps found at 5min/km pace for other months (cursor empty or null) ***");
      }
    } catch (Exception e) {
      Log.e(TAG, "*** ERROR calculating BPM @ 5min/km for other months: " + e.getMessage(), e);
    }

    return stats;
  }

  /**
   * Computes best month statistics (best value for each metric across all months).
   */
  private static BestMonthStats computeBestMonthStats(SQLiteDatabase db) {
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
    best.bestAvgBpm5MinKm = Integer.MAX_VALUE; // Lower is better
    
    try (Cursor cursor = db.rawQuery(sql, null)) {
      while (cursor.moveToNext()) {
        int year = cursor.getInt(0);
        int month = cursor.getInt(1);
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
    
    // Get best BPM, PB count, Top25 count, and BPM@5min/km by querying per month
    // We need to compute these per month since they're not in monthly_stats
    Calendar cal = Calendar.getInstance();
    int currentYear = cal.get(Calendar.YEAR);
    int currentMonth = cal.get(Calendar.MONTH) + 1;
    
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
        
        // Get BPM@5min/km for this month
        // Use same query structure as "All Others" calculation for consistency
        // TIME is in milliseconds, so: (time_ms / distance_m) * 1000 = seconds/km
        String bpm5MinKmSql = "SELECT CAST(ROUND(AVG(l." + Constants.DB.LAP.AVG_HR + ")) AS INT)" +
                              " FROM " + Constants.DB.LAP.TABLE + " l" +
                              " JOIN " + Constants.DB.ACTIVITY.TABLE + " a ON a." + Constants.DB.PRIMARY_KEY + " = l." + Constants.DB.LAP.ACTIVITY +
                              " WHERE a." + Constants.DB.ACTIVITY.SPORT + " = ? AND " +
                              " a." + Constants.DB.ACTIVITY.DELETED + " = 0 AND " +
                              " a." + Constants.DB.ACTIVITY.START_TIME + " >= ? AND " +
                              " a." + Constants.DB.ACTIVITY.START_TIME + " < ? AND " +
                              " l." + Constants.DB.LAP.DISTANCE + " > 0 AND " +
                              " l." + Constants.DB.LAP.TIME + " > 0 AND " +
                              " l." + Constants.DB.LAP.AVG_HR + " > 0 AND " +
                              " ((l." + Constants.DB.LAP.TIME + " * 1.0) / l." + Constants.DB.LAP.DISTANCE + " * 1000.0 BETWEEN 290.0 AND 310.0)";
        
        // Calculate next month start for proper range (using < instead of <=)
        Calendar nextMonthCal = (Calendar) monthCal.clone();
        nextMonthCal.add(Calendar.MONTH, 1);
        long nextMonthStartSeconds = nextMonthCal.getTimeInMillis() / 1000;
        
        try (Cursor bpm5MinKmCursor = db.rawQuery(bpm5MinKmSql, new String[]{
          String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING),
          String.valueOf(monthStartSeconds),
          String.valueOf(nextMonthStartSeconds)})) {
          if (bpm5MinKmCursor.moveToFirst() && !bpm5MinKmCursor.isNull(0)) {
            int avgBpm = bpm5MinKmCursor.getInt(0);
            Log.d(TAG, "BPM@5min/km for " + monthYear + ": " + avgBpm + " (current best: " + best.bestAvgBpm5MinKm + ")");
            if (avgBpm > 0 && avgBpm < best.bestAvgBpm5MinKm) {
              best.bestAvgBpm5MinKm = avgBpm;
              best.bestAvgBpm5MinKmMonth = monthYear;
              Log.d(TAG, "New best BPM@5min/km: " + avgBpm + " in " + monthYear);
            }
          } else {
            Log.d(TAG, "No BPM@5min/km data for " + monthYear);
          }
        } catch (Exception e) {
          Log.e(TAG, "Error calculating BPM@5min/km for " + monthYear + ": " + e.getMessage(), e);
        }
      }
    }
    
    // Reset to 0 if no valid values found
    if (best.bestAvgPace == Double.MAX_VALUE) best.bestAvgPace = 0;
    if (best.bestAvgBpm == Integer.MAX_VALUE) best.bestAvgBpm = 0;
    if (best.bestAvgBpm5MinKm == Integer.MAX_VALUE) best.bestAvgBpm5MinKm = 0;
    
    return best;
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
      // Get last computation info
      String sql = "SELECT " + Constants.DB.MONTHLY_COMPARISON.LAST_COMPUTED + 
                   " FROM " + Constants.DB.MONTHLY_COMPARISON.TABLE +
                   " LIMIT 1";
      
      try (Cursor cursor = db.rawQuery(sql, null)) {
        if (!cursor.moveToFirst()) {
          // No tracking record exists, data is stale
          Log.i(TAG, "No monthly comparison record found, data is stale");
          return true;
        }
        
        long lastComputed = cursor.getLong(0);
        
        // Check if we're in a new month since last computation
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(lastComputed);
        int lastComputedMonth = cal.get(Calendar.MONTH);
        int lastComputedYear = cal.get(Calendar.YEAR);
        
        cal.setTimeInMillis(System.currentTimeMillis());
        int currentMonth = cal.get(Calendar.MONTH);
        int currentYear = cal.get(Calendar.YEAR);
        
        boolean isStale = (currentMonth != lastComputedMonth || currentYear != lastComputedYear);
        Log.i(TAG, "Monthly comparison staleness check: last=" + lastComputedMonth + "/" + lastComputedYear + 
                  ", current=" + currentMonth + "/" + currentYear + ", stale=" + isStale);
        return isStale;
      }
    } catch (Exception e) {
      Log.e(TAG, "Error checking monthly comparison staleness: " + e.getMessage(), e);
      return true; // Default to stale on error
    }
  }
}