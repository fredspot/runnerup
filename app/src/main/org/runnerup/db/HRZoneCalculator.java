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
import java.util.ArrayList;
import java.util.List;
import org.runnerup.common.util.Constants;

public class HRZoneCalculator implements Constants {

  private static final String TAG = "HRZoneCalculator";

  /**
   * Computes heart rate zone statistics.
   * 
   * @param db Database instance
   * @return number of records computed
   */
  public static int computeHRZones(SQLiteDatabase db) {
    Log.i(TAG, "Starting HR zone computation...");

    // Clear existing data
    db.delete(Constants.DB.HR_ZONE_STATS.TABLE, null, null);

    // Initialize zone stats
    ZoneStats[] zoneStats = new ZoneStats[6];
    for (int i = 0; i < 6; i++) {
      zoneStats[i] = new ZoneStats(i);
    }

    // Get all location records with HR data
    String sql = "SELECT " + Constants.DB.LOCATION.ACTIVITY + ", " + 
                 Constants.DB.LOCATION.TIME + ", " + Constants.DB.LOCATION.HR + ", " +
                 Constants.DB.LOCATION.ELAPSED + ", " + Constants.DB.LOCATION.DISTANCE +
                 " FROM " + Constants.DB.LOCATION.TABLE +
                 " WHERE " + Constants.DB.LOCATION.HR + " > 0 AND " +
                 Constants.DB.LOCATION.TYPE + " = ?" +
                 " ORDER BY " + Constants.DB.LOCATION.ACTIVITY + ", " + Constants.DB.LOCATION.TIME;

    List<HRReading> readings = new ArrayList<>();
    try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(Constants.DB.LOCATION.TYPE_GPS)})) {
      while (cursor.moveToNext()) {
        long activityId = cursor.getLong(0);
        long time = cursor.getLong(1);
        int hr = cursor.getInt(2);
        double elapsed = cursor.getDouble(3);
        double distance = cursor.getDouble(4);

        readings.add(new HRReading(activityId, time, hr, elapsed, distance));
      }
    }

    Log.d(TAG, "Found " + readings.size() + " HR readings");

    // Process readings and assign to zones
    for (int i = 0; i < readings.size() - 1; i++) {
      HRReading reading = readings.get(i);
      HRReading nextReading = readings.get(i + 1);

      // Only process consecutive readings from the same activity
      if (reading.activityId != nextReading.activityId) {
        continue;
      }

      // Determine zone for this reading
      int zone = getZone(reading.hr);

      // Calculate time interval
      long timeInterval = nextReading.time - reading.time;
      if (timeInterval <= 0) {
        continue;
      }

      // Add time to zone
      zoneStats[zone].addTime(timeInterval);

      // Calculate distance interval
      double distanceInterval = nextReading.distance - reading.distance;
      if (distanceInterval > 0) {
        zoneStats[zone].addDistance(distanceInterval);
      }
    }

    // Store results
    long timestamp = System.currentTimeMillis();
    for (ZoneStats stats : zoneStats) {
      android.content.ContentValues values = new android.content.ContentValues();
      values.put(Constants.DB.HR_ZONE_STATS.ZONE_NUMBER, stats.zoneNumber);
      values.put(Constants.DB.HR_ZONE_STATS.TIME_IN_ZONE, stats.totalTime);
      values.put(Constants.DB.HR_ZONE_STATS.AVG_PACE_IN_ZONE, stats.calculateAvgPace());
      values.put(Constants.DB.HR_ZONE_STATS.LAST_COMPUTED, timestamp);

      db.insert(Constants.DB.HR_ZONE_STATS.TABLE, null, values);

      Log.d(TAG, "Zone " + stats.zoneNumber + ": time=" + stats.totalTime + "ms, pace=" + stats.calculateAvgPace());
    }

    Log.i(TAG, "HR zone computation completed");
    return 6;
  }

  private static int getZone(int hr) {
    // MHR = 186
    // Zone 0: <63% (<117)
    // Zone 1: 63-71% (117-132)
    // Zone 2: 71-78% (132-145)
    // Zone 3: 78-85% (145-158)
    // Zone 4: 85-92% (158-171)
    // Zone 5: >92% (>171)
    
    if (hr < Constants.DB.HR_ZONES.ZONE1_MIN) {
      return 0;
    } else if (hr < Constants.DB.HR_ZONES.ZONE1_MAX) {
      return 1;
    } else if (hr < Constants.DB.HR_ZONES.ZONE2_MAX) {
      return 2;
    } else if (hr < Constants.DB.HR_ZONES.ZONE3_MAX) {
      return 3;
    } else if (hr < Constants.DB.HR_ZONES.ZONE4_MAX) {
      return 4;
    } else {
      return 5;
    }
  }

  private static class HRReading {
    long activityId;
    long time;
    int hr;
    double elapsed;
    double distance;

    HRReading(long activityId, long time, int hr, double elapsed, double distance) {
      this.activityId = activityId;
      this.time = time;
      this.hr = hr;
      this.elapsed = elapsed;
      this.distance = distance;
    }
  }

  private static class ZoneStats {
    int zoneNumber;
    long totalTime;
    double totalDistance;

    ZoneStats(int zoneNumber) {
      this.zoneNumber = zoneNumber;
      this.totalTime = 0;
      this.totalDistance = 0;
    }

    void addTime(long time) {
      this.totalTime += time;
    }

    void addDistance(double distance) {
      this.totalDistance += distance;
    }

    double calculateAvgPace() {
      if (totalDistance > 0 && totalTime > 0) {
        return (totalTime / totalDistance) * 1000.0; // seconds per km
      }
      return 0;
    }
  }

  /**
   * Checks if HR zone data is stale.
   * 
   * @param db Database instance
   * @return true if data is stale and needs recomputation
   */
  public static boolean isDataStale(SQLiteDatabase db) {
    try {
      // Get last computation info
      String sql = "SELECT " + Constants.DB.HR_ZONE_STATS.LAST_COMPUTED + 
                   " FROM " + Constants.DB.HR_ZONE_STATS.TABLE +
                   " LIMIT 1";
      
      try (Cursor cursor = db.rawQuery(sql, null)) {
        if (!cursor.moveToFirst()) {
          // No tracking record exists, data is stale
          Log.i(TAG, "No HR zone stats record found, data is stale");
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
            
            // Check if there are new activities since last computation
            // For simplicity, check if last computation was more than 1 hour ago
            long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000);
            boolean isStale = lastComputed < oneHourAgo;
            
            Log.i(TAG, "HR zone staleness check: last=" + lastComputed + 
                      ", current=" + System.currentTimeMillis() + ", stale=" + isStale);
            return isStale;
          }
        }
      }
      
      return true; // Default to stale if we can't determine
    } catch (Exception e) {
      Log.e(TAG, "Error checking HR zone staleness: " + e.getMessage(), e);
      return true; // Default to stale on error
    }
  }
}

