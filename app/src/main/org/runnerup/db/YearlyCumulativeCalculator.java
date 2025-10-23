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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.runnerup.common.util.Constants;

public class YearlyCumulativeCalculator implements Constants {

  private static final String TAG = "YearlyCumulativeCalc";

  /**
   * Computes yearly cumulative distance statistics.
   * 
   * @param db Database instance
   * @return number of records computed
   */
  public static int computeCumulative(SQLiteDatabase db) {
    Log.i(TAG, "Starting yearly cumulative computation...");

    // Clear existing data
    db.delete(Constants.DB.YEARLY_CUMULATIVE.TABLE, null, null);

    // Get current year
    Calendar cal = Calendar.getInstance();
    int currentYear = cal.get(Calendar.YEAR);
    int lastYear = currentYear - 1;

    // Process both years
    int totalRecords = 0;
    totalRecords += processYear(db, lastYear);
    totalRecords += processYear(db, currentYear);

    Log.i(TAG, "Yearly cumulative computation completed: " + totalRecords + " records");
    return totalRecords;
  }

  private static int processYear(SQLiteDatabase db, int year) {
    Log.d(TAG, "Processing year: " + year);

    // Calculate start and end timestamps for the year
    Calendar cal = Calendar.getInstance();
    cal.set(year, Calendar.JANUARY, 1, 0, 0, 0);
    cal.set(Calendar.MILLISECOND, 0);
    long yearStart = cal.getTimeInMillis();

    cal.set(year, Calendar.DECEMBER, 31, 23, 59, 59);
    cal.set(Calendar.MILLISECOND, 999);
    long yearEnd = cal.getTimeInMillis();

    // Get all activities for this year
    String sql = "SELECT " + Constants.DB.ACTIVITY.START_TIME + ", " + Constants.DB.ACTIVITY.DISTANCE +
                 " FROM " + Constants.DB.ACTIVITY.TABLE +
                 " WHERE " + Constants.DB.ACTIVITY.SPORT + " = ? AND " +
                 Constants.DB.ACTIVITY.DELETED + " = 0 AND " +
                 Constants.DB.ACTIVITY.START_TIME + " >= ? AND " + Constants.DB.ACTIVITY.START_TIME + " <= ?" +
                 " ORDER BY " + Constants.DB.ACTIVITY.START_TIME;

    // Map to store daily totals
    Map<String, Double> dailyTotals = new HashMap<>();

    try (Cursor cursor = db.rawQuery(sql, new String[]{
      String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING), String.valueOf(yearStart), String.valueOf(yearEnd)})) {
      
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
      
      while (cursor.moveToNext()) {
        long startTime = cursor.getLong(0);
        double distance = cursor.getDouble(1);

        // Get date string
        String dateStr = dateFormat.format(new Date(startTime));

        // Add to daily total
        dailyTotals.put(dateStr, dailyTotals.getOrDefault(dateStr, 0.0) + distance);
      }
    }

    Log.d(TAG, "Found " + dailyTotals.size() + " days with activities in " + year);

    // Calculate cumulative for each day
    double cumulative = 0;
    Calendar dayCal = Calendar.getInstance();
    dayCal.set(year, Calendar.JANUARY, 1, 0, 0, 0);
    dayCal.set(Calendar.MILLISECOND, 0);

    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    long timestamp = System.currentTimeMillis();
    int recordsStored = 0;

    // Process each day from Jan 1 to Dec 31
    Calendar endCal = Calendar.getInstance();
    endCal.set(year, Calendar.DECEMBER, 31, 0, 0, 0);
    endCal.set(Calendar.MILLISECOND, 0);

    while (!dayCal.after(endCal)) {
      String dateStr = dateFormat.format(dayCal.getTime());

      // Add today's distance to cumulative
      cumulative += dailyTotals.getOrDefault(dateStr, 0.0);

      // Store cumulative for this day
      android.content.ContentValues values = new android.content.ContentValues();
      values.put(Constants.DB.YEARLY_CUMULATIVE.DATE, dateStr);
      values.put(Constants.DB.YEARLY_CUMULATIVE.CUMULATIVE_KM, cumulative);
      values.put(Constants.DB.YEARLY_CUMULATIVE.YEAR, year);
      values.put(Constants.DB.YEARLY_CUMULATIVE.LAST_COMPUTED, timestamp);

      db.insert(Constants.DB.YEARLY_CUMULATIVE.TABLE, null, values);
      recordsStored++;

      // Move to next day
      dayCal.add(Calendar.DAY_OF_MONTH, 1);
    }

    Log.d(TAG, "Stored " + recordsStored + " cumulative records for year " + year);
    return recordsStored;
  }

  /**
   * Checks if yearly cumulative data is stale.
   * 
   * @param db Database instance
   * @return true if data is stale and needs recomputation
   */
  public static boolean isDataStale(SQLiteDatabase db) {
    try {
      // Get last computation info
      String sql = "SELECT " + Constants.DB.YEARLY_CUMULATIVE.LAST_COMPUTED + 
                   " FROM " + Constants.DB.YEARLY_CUMULATIVE.TABLE +
                   " LIMIT 1";
      
      try (Cursor cursor = db.rawQuery(sql, null)) {
        if (!cursor.moveToFirst()) {
          // No tracking record exists, data is stale
          Log.i(TAG, "No yearly cumulative record found, data is stale");
          return true;
        }
        
        long lastComputed = cursor.getLong(0);
        
        // Get latest activity ID
        String latestSql = "SELECT MAX(" + Constants.DB.PRIMARY_KEY + ") FROM " + Constants.DB.ACTIVITY.TABLE +
                          " WHERE " + Constants.DB.ACTIVITY.SPORT + " = ? AND " + Constants.DB.ACTIVITY.DELETED + " = ?";
        
        try (Cursor latestCursor = db.rawQuery(latestSql, new String[]{
          String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING), "0"})) {
          
          if (latestCursor.moveToFirst()) {
            long latestActivityId = latestCursor.getLong(0);
            
            // Check if last computation was more than 1 hour ago
            long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000);
            boolean isStale = lastComputed < oneHourAgo;
            
            Log.i(TAG, "Yearly cumulative staleness check: last=" + lastComputed + 
                      ", current=" + System.currentTimeMillis() + ", stale=" + isStale);
            return isStale;
          }
        }
      }
      
      return true; // Default to stale if we can't determine
    } catch (Exception e) {
      Log.e(TAG, "Error checking yearly cumulative staleness: " + e.getMessage(), e);
      return true; // Default to stale on error
    }
  }
}

