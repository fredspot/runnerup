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
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.db.MonthlyComparisonCalculator;
import org.runnerup.util.Formatter;
import org.runnerup.util.ViewUtil;

public class MonthlyComparisonActivity extends AppCompatActivity {

  private static final String TAG = "MonthlyComparisonAct";
  private SQLiteDatabase mDB = null;
  private Formatter formatter = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.monthly_comparison);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(R.string.monthly_comparison_title);
    }

    // Initialize database and formatter
    mDB = DBHelper.getReadableDatabase(this);
    formatter = new Formatter(this);

    // Apply system bars insets to avoid UI overlap
    ViewUtil.Insets(findViewById(R.id.monthly_comparison_root), true);

    // Load and display comparison data
    loadComparisonData();
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

  private void loadComparisonData() {
    // Load comparison data (already computed at startup)
    String sql = "SELECT * FROM " + Constants.DB.MONTHLY_COMPARISON.TABLE + " LIMIT 1";
    
    try (Cursor cursor = mDB.rawQuery(sql, null)) {
      if (cursor.moveToFirst()) {
        Log.d(TAG, "Found monthly comparison data");
        // Get current month data
        Double currentAvgPace = cursor.getDouble(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE));
        Double currentTotalKm = cursor.getDouble(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.CURRENT_TOTAL_KM));
        int currentAvgBpm = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_BPM));
        int currentPbCount = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.CURRENT_PB_COUNT));

        // Get other months data
        Double otherAvgPace = cursor.getDouble(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE));
        Double otherTotalKm = cursor.getDouble(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.OTHER_TOTAL_KM));
        int otherAvgBpm = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_BPM));
        int otherPbCount = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.OTHER_PB_COUNT));

        Log.d(TAG, "Current month: pace=" + currentAvgPace + ", km=" + currentTotalKm + ", bpm=" + currentAvgBpm + ", pbs=" + currentPbCount);
        Log.d(TAG, "Other months: pace=" + otherAvgPace + ", km=" + otherTotalKm + ", bpm=" + otherAvgBpm + ", pbs=" + otherPbCount);

        // Display current month data
        displayCurrentMonthData(currentAvgPace, currentTotalKm, currentAvgBpm, currentPbCount);
        
        // Display other months data
        displayOtherMonthsData(otherAvgPace, otherTotalKm, otherAvgBpm, otherPbCount);
      } else {
        // No data available
        Log.w(TAG, "No monthly comparison data found");
      }
    }
  }

  private void displayCurrentMonthData(Double avgPace, Double totalKm, int avgBpm, int pbCount) {
    TextView avgPaceView = findViewById(R.id.this_month_avg_pace);
    TextView totalKmView = findViewById(R.id.this_month_total_km);
    TextView avgBpmView = findViewById(R.id.this_month_avg_bpm);
    TextView pbCountView = findViewById(R.id.this_month_pb_count);

    if (avgPace != null && avgPace > 0) {
      String paceStr = formatter.formatPace(Formatter.Format.TXT_SHORT, avgPace / 1000.0);
      avgPaceView.setText(paceStr);
    } else {
      avgPaceView.setText("--");
    }

    if (totalKm != null && totalKm > 0) {
      // Convert km to meters for formatter
      String kmStr = formatter.formatDistance(Formatter.Format.TXT_SHORT, (long)(totalKm * 1000));
      totalKmView.setText(kmStr);
    } else {
      totalKmView.setText("--");
    }

    if (avgBpm > 0) {
      avgBpmView.setText(String.valueOf(avgBpm));
    } else {
      avgBpmView.setText("--");
    }

    pbCountView.setText(String.valueOf(pbCount));
  }

  private void displayOtherMonthsData(Double avgPace, Double totalKm, int avgBpm, int pbCount) {
    TextView avgPaceView = findViewById(R.id.other_months_avg_pace);
    TextView totalKmView = findViewById(R.id.other_months_total_km);
    TextView avgBpmView = findViewById(R.id.other_months_avg_bpm);
    TextView pbCountView = findViewById(R.id.other_months_pb_count);

    if (avgPace != null && avgPace > 0) {
      String paceStr = formatter.formatPace(Formatter.Format.TXT_SHORT, avgPace / 1000.0);
      avgPaceView.setText(paceStr);
    } else {
      avgPaceView.setText("--");
    }

    if (totalKm != null && totalKm > 0) {
      // Convert km to meters for formatter
      String kmStr = formatter.formatDistance(Formatter.Format.TXT_SHORT, (long)(totalKm * 1000));
      totalKmView.setText(kmStr);
    } else {
      totalKmView.setText("--");
    }

    if (avgBpm > 0) {
      avgBpmView.setText(String.valueOf(avgBpm));
    } else {
      avgBpmView.setText("--");
    }

    pbCountView.setText(String.valueOf(pbCount));
  }
}