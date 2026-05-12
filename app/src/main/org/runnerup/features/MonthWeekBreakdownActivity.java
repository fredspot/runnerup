/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.core.util.Formatter;
import org.runnerup.core.util.WeekCalendarUtil;
import org.runnerup.data.DBHelper;

/**
 * Lists Monday–Sunday weeks overlapping a calendar month with running aggregates; opens History
 * for the full month from any row or the toolbar action.
 */
public class MonthWeekBreakdownActivity extends AppCompatActivity {

  public static final String EXTRA_YEAR = "YEAR";
  public static final String EXTRA_MONTH = "MONTH";

  private int year;
  /** 1–12 */
  private int month;
  /** 0–11 for History filter */
  private int monthZeroBased;

  private SQLiteDatabase mDB;
  private Formatter formatter;
  private final List<WeekRow> weekRows = new ArrayList<>();
  private WeekListAdapter adapter;

  private static final class WeekRow {
    final long weekStartMillis;
    final String title;
    final int runCount;
    final double totalDistanceM;
    final double avgPaceSecPerKm;
    final double avgRunLengthM;

    WeekRow(
        long weekStartMillis,
        String title,
        int runCount,
        double totalDistanceM,
        double avgPaceSecPerKm,
        double avgRunLengthM) {
      this.weekStartMillis = weekStartMillis;
      this.title = title;
      this.runCount = runCount;
      this.totalDistanceM = totalDistanceM;
      this.avgPaceSecPerKm = avgPaceSecPerKm;
      this.avgRunLengthM = avgRunLengthM;
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.month_week_breakdown);

    year = getIntent().getIntExtra(EXTRA_YEAR, Calendar.getInstance().get(Calendar.YEAR));
    month = getIntent().getIntExtra(EXTRA_MONTH, 1);
    if (month < 1) {
      month = 1;
    }
    if (month > 12) {
      month = 12;
    }
    monthZeroBased = month - 1;

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(formatToolbarTitle());
    }

    mDB = DBHelper.getReadableDatabase(this);
    formatter = new Formatter(this);

    ListView listView = findViewById(R.id.month_week_list);
    TextView emptyView = findViewById(R.id.month_week_empty);
    listView.setEmptyView(emptyView);
    adapter = new WeekListAdapter();
    listView.setAdapter(adapter);
    listView.setOnItemClickListener(
        (parent, view, position, id) -> openMonthHistory());

    View rootView = findViewById(R.id.month_week_breakdown_layout);
    ViewCompat.setOnApplyWindowInsetsListener(
        rootView,
        new OnApplyWindowInsetsListener() {
          @NonNull
          @Override
          public WindowInsetsCompat onApplyWindowInsets(
              @NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.topMargin = insets.top;
            return WindowInsetsCompat.CONSUMED;
          }
        });

    loadWeekRows();
  }

  private String formatToolbarTitle() {
    Calendar c = Calendar.getInstance();
    c.set(Calendar.YEAR, year);
    c.set(Calendar.MONTH, monthZeroBased);
    c.set(Calendar.DAY_OF_MONTH, 1);
    return new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(c.getTime());
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.month_week_breakdown, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.menu_view_month_history) {
      openMonthHistory();
      return true;
    }
    return super.onOptionsItemSelected(item);
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

  private void loadWeekRows() {
    weekRows.clear();

    Calendar monthStart = Calendar.getInstance();
    monthStart.set(Calendar.YEAR, year);
    monthStart.set(Calendar.MONTH, monthZeroBased);
    monthStart.set(Calendar.DAY_OF_MONTH, 1);
    monthStart.set(Calendar.HOUR_OF_DAY, 0);
    monthStart.set(Calendar.MINUTE, 0);
    monthStart.set(Calendar.SECOND, 0);
    monthStart.set(Calendar.MILLISECOND, 0);
    long monthStartSec = monthStart.getTimeInMillis() / 1000;

    Calendar monthEnd = (Calendar) monthStart.clone();
    monthEnd.add(Calendar.MONTH, 1);
    long monthEndSecExclusive = monthEnd.getTimeInMillis() / 1000;

    List<Long> weekStarts =
        WeekCalendarUtil.buildMondayWeekStartsInRange(monthStartSec, monthEndSecExclusive);

    Map<Long, Agg> byWeek = new LinkedHashMap<>();
    for (Long ws : weekStarts) {
      byWeek.put(ws, new Agg());
    }

    // Load activities for the full Monday–Sunday span of every listed week — not only the calendar
    // month. Otherwise a week that straddles two months gets different totals when opened from
    // each month (runs in the other month were excluded by START_TIME month bounds).
    long queryStartSec = 0;
    long queryEndSecExclusive = 0;
    if (!weekStarts.isEmpty()) {
      queryStartSec = weekStarts.get(0) / 1000L;
      queryEndSecExclusive =
          weekStarts.get(weekStarts.size() - 1) / 1000L + 7L * 24 * 3600;
    }

    String sql =
        "SELECT "
            + Constants.DB.ACTIVITY.START_TIME
            + ", "
            + Constants.DB.ACTIVITY.DISTANCE
            + ", "
            + Constants.DB.ACTIVITY.TIME
            + " FROM "
            + Constants.DB.ACTIVITY.TABLE
            + " WHERE "
            + Constants.DB.ACTIVITY.SPORT
            + " = ? AND "
            + Constants.DB.ACTIVITY.DELETED
            + " = 0 AND "
            + Constants.DB.ACTIVITY.START_TIME
            + " >= ? AND "
            + Constants.DB.ACTIVITY.START_TIME
            + " < ?";

    String[] args = {
      String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING),
      String.valueOf(queryStartSec),
      String.valueOf(queryEndSecExclusive)
    };

    Locale locale = Locale.getDefault();
    SimpleDateFormat dayFmt = new SimpleDateFormat("EEE MMM d", locale);

    try (Cursor cursor = mDB.rawQuery(sql, args)) {
      while (cursor.moveToNext()) {
        long startTimeSec = cursor.getLong(0);
        double distanceM = cursor.getDouble(1);
        long timeSec = cursor.getLong(2);

        Calendar act = Calendar.getInstance();
        act.setTimeInMillis(startTimeSec * 1000L);
        long weekStart = WeekCalendarUtil.mondayWeekStartMillis(act);
        Agg agg = byWeek.get(weekStart);
        if (agg != null) {
          agg.addRun(distanceM, timeSec);
        }
      }
    }

    for (Long ws : weekStarts) {
      Agg agg = byWeek.get(ws);
      if (agg == null || agg.runCount == 0) {
        continue;
      }
      Calendar mon = Calendar.getInstance();
      mon.setTimeInMillis(ws);
      Calendar sun = (Calendar) mon.clone();
      sun.add(Calendar.DAY_OF_MONTH, 6);
      String title =
          dayFmt.format(mon.getTime()) + " \u2013 " + dayFmt.format(sun.getTime());

      double avgPaceSecPerKm = 0;
      if (agg.totalDistanceM > 0) {
        avgPaceSecPerKm = agg.totalTimeSec / (agg.totalDistanceM / 1000.0);
      }
      double avgRunLengthM =
          agg.runCount > 0 ? agg.totalDistanceM / agg.runCount : 0;

      weekRows.add(
          new WeekRow(ws, title, agg.runCount, agg.totalDistanceM, avgPaceSecPerKm, avgRunLengthM));
    }

    adapter.notifyDataSetChanged();
  }

  private void openMonthHistory() {
    Intent intent = new Intent(this, MainLayout.class);
    intent.putExtra("HISTORY_TAB", true);
    intent.putExtra("FILTER_YEAR", year);
    intent.putExtra("FILTER_MONTH", monthZeroBased);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    setResult(RESULT_OK);
    startActivity(intent);
    finish();
  }

  private static final class Agg {
    int runCount;
    double totalDistanceM;
    long totalTimeSec;

    void addRun(double distanceM, long timeSec) {
      runCount++;
      totalDistanceM += distanceM;
      totalTimeSec += timeSec;
    }
  }

  private class WeekListAdapter extends BaseAdapter {
    @Override
    public int getCount() {
      return weekRows.size();
    }

    @Override
    public WeekRow getItem(int position) {
      return weekRows.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        LayoutInflater inflater = LayoutInflater.from(MonthWeekBreakdownActivity.this);
        view = inflater.inflate(R.layout.statistics_detail_row, parent, false);
      }

      WeekRow row = getItem(position);
      TextView monthText = view.findViewById(R.id.month_text);
      TextView runCountText = view.findViewById(R.id.run_count_text);
      TextView totalDistanceText = view.findViewById(R.id.total_distance_text);
      TextView avgPaceText = view.findViewById(R.id.avg_pace_text);
      TextView avgRunLengthText = view.findViewById(R.id.avg_run_length_text);

      monthText.setText(row.title);
      runCountText.setText(row.runCount + " runs");
      totalDistanceText.setText(
          formatter.formatDistance(Formatter.Format.TXT_SHORT, (long) row.totalDistanceM));

      if (row.totalDistanceM > 0 && row.avgPaceSecPerKm > 0) {
        double pacePerMeter = row.avgPaceSecPerKm / 1000.0;
        avgPaceText.setText(formatter.formatPace(Formatter.Format.TXT_SHORT, pacePerMeter));
      } else {
        avgPaceText.setText("-");
      }

      avgRunLengthText.setText(
          formatter.formatDistance(Formatter.Format.TXT_SHORT, (long) row.avgRunLengthM));

      return view;
    }
  }
}
