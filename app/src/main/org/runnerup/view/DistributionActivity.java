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

package org.runnerup.view;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.NonNull;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.util.Formatter;
import org.runnerup.widget.TitleSpinner;
import java.util.ArrayList;
import java.util.List;

public class DistributionActivity extends AppCompatActivity {
  
  private static final String TAG = "DistributionActivity";
  private SQLiteDatabase mDB = null;
  private Formatter formatter = null;
  
  // Target distances in meters (same as BestTimesCalculator)
  private static final int[] TARGET_DISTANCES = {1000, 5000, 10000, 15000, 20000, 21097, 30000, 40000, 42195};
  private TitleSpinner distanceSpinner;
  private DistributionChart chart;
  private TextView statMin, stat25th, statMean, stat75th, statMax;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.distribution);
    
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(R.string.statistics_distribution);
    }
    
    mDB = DBHelper.getReadableDatabase(this);
    formatter = new Formatter(this);
    
    // Handle window insets
    View rootView = findViewById(R.id.distribution_root);
    ViewCompat.setOnApplyWindowInsetsListener(rootView, new OnApplyWindowInsetsListener() {
      @NonNull
      @Override
      public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
        Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
        android.view.ViewGroup.MarginLayoutParams mlp = (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
        mlp.topMargin = insets.top;
        return WindowInsetsCompat.CONSUMED;
      }
    });
    
    // Initialize views
    distanceSpinner = findViewById(R.id.distance_spinner);
    chart = findViewById(R.id.distribution_chart);
    
    // Setup distance spinner - default to 1km (index 0)
    // Note: TitleSpinner will auto-populate from XML entries attribute
    // We just need to set the initial selection
    distanceSpinner.setViewSelection(0); // Default to 1km
    distanceSpinner.setViewValue(0);
    
    // Setup spinner listener
    distanceSpinner.setViewOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int selectedDistance = TARGET_DISTANCES[position];
        // Ensure spinner displays the selected distance label
        distanceSpinner.setViewSelection(position);
        distanceSpinner.setViewValue(position);
        loadDistribution(selectedDistance);
      }
      
      @Override
      public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing
      }
    });
    
    // Load initial data for 1km
    loadDistribution(1000);
  }
  
  @Override
  protected void onDestroy() {
    super.onDestroy();
    DBHelper.closeDB(mDB);
  }
  
  @Override
  public boolean onSupportNavigateUp() {
    onBackPressed();
    return true;
  }
  
  private void loadDistribution(int targetDistance) {
    Log.d(TAG, "Loading distribution for distance: " + targetDistance + "m");
    
    // Get all running activities
    List<Long> activityIds = getRunningActivities();
    
    // Collect all lap times matching the distance
    List<Long> lapTimes = new ArrayList<>();
    
    for (Long activityId : activityIds) {
      List<LapTimeData> laps = getLapsForActivity(activityId);
      
      // Find laps that match the target distance
      if (targetDistance == 1000) {
        // For 1km: find single laps close to 1km
        for (LapTimeData lap : laps) {
          double distanceRatio = lap.distanceM / targetDistance;
          if (distanceRatio >= 0.95 && distanceRatio <= 1.05) {
            double pacePerKm = lap.timeSeconds / (lap.distanceM / 1000.0);
            if (pacePerKm >= 120.0 && pacePerKm <= 720.0) {
              lapTimes.add(lap.timeSeconds);
            }
          }
        }
      } else {
        // For other distances: find consecutive laps that sum to the target
        int expectedLapCount = targetDistance / 1000; // Approximate
        for (int startIdx = 0; startIdx <= laps.size() - expectedLapCount; startIdx++) {
          long totalTime = 0;
          double totalDistance = 0;
          
          for (int i = 0; i < expectedLapCount && startIdx + i < laps.size(); i++) {
            LapTimeData lap = laps.get(startIdx + i);
            totalTime += lap.timeSeconds;
            totalDistance += lap.distanceM;
          }
          
          double distanceRatio = totalDistance / targetDistance;
          if (distanceRatio >= 0.95 && distanceRatio <= 1.05) {
            double pacePerKm = totalTime / (totalDistance / 1000.0);
            if (pacePerKm >= 120.0 && pacePerKm <= 720.0) {
              lapTimes.add(totalTime);
            }
          }
        }
      }
    }
    
    Log.d(TAG, "Found " + lapTimes.size() + " matching lap times");
    
    // Update chart
    chart.setLapTimes(lapTimes);
  }
  
  private String formatTime(long seconds) {
    if (seconds == 0) return "--";
    long hours = seconds / 3600;
    long minutes = (seconds % 3600) / 60;
    long secs = seconds % 60;
    
    if (hours > 0) {
      return String.format("%d:%02d:%02d", hours, minutes, secs);
    } else {
      return String.format("%d:%02d", minutes, secs);
    }
  }
  
  private List<Long> getRunningActivities() {
    List<Long> activityIds = new ArrayList<>();
    String[] columns = {Constants.DB.PRIMARY_KEY};
    String selection = Constants.DB.ACTIVITY.SPORT + " = ? AND " + Constants.DB.ACTIVITY.DELETED + " = ?";
    String[] selectionArgs = {String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING), "0"};
    
    try (Cursor cursor = mDB.query(Constants.DB.ACTIVITY.TABLE, columns, selection, selectionArgs, null, null, null)) {
      while (cursor.moveToNext()) {
        activityIds.add(cursor.getLong(0));
      }
    }
    
    return activityIds;
  }
  
  private List<LapTimeData> getLapsForActivity(Long activityId) {
    List<LapTimeData> laps = new ArrayList<>();
    String[] columns = {Constants.DB.LAP.TIME, Constants.DB.LAP.DISTANCE};
    String selection = Constants.DB.LAP.ACTIVITY + " = ?";
    String[] selectionArgs = {String.valueOf(activityId)};
    
    try (Cursor cursor = mDB.query(Constants.DB.LAP.TABLE, columns, selection, selectionArgs, null, null, Constants.DB.LAP.LAP + " ASC")) {
      while (cursor.moveToNext()) {
        long timeSeconds = cursor.getLong(0);
        double distanceM = cursor.getDouble(1);
        if (timeSeconds > 0 && distanceM > 0) {
          laps.add(new LapTimeData(timeSeconds, distanceM));
        }
      }
    }
    
    return laps;
  }
  
  private static class LapTimeData {
    final long timeSeconds;
    final double distanceM;
    
    LapTimeData(long timeSeconds, double distanceM) {
      this.timeSeconds = timeSeconds;
      this.distanceM = distanceM;
    }
  }
}

