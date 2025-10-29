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
    android.util.Log.e(TAG, "*** onCreate() - about to open database ***");
    mDB = DBHelper.getReadableDatabase(this);
    android.util.Log.e(TAG, "*** Database opened successfully ***");
    formatter = new Formatter(this);

    // Apply system bars insets to avoid UI overlap
    ViewUtil.Insets(findViewById(R.id.monthly_comparison_root), true);

    // Load and display comparison data
    android.util.Log.e(TAG, "*** About to call loadComparisonData() ***");
    loadComparisonData();
    android.util.Log.e(TAG, "*** loadComparisonData() returned ***");
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
    android.util.Log.e(TAG, "=== loadComparisonData() START ===");
    String sql = "SELECT * FROM " + Constants.DB.MONTHLY_COMPARISON.TABLE + " LIMIT 1";
    android.util.Log.e(TAG, "SQL: " + sql);
    
    try (Cursor cursor = mDB.rawQuery(sql, null)) {
      Log.i(TAG, "Cursor row count: " + cursor.getCount());
      Log.i(TAG, "Cursor column names: " + java.util.Arrays.toString(cursor.getColumnNames()));
      
      if (cursor.moveToFirst()) {
        Log.i(TAG, "*** Found monthly comparison data ***");
        // Get current month data
        Double currentAvgPace = cursor.getDouble(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE));
        Double currentTotalKm = cursor.getDouble(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.CURRENT_TOTAL_KM));
        int currentAvgBpm = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_BPM));
        int currentPbCount = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.CURRENT_PB_COUNT));
        Double currentAvgDistancePerRun = cursor.isNull(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_DISTANCE_PER_RUN)) ? null :
                                         cursor.getDouble(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_DISTANCE_PER_RUN));
        int currentTop25Count = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.CURRENT_TOP25_COUNT));

        // Get other months data
        Double otherAvgPace = cursor.getDouble(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE));
        Double otherTotalKm = cursor.getDouble(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.OTHER_TOTAL_KM));
        int otherAvgBpm = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_BPM));
        int otherPbCount = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.OTHER_PB_COUNT));
        Double otherAvgDistancePerRun = cursor.isNull(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_DISTANCE_PER_RUN)) ? null :
                                       cursor.getDouble(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_DISTANCE_PER_RUN));
        int otherTop25Count = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.DB.MONTHLY_COMPARISON.OTHER_TOP25_COUNT));
        // Check if columns exist before trying to read them
        int currentAvgBpm5MinKm = 0;
        int otherAvgBpm5MinKm = 0;
        try {
          int colIndex = cursor.getColumnIndex(Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_BPM_5MIN_KM);
          if (colIndex >= 0 && !cursor.isNull(colIndex)) {
            currentAvgBpm5MinKm = cursor.getInt(colIndex);
            Log.i(TAG, "*** Found CURRENT_AVG_BPM_5MIN_KM column, value: " + currentAvgBpm5MinKm + " ***");
          } else {
            Log.w(TAG, "*** CURRENT_AVG_BPM_5MIN_KM column not found or is null (colIndex=" + colIndex + ") ***");
          }
          
          colIndex = cursor.getColumnIndex(Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_BPM_5MIN_KM);
          if (colIndex >= 0 && !cursor.isNull(colIndex)) {
            otherAvgBpm5MinKm = cursor.getInt(colIndex);
            Log.i(TAG, "*** Found OTHER_AVG_BPM_5MIN_KM column, value: " + otherAvgBpm5MinKm + " ***");
          } else {
            Log.w(TAG, "*** OTHER_AVG_BPM_5MIN_KM column not found or is null (colIndex=" + colIndex + ") ***");
          }
        } catch (Exception e) {
          Log.e(TAG, "*** ERROR reading BPM 5min columns: " + e.getMessage(), e);
        }

        Log.i(TAG, "*** Current month: pace=" + currentAvgPace + ", km=" + currentTotalKm + ", bpm=" + currentAvgBpm + ", pbs=" + currentPbCount + ", bpm5min=" + currentAvgBpm5MinKm + " ***");
        Log.i(TAG, "*** Other months: pace=" + otherAvgPace + ", km=" + otherTotalKm + ", bpm=" + otherAvgBpm + ", pbs=" + otherPbCount + ", bpm5min=" + otherAvgBpm5MinKm + " ***");

        // Always compute BPM@5min/km on the fly (for now, until DB is populated)
        android.util.Log.e(TAG, "*** About to compute current AVG_BPM_5MIN_KM on the fly ***");
        int computed = computeAvgBpm5MinKm(true);
        android.util.Log.e(TAG, "*** Computed current AVG_BPM_5MIN_KM on the fly: " + computed + " ***");
        // Always use computed value, even if 0 (means no matching laps)
        currentAvgBpm5MinKm = computed;
        android.util.Log.e(TAG, "*** Setting currentAvgBpm5MinKm to: " + currentAvgBpm5MinKm + " ***");

        // Display current month data
        displayCurrentMonthData(currentAvgPace, currentTotalKm, currentAvgBpm, currentPbCount, currentAvgDistancePerRun, currentTop25Count, currentAvgBpm5MinKm);
        
        // Always compute BPM@5min/km on the fly (for now, until DB is populated)
        android.util.Log.e(TAG, "*** About to compute other AVG_BPM_5MIN_KM on the fly ***");
        int computedOther = computeAvgBpm5MinKm(false);
        android.util.Log.e(TAG, "*** Computed other AVG_BPM_5MIN_KM on the fly: " + computedOther + " ***");
        // Always use computed value
        otherAvgBpm5MinKm = computedOther;
        android.util.Log.e(TAG, "*** Setting otherAvgBpm5MinKm to: " + otherAvgBpm5MinKm + " ***");

        // Display other months data
        displayOtherMonthsData(otherAvgPace, otherTotalKm, otherAvgBpm, otherPbCount, otherAvgDistancePerRun, otherTop25Count, otherAvgBpm5MinKm);
      } else {
        // No data available
        Log.w(TAG, "No monthly comparison data found");
      }
    }
  }

  private int computeAvgBpm5MinKm(boolean currentMonth) {
    java.util.Calendar cal = java.util.Calendar.getInstance();
    cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
    cal.set(java.util.Calendar.MINUTE, 0);
    cal.set(java.util.Calendar.SECOND, 0);
    cal.set(java.util.Calendar.MILLISECOND, 0);
    // Database stores start_time in SECONDS (not milliseconds)
    long monthStart = cal.getTimeInMillis() / 1000;
    cal.add(java.util.Calendar.MONTH, 1);
    long nextMonthStart = cal.getTimeInMillis() / 1000;
    android.util.Log.e(TAG, "*** Month range: " + monthStart + " to " + nextMonthStart + " (in seconds) ***");

    StringBuilder sb = new StringBuilder();
    sb.append("SELECT CAST(ROUND(AVG(l.")
      .append(Constants.DB.LAP.AVG_HR)
      .append(")) AS INT) FROM ")
      .append(Constants.DB.LAP.TABLE)
      .append(" l JOIN ")
      .append(Constants.DB.ACTIVITY.TABLE)
      .append(" a ON a.")
      .append(Constants.DB.PRIMARY_KEY)
      .append(" = l.")
      .append(Constants.DB.LAP.ACTIVITY)
      .append(" WHERE a.")
      .append(Constants.DB.ACTIVITY.SPORT)
      .append(" = ")
      .append(Constants.DB.ACTIVITY.SPORT_RUNNING)
      .append(" AND a.")
      .append(Constants.DB.ACTIVITY.DELETED)
      .append(" = 0 ")
      .append(" AND l.")
      .append(Constants.DB.LAP.DISTANCE)
      .append(" > 0 AND l.")
      .append(Constants.DB.LAP.TIME)
      .append(" > 0 AND l.")
      .append(Constants.DB.LAP.AVG_HR)
      .append(" > 0 ");

    if (currentMonth) {
      sb.append(" AND a.")
        .append(Constants.DB.ACTIVITY.START_TIME)
        .append(" >= ")
        .append(monthStart)
        .append(" AND a.")
        .append(Constants.DB.ACTIVITY.START_TIME)
        .append(" < ")
        .append(nextMonthStart);
    } else {
      sb.append(" AND (a.")
        .append(Constants.DB.ACTIVITY.START_TIME)
        .append(" < ")
        .append(monthStart)
        .append(" OR a.")
        .append(Constants.DB.ACTIVITY.START_TIME)
        .append(" >= ")
        .append(nextMonthStart)
        .append(")");
    }

    // pace in seconds/km: (time_ms / distance_m) * 1000
    // time is in milliseconds, distance is in meters
    // (time_ms / distance_m) * 1000 = seconds/km
    sb.append(" AND ( (l.")
      .append(Constants.DB.LAP.TIME)
      .append(" * 1.0) / l.")
      .append(Constants.DB.LAP.DISTANCE)
      .append(" * 1000.0 BETWEEN 290.0 AND 310.0 )");

    String sql = sb.toString();
    Log.e(TAG, "On-the-fly BPM@5min/km SQL: " + sql);

    try (Cursor c = mDB.rawQuery(sql, null)) {
      android.util.Log.e(TAG, "*** Query returned " + c.getCount() + " rows ***");
      if (c.moveToFirst()) {
        int val = c.isNull(0) ? 0 : c.getInt(0);
        android.util.Log.e(TAG, "*** On-the-fly BPM@5min/km result: " + val + " (current=" + currentMonth + ", isNull=" + c.isNull(0) + ") ***");
        return val;
      } else {
        android.util.Log.e(TAG, "*** Query returned no rows ***");
      }
    } catch (Exception e) {
      android.util.Log.e(TAG, "*** ERROR in computeAvgBpm5MinKm query: " + e.getMessage(), e);
    }
    return 0;
  }

  private void displayCurrentMonthData(Double avgPace, Double totalKm, int avgBpm, int pbCount, Double avgDistancePerRun, int top25Count, int avgBpm5MinKm) {
    TextView avgPaceView = findViewById(R.id.this_month_avg_pace);
    TextView totalKmView = findViewById(R.id.this_month_total_km);
    TextView avgDistancePerRunView = findViewById(R.id.this_month_avg_distance_per_run);
    TextView avgBpmView = findViewById(R.id.this_month_avg_bpm);
    TextView pbCountView = findViewById(R.id.this_month_pb_count);
    TextView top25CountView = findViewById(R.id.this_month_top25_count);
    TextView avgBpm5MinKmView = findViewById(R.id.this_month_avg_bpm_5min_km);

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

    if (avgDistancePerRun != null && avgDistancePerRun > 0) {
      String distanceStr = formatter.formatDistance(Formatter.Format.TXT_SHORT, avgDistancePerRun.longValue());
      avgDistancePerRunView.setText(distanceStr);
    } else {
      avgDistancePerRunView.setText("--");
    }

    top25CountView.setText(String.valueOf(top25Count));

    android.util.Log.e(TAG, "*** displayCurrentMonthData: avgBpm5MinKm=" + avgBpm5MinKm + " ***");
    if (avgBpm5MinKm > 0) {
      avgBpm5MinKmView.setText(String.valueOf(avgBpm5MinKm));
      android.util.Log.e(TAG, "*** Set avgBpm5MinKmView to: " + avgBpm5MinKm + " ***");
    } else {
      avgBpm5MinKmView.setText("--");
      android.util.Log.e(TAG, "*** Set avgBpm5MinKmView to: -- (value is 0 or negative) ***");
    }
  }

  private void displayOtherMonthsData(Double avgPace, Double totalKm, int avgBpm, int pbCount, Double avgDistancePerRun, int top25Count, int avgBpm5MinKm) {
    TextView avgPaceView = findViewById(R.id.other_months_avg_pace);
    TextView totalKmView = findViewById(R.id.other_months_total_km);
    TextView avgDistancePerRunView = findViewById(R.id.other_months_avg_distance_per_run);
    TextView avgBpmView = findViewById(R.id.other_months_avg_bpm);
    TextView pbCountView = findViewById(R.id.other_months_pb_count);
    TextView top25CountView = findViewById(R.id.other_months_top25_count);
    TextView avgBpm5MinKmView = findViewById(R.id.other_months_avg_bpm_5min_km);

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

    if (avgDistancePerRun != null && avgDistancePerRun > 0) {
      String distanceStr = formatter.formatDistance(Formatter.Format.TXT_SHORT, avgDistancePerRun.longValue());
      avgDistancePerRunView.setText(distanceStr);
    } else {
      avgDistancePerRunView.setText("--");
    }

    top25CountView.setText(String.valueOf(top25Count));

    android.util.Log.e(TAG, "*** displayCurrentMonthData: avgBpm5MinKm=" + avgBpm5MinKm + " ***");
    if (avgBpm5MinKm > 0) {
      avgBpm5MinKmView.setText(String.valueOf(avgBpm5MinKm));
      android.util.Log.e(TAG, "*** Set avgBpm5MinKmView to: " + avgBpm5MinKm + " ***");
    } else {
      avgBpm5MinKmView.setText("--");
      android.util.Log.e(TAG, "*** Set avgBpm5MinKmView to: -- (value is 0 or negative) ***");
    }
  }
}