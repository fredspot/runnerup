/*
 * Copyright (C) 2026 RunnerUp
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
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.core.util.Formatter;
import org.runnerup.data.DBHelper;

public class RunCalendarActivity extends AppCompatActivity implements Constants {

  private SQLiteDatabase mDB;
  private Formatter formatter;
  private final SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
  private final SimpleDateFormat monthTitleFormat = new SimpleDateFormat("LLLL yyyy", Locale.getDefault());

  private TextView monthTitleView;
  private TextView emptyHint;
  private View[][] dayCells = new View[6][7];

  private int viewYear;
  private int viewMonth; // 0–11

  private Map<String, DayStats> statsByDay = new HashMap<>();

  private static final class DayStats {
    int runCount;
    double totalDistanceM;
    long totalTimeSec;
    long sumHr;
    int hrRunsCount;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_run_calendar);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(R.string.run_calendar_title);
    }

    mDB = DBHelper.getReadableDatabase(this);
    formatter = new Formatter(this);

    monthTitleView = findViewById(R.id.run_calendar_month_title);
    emptyHint = findViewById(R.id.run_calendar_empty_hint);

    ImageButton prev = findViewById(R.id.run_calendar_prev);
    ImageButton next = findViewById(R.id.run_calendar_next);
    prev.setOnClickListener(v -> shiftMonth(-1));
    next.setOnClickListener(v -> shiftMonth(1));

    buildCalendarGrid();
    populateWeekdayRow();

    java.util.Calendar now = java.util.Calendar.getInstance();
    viewYear = now.get(java.util.Calendar.YEAR);
    viewMonth = now.get(java.util.Calendar.MONTH);

    View rootView = findViewById(R.id.run_calendar_root);
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

    refreshMonth();
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

  private void buildCalendarGrid() {
    TableLayout table = findViewById(R.id.run_calendar_table);
    for (int r = 0; r < 6; r++) {
      TableRow row = new TableRow(this);
      for (int c = 0; c < 7; c++) {
        View cell = getLayoutInflater().inflate(R.layout.item_run_calendar_day, row, false);
        TableRow.LayoutParams lp =
            new TableRow.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cell.setLayoutParams(lp);
        row.addView(cell);
        dayCells[r][c] = cell;
      }
      table.addView(row);
    }
  }

  private void populateWeekdayRow() {
    LinearLayout row = findViewById(R.id.run_calendar_weekday_row);
    row.removeAllViews();
    java.util.Calendar cal = java.util.Calendar.getInstance();
    int firstDow = cal.getFirstDayOfWeek();
    DateFormatSymbols dfs = new DateFormatSymbols(Locale.getDefault());
    String[] shortWeekdays = dfs.getShortWeekdays();
    int dow = firstDow;
    for (int i = 0; i < 7; i++) {
      TextView tv = new TextView(this);
      tv.setText(shortWeekdays[dow]);
      tv.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary));
      tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
      tv.setGravity(Gravity.CENTER);
      LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      row.addView(tv, lp);
      dow++;
      if (dow > java.util.Calendar.SATURDAY) {
        dow = java.util.Calendar.SUNDAY;
      }
    }
  }

  private void shiftMonth(int delta) {
    java.util.Calendar c = java.util.Calendar.getInstance();
    c.set(viewYear, viewMonth, 1);
    c.add(java.util.Calendar.MONTH, delta);
    viewYear = c.get(java.util.Calendar.YEAR);
    viewMonth = c.get(java.util.Calendar.MONTH);
    refreshMonth();
  }

  private void refreshMonth() {
    loadStatsForVisibleMonth();
    monthTitleView.setText(monthTitleFormat.format(monthStartCal().getTime()));
    emptyHint.setVisibility(statsByDay.isEmpty() ? View.VISIBLE : View.GONE);

    java.util.Calendar monthCal = monthStartCal();
    int firstDayDow = monthCal.get(java.util.Calendar.DAY_OF_WEEK);
    int firstDayOfWeek = monthCal.getFirstDayOfWeek();
    int startOffset = (firstDayDow - firstDayOfWeek + 7) % 7;
    int daysInMonth = monthCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);

    for (int i = 0; i < 42; i++) {
      int row = i / 7;
      int col = i % 7;
      View cell = dayCells[row][col];
      TextView num = cell.findViewById(R.id.run_calendar_day_number);
      TextView dist = cell.findViewById(R.id.run_calendar_day_distance);

      int day = i - startOffset + 1;
      if (i < startOffset || day > daysInMonth) {
        num.setText("");
        dist.setVisibility(View.GONE);
        dist.setText("");
        cell.setBackground(null);
        cell.setOnClickListener(null);
        cell.setClickable(false);
        cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        continue;
      }

      java.util.Calendar dayCal = (java.util.Calendar) monthCal.clone();
      dayCal.set(viewYear, viewMonth, day);
      String key = dateKeyFormat.format(dayCal.getTime());

      num.setText(String.format(Locale.getDefault(), "%d", day));
      DayStats stats = statsByDay.get(key);
      if (stats != null && stats.runCount > 0) {
        num.setTextColor(ContextCompat.getColor(this, R.color.colorText));
        num.setTypeface(null, Typeface.BOLD);
        dist.setVisibility(View.VISIBLE);
        dist.setText(formatCellDistance(stats.totalDistanceM));
        cell.setBackgroundResource(R.drawable.run_calendar_day_has_run);
        cell.setClickable(true);
        cell.setOnClickListener(v -> showDaySummary(dayCal.getTime(), stats));
        cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
      } else {
        num.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary));
        num.setTypeface(null, Typeface.NORMAL);
        dist.setVisibility(View.GONE);
        dist.setText("");
        cell.setBackground(null);
        cell.setOnClickListener(null);
        cell.setClickable(false);
        cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
      }
    }
  }

  private java.util.Calendar monthStartCal() {
    java.util.Calendar c = java.util.Calendar.getInstance();
    c.set(viewYear, viewMonth, 1, 0, 0, 0);
    c.set(java.util.Calendar.MILLISECOND, 0);
    return c;
  }

  private void loadStatsForVisibleMonth() {
    statsByDay = new HashMap<>();
    java.util.Calendar start = monthStartCal();
    long startSec = start.getTimeInMillis() / 1000L;
    java.util.Calendar end = (java.util.Calendar) start.clone();
    end.add(java.util.Calendar.MONTH, 1);
    long endSec = end.getTimeInMillis() / 1000L;

    String sql =
        "SELECT a."
            + Constants.DB.ACTIVITY.START_TIME
            + ", a."
            + Constants.DB.ACTIVITY.DISTANCE
            + ", a."
            + Constants.DB.ACTIVITY.TIME
            + ", a."
            + Constants.DB.ACTIVITY.AVG_HR
            + " FROM "
            + Constants.DB.ACTIVITY.TABLE
            + " a WHERE a."
            + Constants.DB.ACTIVITY.SPORT
            + " = ? AND a."
            + Constants.DB.ACTIVITY.DELETED
            + " = 0 AND a."
            + Constants.DB.ACTIVITY.START_TIME
            + " >= ? AND a."
            + Constants.DB.ACTIVITY.START_TIME
            + " < ?";

    try (Cursor cursor =
        mDB.rawQuery(
            sql,
            new String[] {
              String.valueOf(Constants.DB.ACTIVITY.SPORT_RUNNING),
              String.valueOf(startSec),
              String.valueOf(endSec)
            })) {
      while (cursor.moveToNext()) {
        long startTimeSec = cursor.getLong(0);
        double distanceM = cursor.getDouble(1);
        long timeSec = cursor.getLong(2);
        int hr = 0;
        if (!cursor.isNull(3)) {
          hr = cursor.getInt(3);
        }

        String key = dateKeyFormat.format(new Date(startTimeSec * 1000L));
        DayStats agg = statsByDay.get(key);
        if (agg == null) {
          agg = new DayStats();
          statsByDay.put(key, agg);
        }
        agg.runCount++;
        agg.totalDistanceM += distanceM;
        agg.totalTimeSec += timeSec;
        if (hr > 0) {
          agg.sumHr += hr;
          agg.hrRunsCount++;
        }
      }
    }
  }

  private String formatCellDistance(double meters) {
    boolean metric =
        Formatter.getUseMetric(
            getResources(), PreferenceManager.getDefaultSharedPreferences(this), null);
    if (metric) {
      if (meters >= 1000.0) {
        int km = (int) Math.round(meters / Formatter.km_meters);
        return km + " " + getString(org.runnerup.common.R.string.metrics_distance_km);
      }
      return Math.round(meters)
          + " "
          + getString(org.runnerup.common.R.string.metrics_distance_m);
    }
    if (meters >= Formatter.mi_meters * 0.99) {
      int mi = (int) Math.round(meters / Formatter.mi_meters);
      return mi + " " + getString(org.runnerup.common.R.string.metrics_distance_mi);
    }
    return Math.round(meters)
        + " "
        + getString(org.runnerup.common.R.string.metrics_distance_m);
  }

  private void showDaySummary(Date day, DayStats stats) {
    String title =
        android.text.format.DateFormat.getMediumDateFormat(this).format(day);
    String dist =
        formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(stats.totalDistanceM));
    String time = formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, stats.totalTimeSec);

    StringBuilder msg = new StringBuilder();
    msg.append(getString(R.string.run_calendar_summary_runs, stats.runCount)).append('\n');
    msg.append(getString(R.string.run_calendar_summary_distance, dist)).append('\n');
    msg.append(getString(R.string.run_calendar_summary_time, time)).append('\n');
    if (stats.hrRunsCount > 0) {
      int avgHr = (int) Math.round((double) stats.sumHr / stats.hrRunsCount);
      msg.append(getString(R.string.run_calendar_summary_avg_hr, avgHr));
    } else {
      msg.append(getString(R.string.run_calendar_summary_avg_hr_na));
    }

    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(msg.toString())
        .setPositiveButton(android.R.string.ok, null)
        .show();
  }
}
