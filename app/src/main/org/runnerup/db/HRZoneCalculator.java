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

    // Get all laps with HR data
    String sql = "SELECT l." + Constants.DB.PRIMARY_KEY + ", l." + Constants.DB.LAP.ACTIVITY + 
                 ", l." + Constants.DB.LAP.DISTANCE + ", l." + Constants.DB.LAP.TIME +
                 ", COALESCE(hr_data.avg_hr, 0) as avg_hr" +
                 " FROM " + Constants.DB.LAP.TABLE + " l" +
                 " INNER JOIN " + Constants.DB.ACTIVITY.TABLE + " a" +
                 " ON l." + Constants.DB.LAP.ACTIVITY + " = a." + Constants.DB.PRIMARY_KEY +
                 " LEFT JOIN (" +
                 "   SELECT " + Constants.DB.LOCATION.ACTIVITY + ", AVG(" + Constants.DB.LOCATION.HR + ") as avg_hr" +
                 "   FROM " + Constants.DB.LOCATION.TABLE +
                 "   WHERE " + Constants.DB.LOCATION.HR + " > 0" +
                 "   GROUP BY " + Constants.DB.LOCATION.ACTIVITY +
                 ") hr_data ON l." + Constants.DB.LAP.ACTIVITY + " = hr_data." + Constants.DB.LOCATION.ACTIVITY +
                 " WHERE a." + Constants.DB.ACTIVITY.SPORT + " = ? AND " +
                 "a." + Constants.DB.ACTIVITY.DELETED + " = 0" +
                 " ORDER BY l." + Constants.DB.LAP.ACTIVITY + ", l." + Constants.DB.PRIMARY_KEY;

    List<LapData> laps = new ArrayList<>();
    try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING)})) {
      while (cursor.moveToNext()) {
        long lapId = cursor.getLong(0);
        long activityId = cursor.getLong(1);
        double distance = cursor.getDouble(2);
        long time = cursor.getLong(3);
        double avgHR = cursor.getDouble(4);
        
        laps.add(new LapData(lapId, activityId, distance, time, avgHR));
      }
    }

    Log.d(TAG, "Found " + laps.size() + " laps");

    // For each lap, assign to zone based on average HR
    for (LapData lap : laps) {
      if (lap.avgHR > 0) {
        // Determine zone for this lap
        int zone = getZone((int)lap.avgHR);
        
        // Add lap data to zone
        // lap.time is in seconds, convert to milliseconds
        zoneStats[zone].addLap(lap.distance, lap.time * 1000);
        
        Log.d(TAG, "Lap " + lap.lapId + " in zone " + zone + ", avgHR=" + lap.avgHR + ", distance=" + lap.distance + "m, time=" + lap.time + "s");
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

      Log.d(TAG, "Zone " + stats.zoneNumber + ": time=" + stats.totalTime + "ms, distance=" + stats.totalDistance + "m, pace=" + stats.calculateAvgPace());
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

  private static class LapData {
    long lapId;
    long activityId;
    double distance;
    long time;
    double avgHR;

    LapData(long lapId, long activityId, double distance, long time, double avgHR) {
      this.lapId = lapId;
      this.activityId = activityId;
      this.distance = distance;
      this.time = time;
      this.avgHR = avgHR;
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

    void addLap(double distance, long time) {
      this.totalDistance += distance;
      this.totalTime += time;
    }

    double calculateAvgPace() {
      if (totalDistance > 0 && totalTime > 0) {
        // totalTime is in milliseconds, totalDistance is in meters
        // Convert to seconds per km: (ms / m) * 1000 / 1000 = s/km
        return (totalTime / totalDistance); // seconds per km
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
