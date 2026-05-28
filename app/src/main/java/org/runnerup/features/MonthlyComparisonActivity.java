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

package org.runnerup.features;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.util.Locale;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.core.util.Formatter;
import org.runnerup.core.util.HRZones;
import org.runnerup.core.util.ViewUtil;
import org.runnerup.data.DBHelper;
import org.runnerup.analytics.MonthlyComparisonCalculator;

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

    mDB = DBHelper.getReadableDatabase(this);
    formatter = new Formatter(this);

    ViewUtil.Insets(findViewById(R.id.monthly_comparison_root), true);
    bindZoneMetricLabels(new HRZones(this));
    ensureComparisonDataLoaded();
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

  private void bindZoneMetricLabels(HRZones hrZones) {
    int[] bounds = MonthlyComparisonCalculator.resolveZoneBounds(hrZones);
    TextView aerobicLabel = findViewById(R.id.metric_avg_pace_zone_3);
    TextView easyLabel = findViewById(R.id.metric_easy_pace);
    if (bounds[5] > bounds[4]) {
      aerobicLabel.setText(
          getString(
              R.string.monthly_comparison_zone_aerobic_metric, 3, bounds[4], bounds[5] - 1));
    } else {
      aerobicLabel.setText(getString(R.string.monthly_comparison_zone_pace_metric_unconfigured, 3));
    }
    if (bounds[2] > bounds[0] && bounds[3] > bounds[2]) {
      easyLabel.setText(
          getString(R.string.monthly_comparison_zone_easy_metric, bounds[0], bounds[3] - 1));
    } else {
      easyLabel.setText(getString(R.string.monthly_comparison_zone_pace_metric_unconfigured, 1));
    }
  }

  private void ensureComparisonDataLoaded() {
    if (needsZonePaceRecompute()) {
      recomputeComparisonAsync();
    } else {
      loadComparisonData();
    }
  }

  private boolean needsZonePaceRecompute() {
    String sql = "SELECT * FROM " + Constants.DB.MONTHLY_COMPARISON.TABLE + " LIMIT 1";
    try (Cursor cursor = mDB.rawQuery(sql, null)) {
      if (cursor.getCount() == 0) {
        return true;
      }
      if (cursor.getColumnIndex(Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE_ZONE_1) < 0) {
        return true;
      }
      if (cursor.moveToFirst()) {
        double[] zonePace = readZonePaceCurrent(cursor);
        return zonePace[1] <= 0
            && zonePace[2] <= 0
            && zonePace[3] <= 0
            && zonePace[4] <= 0;
      }
    }
    return true;
  }

  private void recomputeComparisonAsync() {
    org.runnerup.core.util.BgTasks.run(
        () -> {
          SQLiteDatabase db = DBHelper.getWritableDatabase(MonthlyComparisonActivity.this);
          try {
            MonthlyComparisonCalculator.computeComparison(
                db,
                MonthlyComparisonCalculator.resolveZoneBounds(
                    new HRZones(MonthlyComparisonActivity.this)));
          } finally {
            DBHelper.closeDB(db);
          }
        },
        () -> {
          DBHelper.closeDB(mDB);
          mDB = DBHelper.getReadableDatabase(MonthlyComparisonActivity.this);
          loadComparisonData();
        });
  }

  private void loadComparisonData() {
    String sql = "SELECT * FROM " + Constants.DB.MONTHLY_COMPARISON.TABLE + " LIMIT 1";

    try (Cursor cursor = mDB.rawQuery(sql, null)) {
      if (cursor.getCount() == 0) {
        recomputeComparisonAsync();
        return;
      }

      if (cursor.moveToFirst()) {
        try {
          Double currentAvgPace = getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE);
          Double currentTotalKm = getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_TOTAL_KM);
          int currentAvgBpm = getIntColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_BPM);
          int currentPbCount = getIntColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_PB_COUNT);
          Double currentAvgDistancePerRun =
              getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_DISTANCE_PER_RUN);
          int currentTop25Count =
              getIntColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_TOP25_COUNT);

          Double otherAvgPace = getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE);
          Double otherTotalKm = getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_TOTAL_KM);
          int otherAvgBpm = getIntColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_BPM);
          Double otherPbCount = getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_PB_COUNT);
          Double otherAvgDistancePerRun =
              getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_DISTANCE_PER_RUN);
          Double otherTop25Count =
              getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_TOP25_COUNT);

          double[] currentZonePace = readZonePaceCurrent(cursor);
          double[] otherZonePace = readZonePaceOther(cursor);
          double[] bestZonePace = readZonePaceBest(cursor);
          String[] bestZoneMonth = readZonePaceBestMonth(cursor);

          displayCurrentMonthData(
              currentAvgPace,
              currentTotalKm,
              currentAvgBpm,
              currentPbCount,
              currentAvgDistancePerRun,
              currentTop25Count);

          displayOtherMonthsData(
              otherAvgPace,
              otherTotalKm,
              otherAvgBpm,
              otherPbCount,
              otherAvgDistancePerRun,
              otherTop25Count);

          displayBestMonthData(
              getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE),
              getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_MONTH),
              getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_TOTAL_KM),
              getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_TOTAL_KM_MONTH),
              getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_DISTANCE_PER_RUN),
              getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_DISTANCE_PER_RUN_MONTH),
              getIntColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_BPM),
              getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_BPM_MONTH),
              getIntColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_PB_COUNT),
              getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_PB_COUNT_MONTH),
              getIntColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_TOP25_COUNT),
              getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_TOP25_COUNT_MONTH));

          displayZonePaceSection(currentZonePace, otherZonePace, bestZonePace, bestZoneMonth);
        } catch (Exception e) {
          android.util.Log.e(TAG, "Error reading monthly comparison data: " + e.getMessage(), e);
        }
      }
    } catch (Exception e) {
      android.util.Log.e(TAG, "Error loading comparison data: " + e.getMessage(), e);
    }
  }

  private static double[] readZonePaceCurrent(Cursor cursor) {
    double[] pace = new double[5];
    pace[1] = doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE_ZONE_1));
    pace[2] = doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE_ZONE_2));
    pace[3] = doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE_ZONE_3));
    pace[4] = doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE_ZONE_4));
    return pace;
  }

  private static double[] readZonePaceOther(Cursor cursor) {
    double[] pace = new double[5];
    pace[1] = doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE_ZONE_1));
    pace[2] = doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE_ZONE_2));
    pace[3] = doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE_ZONE_3));
    pace[4] = doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE_ZONE_4));
    return pace;
  }

  private static double[] readZonePaceBest(Cursor cursor) {
    double[] pace = new double[5];
    pace[1] = doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_1));
    pace[2] = doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_2));
    pace[3] = doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_3));
    pace[4] = doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_4));
    return pace;
  }

  private static double doubleValue(Double value) {
    return value != null ? value : 0;
  }

  private static String[] readZonePaceBestMonth(Cursor cursor) {
    return new String[] {
      null,
      getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_1_MONTH),
      getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_2_MONTH),
      getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_3_MONTH),
      getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_4_MONTH)
    };
  }

  private static Double getDoubleColumn(Cursor cursor, String column) {
    int colIndex = cursor.getColumnIndex(column);
    if (colIndex < 0 || cursor.isNull(colIndex)) {
      return null;
    }
    return cursor.getDouble(colIndex);
  }

  private static int getIntColumn(Cursor cursor, String column) {
    int colIndex = cursor.getColumnIndex(column);
    if (colIndex < 0 || cursor.isNull(colIndex)) {
      return 0;
    }
    return cursor.getInt(colIndex);
  }

  private static String getStringColumn(Cursor cursor, String column) {
    int colIndex = cursor.getColumnIndex(column);
    if (colIndex < 0 || cursor.isNull(colIndex)) {
      return null;
    }
    return cursor.getString(colIndex);
  }

  private String formatPaceSecondsPerKm(Double paceSecondsPerKm) {
    if (paceSecondsPerKm == null || paceSecondsPerKm <= 0) {
      return "--";
    }
    return formatter.formatPaceFromSecPerKm(Formatter.Format.TXT_SHORT, paceSecondsPerKm);
  }

  /** Delta in seconds/km: negative means faster than baseline. */
  private String formatPaceDeltaSecondsPerKm(Double currentSecPerKm, Double baselineSecPerKm) {
    if (currentSecPerKm == null
        || currentSecPerKm <= 0
        || baselineSecPerKm == null
        || baselineSecPerKm <= 0) {
      return "--";
    }
    double deltaSec = currentSecPerKm - baselineSecPerKm;
    if (Math.abs(deltaSec) < 1.0) {
      return getString(R.string.monthly_comparison_zone_pace_same);
    }
    long sec = Math.round(Math.abs(deltaSec));
    long minutes = sec / 60;
    long seconds = sec % 60;
    String sign = deltaSec > 0 ? "+" : "−";
    return String.format(Locale.US, "%s%d:%02d", sign, minutes, seconds);
  }

  private void applyPaceDeltaColor(TextView view, Double current, Double baseline) {
    if (current == null || baseline == null || current <= 0 || baseline <= 0) {
      view.setTextColor(getColor(R.color.colorTextSecondary));
      return;
    }
    double delta = current - baseline;
    if (Math.abs(delta) < 1.0) {
      view.setTextColor(getColor(R.color.colorTextSecondary));
    } else if (delta < 0) {
      view.setTextColor(getColor(R.color.colorAccent));
    } else {
      view.setTextColor(getColor(R.color.colorTextSecondary));
    }
  }

  private void displayCurrentMonthData(
      Double avgPace,
      Double totalKm,
      int avgBpm,
      int pbCount,
      Double avgDistancePerRun,
      int top25Count) {
    TextView avgPaceView = findViewById(R.id.this_month_avg_pace);
    TextView totalKmView = findViewById(R.id.this_month_total_km);
    TextView avgDistancePerRunView = findViewById(R.id.this_month_avg_distance_per_run);
    TextView avgBpmView = findViewById(R.id.this_month_avg_bpm);
    TextView pbCountView = findViewById(R.id.this_month_pb_count);
    TextView top25CountView = findViewById(R.id.this_month_top25_count);

    avgPaceView.setText(formatPaceSecondsPerKm(avgPace));

    if (totalKm != null && totalKm > 0) {
      totalKmView.setText(String.format(Locale.US, "%.1fk", totalKm));
    } else {
      totalKmView.setText("--");
    }

    avgBpmView.setText(avgBpm > 0 ? String.valueOf(avgBpm) : "--");
    pbCountView.setText(String.valueOf(pbCount));

    if (avgDistancePerRun != null && avgDistancePerRun > 0) {
      double avgDistanceKm = avgDistancePerRun / 1000.0;
      avgDistancePerRunView.setText(String.format(Locale.US, "%.1fk", avgDistanceKm));
    } else {
      avgDistancePerRunView.setText("--");
    }

    top25CountView.setText(String.valueOf(top25Count));
  }

  private void displayOtherMonthsData(
      Double avgPace,
      Double totalKm,
      int avgBpm,
      Double pbCount,
      Double avgDistancePerRun,
      Double top25Count) {
    TextView avgPaceView = findViewById(R.id.other_months_avg_pace);
    TextView totalKmView = findViewById(R.id.other_months_total_km);
    TextView avgDistancePerRunView = findViewById(R.id.other_months_avg_distance_per_run);
    TextView avgBpmView = findViewById(R.id.other_months_avg_bpm);
    TextView pbCountView = findViewById(R.id.other_months_pb_count);
    TextView top25CountView = findViewById(R.id.other_months_top25_count);

    avgPaceView.setText(formatPaceSecondsPerKm(avgPace));

    if (totalKm != null && totalKm > 0) {
      totalKmView.setText(String.format(Locale.US, "%.1fk", totalKm));
    } else {
      totalKmView.setText("--");
    }

    avgBpmView.setText(avgBpm > 0 ? String.valueOf(avgBpm) : "--");

    if (pbCount != null) {
      pbCountView.setText(String.format(Locale.US, "%.2f", pbCount));
    } else {
      pbCountView.setText("--");
    }

    if (avgDistancePerRun != null && avgDistancePerRun > 0) {
      double avgDistanceKm = avgDistancePerRun / 1000.0;
      avgDistancePerRunView.setText(String.format(Locale.US, "%.1fk", avgDistanceKm));
    } else {
      avgDistancePerRunView.setText("--");
    }

    if (top25Count != null) {
      top25CountView.setText(String.format(Locale.US, "%.2f", top25Count));
    } else {
      top25CountView.setText("--");
    }
  }

  private void displayZonePaceSection(
      double[] currentZonePace,
      double[] otherZonePace,
      double[] bestZonePace,
      String[] bestZoneMonth) {
    bindZoneRow(
        R.id.this_month_avg_pace_zone_3,
        R.id.other_months_avg_pace_zone_3,
        R.id.best_month_avg_pace_zone_3,
        R.id.best_month_avg_pace_zone_3_label,
        currentZonePace[3],
        otherZonePace[3],
        bestZonePace[3],
        bestZoneMonth[3]);

    bindZoneRow(
        R.id.this_month_easy_pace,
        R.id.other_months_easy_pace,
        R.id.best_month_easy_pace,
        R.id.best_month_easy_pace_label,
        compositeEasyPace(currentZonePace),
        compositeEasyPace(otherZonePace),
        compositeEasyPace(bestZonePace),
        bestEasyMonthLabel(bestZonePace, bestZoneMonth));
  }

  private void bindZoneRow(
      int thisMonthId,
      int otherMonthId,
      int bestValueId,
      int bestLabelId,
      double currentPace,
      double otherPace,
      double bestPace,
      String bestMonth) {
    TextView thisMonthView = findViewById(thisMonthId);
    TextView otherMonthView = findViewById(otherMonthId);
    TextView bestValueView = findViewById(bestValueId);
    TextView bestLabelView = findViewById(bestLabelId);

    Double current = currentPace > 0 ? currentPace : null;
    Double other = otherPace > 0 ? otherPace : null;
    Double best = bestPace > 0 ? bestPace : null;

    thisMonthView.setText(formatPaceSecondsPerKm(current));
    otherMonthView.setText(formatPaceDeltaSecondsPerKm(current, other));
    applyPaceDeltaColor(otherMonthView, current, other);

    if (best != null) {
      bestValueView.setText(formatPaceSecondsPerKm(best));
      setBestMonthLabel(bestLabelView, bestMonth);
    } else {
      bestValueView.setText("--");
      setBestMonthLabel(bestLabelView, "");
    }
  }

  private static double compositeEasyPace(double[] zonePace) {
    int count = 0;
    double sum = 0;
    if (zonePace[1] > 0) {
      sum += zonePace[1];
      count++;
    }
    if (zonePace[2] > 0) {
      sum += zonePace[2];
      count++;
    }
    return count > 0 ? sum / count : 0;
  }

  private static String bestEasyMonthLabel(double[] bestZonePace, String[] bestZoneMonth) {
    int bestZone = 0;
    double bestPace = Double.MAX_VALUE;
    if (bestZonePace[1] > 0 && bestZonePace[1] < bestPace) {
      bestPace = bestZonePace[1];
      bestZone = 1;
    }
    if (bestZonePace[2] > 0 && bestZonePace[2] < bestPace) {
      bestPace = bestZonePace[2];
      bestZone = 2;
    }
    return bestZone > 0 && bestZoneMonth[bestZone] != null ? bestZoneMonth[bestZone] : "";
  }

  private void setBestMonthLabel(TextView labelView, String month) {
    labelView.setText(month != null ? month : "");
  }

  private void displayBestMonthData(
      Double bestAvgPace,
      String bestAvgPaceMonth,
      Double bestTotalKm,
      String bestTotalKmMonth,
      Double bestAvgDistancePerRun,
      String bestAvgDistancePerRunMonth,
      int bestAvgBpm,
      String bestAvgBpmMonth,
      int bestPbCount,
      String bestPbCountMonth,
      int bestTop25Count,
      String bestTop25CountMonth) {
    TextView bestAvgPaceView = findViewById(R.id.best_month_avg_pace);
    TextView bestAvgPaceLabelView = findViewById(R.id.best_month_avg_pace_label);
    if (bestAvgPace != null && bestAvgPace > 0) {
      bestAvgPaceView.setText(formatPaceSecondsPerKm(bestAvgPace));
      setBestMonthLabel(bestAvgPaceLabelView, bestAvgPaceMonth);
    } else {
      bestAvgPaceView.setText("--");
      setBestMonthLabel(bestAvgPaceLabelView, "");
    }

    TextView bestTotalKmView = findViewById(R.id.best_month_total_km);
    TextView bestTotalKmLabelView = findViewById(R.id.best_month_total_km_label);
    if (bestTotalKm != null && bestTotalKm > 0) {
      bestTotalKmView.setText(String.format(Locale.US, "%.1fk", bestTotalKm));
      setBestMonthLabel(bestTotalKmLabelView, bestTotalKmMonth);
    } else {
      bestTotalKmView.setText("--");
      setBestMonthLabel(bestTotalKmLabelView, "");
    }

    TextView bestAvgDistancePerRunView = findViewById(R.id.best_month_avg_distance_per_run);
    TextView bestAvgDistancePerRunLabelView = findViewById(R.id.best_month_avg_distance_per_run_label);
    if (bestAvgDistancePerRun != null && bestAvgDistancePerRun > 0) {
      double avgDistanceKm = bestAvgDistancePerRun / 1000.0;
      bestAvgDistancePerRunView.setText(String.format(Locale.US, "%.1fk", avgDistanceKm));
      setBestMonthLabel(bestAvgDistancePerRunLabelView, bestAvgDistancePerRunMonth);
    } else {
      bestAvgDistancePerRunView.setText("--");
      setBestMonthLabel(bestAvgDistancePerRunLabelView, "");
    }

    TextView bestAvgBpmView = findViewById(R.id.best_month_avg_bpm);
    TextView bestAvgBpmLabelView = findViewById(R.id.best_month_avg_bpm_label);
    if (bestAvgBpm > 0) {
      bestAvgBpmView.setText(String.valueOf(bestAvgBpm));
      setBestMonthLabel(bestAvgBpmLabelView, bestAvgBpmMonth);
    } else {
      bestAvgBpmView.setText("--");
      setBestMonthLabel(bestAvgBpmLabelView, "");
    }

    TextView bestPbCountView = findViewById(R.id.best_month_pb_count);
    TextView bestPbCountLabelView = findViewById(R.id.best_month_pb_count_label);
    bestPbCountView.setText(String.valueOf(bestPbCount));
    setBestMonthLabel(bestPbCountLabelView, bestPbCountMonth);

    TextView bestTop25CountView = findViewById(R.id.best_month_top25_count);
    TextView bestTop25CountLabelView = findViewById(R.id.best_month_top25_count_label);
    bestTop25CountView.setText(String.valueOf(bestTop25Count));
    setBestMonthLabel(bestTop25CountLabelView, bestTop25CountMonth);
  }
}
