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
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.ContextCompat;
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

    // Handle window insets for proper spacing
    View rootView = findViewById(R.id.hr_zone_root);
    ViewCompat.setOnApplyWindowInsetsListener(rootView, new OnApplyWindowInsetsListener() {
      @NonNull
      @Override
      public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
        Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        mlp.topMargin = insets.top;
        return WindowInsetsCompat.CONSUMED;
      }
    });

    // Setup toggle
    Switch toggle = findViewById(R.id.toggle_display);
    TextView toggleLabelLeft = findViewById(R.id.toggle_label_left);
    TextView toggleLabelRight = findViewById(R.id.toggle_label_right);
    
    toggle.setChecked(true); // Start with Time mode (right side)
    toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
      showTime = isChecked;
      if (showTime) {
        toggleLabelLeft.setTextColor(ContextCompat.getColor(HRZoneActivity.this, R.color.colorTextSecondary));
        toggleLabelRight.setTextColor(ContextCompat.getColor(HRZoneActivity.this, R.color.colorAccent));
        toggleLabelRight.getPaint().setFakeBoldText(true);
        toggleLabelLeft.getPaint().setFakeBoldText(false);
      } else {
        toggleLabelLeft.setTextColor(ContextCompat.getColor(HRZoneActivity.this, R.color.colorAccent));
        toggleLabelRight.setTextColor(ContextCompat.getColor(HRZoneActivity.this, R.color.colorTextSecondary));
        toggleLabelLeft.getPaint().setFakeBoldText(true);
        toggleLabelRight.getPaint().setFakeBoldText(false);
      }
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

    if (showTime) {
      // For time: find max value for normalization
      double maxValue = 0;
      for (HRZoneData data : zoneData) {
        if (data.timeInZone > maxValue) {
          maxValue = data.timeInZone;
        }
      }
      
      // Update each zone display with time normalization
      for (HRZoneData data : zoneData) {
        updateZoneDisplay(data, maxValue, 0);
      }
    } else {
      // For pace: use fixed range (3:20 = 200 seconds to 10:00 = 600 seconds per km)
      // Fastest pace (min time): 3:20 = 200 seconds per km
      // Slowest pace (max time): 10:00 = 600 seconds per km
      double minPace = 200.0; // 3:20 per km in seconds
      double maxPace = 600.0; // 10:00 per km in seconds
      
      // Update each zone display with fixed pace normalization
      for (HRZoneData data : zoneData) {
        updateZoneDisplay(data, maxPace, minPace);
      }
    }
  }

  private void updateZoneDisplay(HRZoneData data, double maxValue, double minValue) {
    int zoneNumber = data.zoneNumber;
    
    // Get views
    ProgressBar bar = getZoneProgressBar(zoneNumber);
    TextView valueView = getZoneValueView(zoneNumber);

    if (bar == null || valueView == null) {
      return;
    }

    // Calculate normalized value
    double value = showTime ? data.timeInZone : data.avgPace;
    int normalizedProgress;
    
    if (showTime) {
      // For time: higher value = bigger bar (normal behavior)
      normalizedProgress = maxValue > 0 ? (int)((value / maxValue) * 100) : 0;
    } else {
      // For pace: faster pace (lower value) = bigger bar (inverted)
      // Use fixed range: fastest (3:20 = 200s) to slowest (10:00 = 600s)
      // Clamp value to range if outside
      double clampedValue = value;
      if (value < minValue) {
        clampedValue = minValue; // Faster than 3:20, treat as 3:20
      } else if (value > maxValue) {
        clampedValue = maxValue; // Slower than 10:00, treat as 10:00
      }
      
      if (value > 0) {
        // Invert: (max - value) / (max - min) so faster = bigger bar
        normalizedProgress = (int)(((maxValue - clampedValue) / (maxValue - minValue)) * 100);
      } else {
        normalizedProgress = 0; // Invalid value
      }
    }

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
