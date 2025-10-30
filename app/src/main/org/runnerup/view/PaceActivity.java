/*
 * Copyright (C) 2024 RunnerUp
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.runnerup.view;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.widget.TitleSpinner;

public class PaceActivity extends AppCompatActivity {

  private SQLiteDatabase mDB = null;
  private TitleSpinner metricSpinner;
  private PaceChart chart;

  private static final int METRIC_LAPS = 0;
  private static final int METRIC_AVG_HR = 1;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.pace);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(R.string.statistics_pace);
    }

    View rootView = findViewById(R.id.pace_root);
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

    mDB = DBHelper.getReadableDatabase(this);

    metricSpinner = findViewById(R.id.metric_spinner);
    chart = findViewById(R.id.pace_chart);

    // Set entries programmatically
    String[] labels = new String[] { getString(R.string.pace_metric_laps), getString(R.string.pace_metric_avg_hr) };
    metricSpinner.setArrayEntries(labels);
    metricSpinner.setViewSelection(0);
    metricSpinner.setViewValue(0);

    metricSpinner.setViewOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        metricSpinner.setViewSelection(position);
        metricSpinner.setViewValue(position);
        loadData(position == 0 ? METRIC_LAPS : METRIC_AVG_HR);
      }
      @Override public void onNothingSelected(AdapterView<?> parent) { }
    });

    loadData(METRIC_LAPS);
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

  private void loadData(int metric) {
    // Pace bins: 3:30 to 7:30 every 10s inclusive; bin width +-5s
    int startSec = 3 * 60 + 30; // 210
    int endSec = 7 * 60 + 30;   // 450
    int step = 10;         // 10 seconds
    List<Integer> centers = new ArrayList<>();
    for (int s = startSec; s <= endSec; s += step) centers.add(s);

    // year -> binIndex -> stats
    Map<Integer, BinStats[]> yearToBins = new HashMap<>();

    // Query all laps with activity metadata (start time for year) and HR
    String sql = "SELECT a." + Constants.DB.PRIMARY_KEY + ", a." + Constants.DB.ACTIVITY.START_TIME + ", l." + Constants.DB.LAP.TIME + ", l." + Constants.DB.LAP.DISTANCE + ", a." + Constants.DB.ACTIVITY.AVG_HR +
        " FROM " + Constants.DB.LAP.TABLE + " l JOIN " + Constants.DB.ACTIVITY.TABLE + " a ON l." + Constants.DB.LAP.ACTIVITY + " = a." + Constants.DB.PRIMARY_KEY +
        " WHERE a." + Constants.DB.ACTIVITY.SPORT + " = " + Constants.DB.ACTIVITY.SPORT_RUNNING +
        " AND a." + Constants.DB.ACTIVITY.DELETED + " = 0 AND l." + Constants.DB.LAP.DISTANCE + " > 0 AND l." + Constants.DB.LAP.TIME + " > 0";

    try (Cursor c = mDB.rawQuery(sql, null)) {
      while (c.moveToNext()) {
        long startEpoch = c.getLong(1);
        long timeSec = c.getLong(2);
        double distM = c.getDouble(3);
        int hr = c.isNull(4) ? 0 : c.getInt(4);
        double paceSecPerKm = timeSec / (distM / 1000.0);
        if (paceSecPerKm <= 0) continue;

        int paceSec = (int) Math.round(paceSecPerKm);
        // Accept laps roughly within the bin extents (±5s at ends)
        if (paceSec < (startSec - 5) || paceSec > (endSec + 5)) continue;

        int binIndex = (int) Math.round((paceSec - startSec) / (double) step);
        if (binIndex < 0) binIndex = 0;
        if (binIndex >= centers.size()) binIndex = centers.size() - 1;

        Calendar cal = Calendar.getInstance(Locale.US);
        cal.setTimeInMillis(startEpoch * 1000L);
        int year = cal.get(Calendar.YEAR);

        BinStats[] bins = yearToBins.get(year);
        if (bins == null) {
          bins = new BinStats[centers.size()];
          for (int i = 0; i < bins.length; i++) bins[i] = new BinStats();
          yearToBins.put(year, bins);
        }
        bins[binIndex].laps += 1;
        if (hr > 0) {
          bins[binIndex].hrSum += hr;
          bins[binIndex].hrCount += 1;
        }
      }
    }

    // Prepare chart data
    chart.setBins(centers);
    chart.setData(yearToBins);
    chart.setMetric(metric == METRIC_LAPS ? PaceChart.Metric.LAPS : PaceChart.Metric.AVG_HR);
  }

  static class BinStats {
    int laps = 0;
    int hrSum = 0;
    int hrCount = 0;
    float avgHr() { return hrCount > 0 ? (hrSum / (float) hrCount) : 0f; }
  }
}


