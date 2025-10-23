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
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.db.HRZoneCalculator;
import org.runnerup.util.Formatter;
import org.runnerup.util.ViewUtil;
import java.util.ArrayList;
import java.util.List;

public class HRZoneActivity extends AppCompatActivity {

  private static final String TAG = "HRZoneActivity";
  private SQLiteDatabase mDB = null;
  private Formatter formatter = null;
  private List<HRZoneData> zoneData = new ArrayList<>();
  private boolean showTime = true; // true = time, false = pace

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.hr_zone);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(R.string.hr_zones_title);
    }

    // Initialize database and formatter
    mDB = DBHelper.getReadableDatabase(this);
    formatter = new Formatter(this);

    // Apply system bars insets to avoid UI overlap
    ViewUtil.Insets(findViewById(R.id.hr_zone_root), true);

    // Setup toggle
    Switch toggle = findViewById(R.id.toggle_display);
    toggle.setChecked(true); // Start with Time mode (right side)
    toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
      showTime = isChecked;
      updateDisplay();
    });

    // Load and display HR zone data
    loadHRZoneData();
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

  private void loadHRZoneData() {
    // Force recomputation for debugging
    Log.d(TAG, "Forcing HR zone recomputation...");
    HRZoneCalculator.computeHRZones(mDB);

    // Load HR zone data
    zoneData.clear();
    String sql = "SELECT " + Constants.DB.HR_ZONE_STATS.ZONE_NUMBER + ", " +
                 Constants.DB.HR_ZONE_STATS.TIME_IN_ZONE + ", " +
                 Constants.DB.HR_ZONE_STATS.AVG_PACE_IN_ZONE +
                 " FROM " + Constants.DB.HR_ZONE_STATS.TABLE +
                 " ORDER BY " + Constants.DB.HR_ZONE_STATS.ZONE_NUMBER;

    try (Cursor cursor = mDB.rawQuery(sql, null)) {
      while (cursor.moveToNext()) {
        int zone = cursor.getInt(0);
        long timeInZone = cursor.getLong(1);
        double avgPace = cursor.getDouble(2);
        zoneData.add(new HRZoneData(zone, timeInZone, avgPace));
        Log.d(TAG, "Loaded zone " + zone + ": time=" + timeInZone + "ms, pace=" + avgPace);
      }
    }

    if (zoneData.isEmpty()) {
      Log.w(TAG, "No HR zone data found");
    }

    // Normalize values and update display
    normalizeAndDisplay();
  }

  private void normalizeAndDisplay() {
    if (zoneData.isEmpty()) {
      return;
    }

    // Find max value for normalization
    double maxValue = 0;
    for (HRZoneData data : zoneData) {
      double value = showTime ? data.timeInZone : data.avgPace;
      if (value > maxValue) {
        maxValue = value;
      }
    }

    // Update each zone display
    for (HRZoneData data : zoneData) {
      updateZoneDisplay(data, maxValue);
    }
  }

  private void updateZoneDisplay(HRZoneData data, double maxValue) {
    int zoneNumber = data.zoneNumber;
    
    // Get views
    ProgressBar bar = getZoneProgressBar(zoneNumber);
    TextView valueView = getZoneValueView(zoneNumber);

    if (bar == null || valueView == null) {
      return;
    }

    // Calculate normalized value
    double value = showTime ? data.timeInZone : data.avgPace;
    int normalizedProgress = maxValue > 0 ? (int)((value / maxValue) * 100) : 0;

    // Update progress bar
    bar.setProgress(normalizedProgress);

    // Update value text
    if (showTime) {
      // Display time in hours:minutes
      long totalSeconds = (long)(value / 1000);
      long hours = totalSeconds / 3600;
      long minutes = (totalSeconds % 3600) / 60;
      
      if (hours > 0) {
        valueView.setText(String.format("%dh %02dm", hours, minutes));
      } else {
        valueView.setText(String.format("%dm", minutes));
      }
    } else {
      // Display pace
      Log.d(TAG, "Displaying pace for zone " + data.zoneNumber + ": avgPace=" + data.avgPace);
      if (data.avgPace > 0) {
        // avgPace is stored in seconds per km (e.g., 450 = 7:30/km)
        // Convert to seconds per meter first
        double secondsPerMeter = data.avgPace / 1000.0;
        Log.d(TAG, "Formatting pace for zone " + data.zoneNumber + ": avgPace=" + data.avgPace + ", secondsPerMeter=" + secondsPerMeter);
        
        // Convert to meters per second for formatPaceSpeed
        double metersPerSecond = 1.0 / secondsPerMeter;
        Log.d(TAG, "metersPerSecond=" + metersPerSecond);
        
        String paceStr = formatter.formatPaceSpeed(Formatter.Format.TXT_SHORT, metersPerSecond);
        Log.d(TAG, "Formatted pace: " + paceStr);
        valueView.setText(paceStr);
      } else {
        Log.d(TAG, "Pace is 0 for zone " + data.zoneNumber);
        valueView.setText("--");
      }
    }
  }

  private void updateDisplay() {
    normalizeAndDisplay();
  }

  private ProgressBar getZoneProgressBar(int zone) {
    switch (zone) {
      case 0: return findViewById(R.id.zone_0_bar);
      case 1: return findViewById(R.id.zone_1_bar);
      case 2: return findViewById(R.id.zone_2_bar);
      case 3: return findViewById(R.id.zone_3_bar);
      case 4: return findViewById(R.id.zone_4_bar);
      case 5: return findViewById(R.id.zone_5_bar);
      default: return null;
    }
  }

  private TextView getZoneValueView(int zone) {
    switch (zone) {
      case 0: return findViewById(R.id.zone_0_value);
      case 1: return findViewById(R.id.zone_1_value);
      case 2: return findViewById(R.id.zone_2_value);
      case 3: return findViewById(R.id.zone_3_value);
      case 4: return findViewById(R.id.zone_4_value);
      case 5: return findViewById(R.id.zone_5_value);
      default: return null;
    }
  }

  private static class HRZoneData {
    int zoneNumber;
    long timeInZone;
    double avgPace;

    HRZoneData(int zoneNumber, long timeInZone, double avgPace) {
      this.zoneNumber = zoneNumber;
      this.timeInZone = timeInZone;
      this.avgPace = avgPace;
    }
  }
}
