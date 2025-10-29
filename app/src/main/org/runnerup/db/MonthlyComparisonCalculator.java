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
    values.put(Constants.DB.MONTHLY_COMPARISON.LAST_COMPUTED, System.currentTimeMillis());

    db.insert(Constants.DB.MONTHLY_COMPARISON.TABLE, null, values);

    Log.i(TAG, "Monthly comparison computation completed");
    return 1;
  }

  private static class MonthlyStats {
    double avgPace;
    double totalKm;
    int avgBpm;
    int pbCount;
    double avgDistancePerRun; // in meters
    int top25Count;
    int avgBpm5MinKm; // average BPM for laps run at ~5min/km pace (4:50-5:10)
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
    String pbSql = "SELECT COUNT(DISTINCT bt." + Constants.DB.BEST_TIMES.DISTANCE + ")" +
                   " FROM " + Constants.DB.BEST_TIMES.TABLE + " bt" +
                   " WHERE bt." + Constants.DB.BEST_TIMES.RANK + " = 1" +
                   " AND bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID + " IN (" +
                   "SELECT _id FROM " + Constants.DB.ACTIVITY.TABLE +
                   " WHERE " + Constants.DB.ACTIVITY.START_TIME + " >= ? AND " + Constants.DB.ACTIVITY.START_TIME + " <= ?)";
    
    Log.d(TAG, "PB SQL for current month: " + pbSql);
    Log.d(TAG, "PB params: startTime=" + monthStartSeconds + ", endTime=" + monthEndSeconds);
    
    try (Cursor pbCursor = db.rawQuery(pbSql, new String[]{String.valueOf(monthStartSeconds), String.valueOf(monthEndSeconds)})) {
      if (pbCursor.moveToFirst()) {
        stats.pbCount = pbCursor.getInt(0);
        Log.d(TAG, "Found " + stats.pbCount + " PBs in current month");
        
        // Debug: List all PBs in this month
        String debugPbSql = "SELECT DISTINCT bt." + Constants.DB.BEST_TIMES.DISTANCE + " FROM " + Constants.DB.BEST_TIMES.TABLE + " bt" +
                           " WHERE bt." + Constants.DB.BEST_TIMES.RANK + " = 1" +
                           " AND bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID + " IN (" +
                           "SELECT _id FROM " + Constants.DB.ACTIVITY.TABLE +
                           " WHERE " + Constants.DB.ACTIVITY.START_TIME + " >= ? AND " + Constants.DB.ACTIVITY.START_TIME + " <= ?)";
        try (Cursor debugCursor = db.rawQuery(debugPbSql, new String[]{String.valueOf(monthStartSeconds), String.valueOf(monthEndSeconds)})) {
          Log.d(TAG, "PB distances in this month:");
          while (debugCursor.moveToNext()) {
            Log.d(TAG, "  - " + debugCursor.getInt(0) + "m");
          }
        }
      }
    }

    // Count runs in top 25 for all distances (activities that appear in best_times with rank <= 25)
    String top25Sql = "SELECT COUNT(DISTINCT bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID + ")" +
                      " FROM " + Constants.DB.BEST_TIMES.TABLE + " bt" +
                      " WHERE bt." + Constants.DB.BEST_TIMES.RANK + " <= 25" +
                      " AND bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID + " IN (" +
                      "SELECT _id FROM " + Constants.DB.ACTIVITY.TABLE +
                      " WHERE " + Constants.DB.ACTIVITY.START_TIME + " >= ? AND " + Constants.DB.ACTIVITY.START_TIME + " <= ?)";
    
    try (Cursor top25Cursor = db.rawQuery(top25Sql, new String[]{String.valueOf(monthStartSeconds), String.valueOf(monthEndSeconds)})) {
      if (top25Cursor.moveToFirst()) {
        stats.top25Count = top25Cursor.getInt(0);
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

    // Count PBs achieved in other months
    String pbSql = "SELECT COUNT(DISTINCT bt." + Constants.DB.BEST_TIMES.DISTANCE + ")" +
                   " FROM " + Constants.DB.BEST_TIMES.TABLE + " bt" +
                   " WHERE bt." + Constants.DB.BEST_TIMES.RANK + " = 1" +
                   " AND bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID + " IN (" +
                   "SELECT _id FROM " + Constants.DB.ACTIVITY.TABLE +
                   " WHERE (" + Constants.DB.ACTIVITY.START_TIME + " < ? OR " + Constants.DB.ACTIVITY.START_TIME + " > ?))";
    
    try (Cursor pbCursor = db.rawQuery(pbSql, new String[]{String.valueOf(monthStartSeconds), String.valueOf(monthEndSeconds)})) {
      if (pbCursor.moveToFirst()) {
        stats.pbCount = pbCursor.getInt(0);
      }
    }

    // Count runs in top 25 for all distances (activities that appear in best_times with rank <= 25)
    String top25Sql = "SELECT COUNT(DISTINCT bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID + ")" +
                      " FROM " + Constants.DB.BEST_TIMES.TABLE + " bt" +
                      " WHERE bt." + Constants.DB.BEST_TIMES.RANK + " <= 25" +
                      " AND bt." + Constants.DB.BEST_TIMES.ACTIVITY_ID + " IN (" +
                      "SELECT _id FROM " + Constants.DB.ACTIVITY.TABLE +
                      " WHERE (" + Constants.DB.ACTIVITY.START_TIME + " < ? OR " + Constants.DB.ACTIVITY.START_TIME + " > ?))";
    
    try (Cursor top25Cursor = db.rawQuery(top25Sql, new String[]{String.valueOf(monthStartSeconds), String.valueOf(monthEndSeconds)})) {
      if (top25Cursor.moveToFirst()) {
        stats.top25Count = top25Cursor.getInt(0);
      }
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