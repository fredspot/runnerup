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

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.core.util.WeekCalendarUtil;
import org.runnerup.data.DBHelper;

public class WeeklyKmActivity extends AppCompatActivity {

  private SQLiteDatabase mDB;
  private GraphView graph;
  private TextView rangeSummary;
  private TextView emptyHint;

  private long rangeStartSec;
  private long rangeEndSecExclusive;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.weekly_km);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(R.string.weekly_km_title);
    }

    mDB = DBHelper.getReadableDatabase(this);
    graph = findViewById(R.id.weekly_km_graph);
    rangeSummary = findViewById(R.id.weekly_km_range_summary);
    emptyHint = findViewById(R.id.weekly_km_empty_hint);

    Button changeRange = findViewById(R.id.weekly_km_change_range);
    changeRange.setOnClickListener(v -> showDateRangeFlow());
    TextView resetDefault = findViewById(R.id.weekly_km_reset_default);
    resetDefault.setOnClickListener(
        v -> {
          applyDefaultRange();
          refreshChart();
        });

    View rootView = findViewById(R.id.weekly_km_root);
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

    applyDefaultRange();
    refreshChart();
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

  private void applyDefaultRange() {
    Calendar todayStart = Calendar.getInstance();
    todayStart.set(Calendar.HOUR_OF_DAY, 0);
    todayStart.set(Calendar.MINUTE, 0);
    todayStart.set(Calendar.SECOND, 0);
    todayStart.set(Calendar.MILLISECOND, 0);

    Calendar rangeEnd = (Calendar) todayStart.clone();
    rangeEnd.add(Calendar.DAY_OF_MONTH, 1);

    Calendar rangeStart = (Calendar) todayStart.clone();
    rangeStart.add(Calendar.MONTH, -2);

    rangeStartSec = rangeStart.getTimeInMillis() / 1000;
    rangeEndSecExclusive = rangeEnd.getTimeInMillis() / 1000;
  }

  /** Two-step pick: start date (Next), then end date (OK), full-screen pickers. */
  private void showDateRangeFlow() {
    Calendar startCal = Calendar.getInstance();
    startCal.setTimeInMillis(rangeStartSec * 1000L);

    final DatePickerDialog first =
        new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {},
            startCal.get(Calendar.YEAR),
            startCal.get(Calendar.MONTH),
            startCal.get(Calendar.DAY_OF_MONTH));
    first.setTitle(getString(R.string.weekly_km_pick_start_date));
    first.setOnShowListener(
        di -> {
          first
              .getButton(DialogInterface.BUTTON_POSITIVE)
              .setOnClickListener(
                  v -> {
                    DatePicker dp = first.getDatePicker();
                    Calendar fromC = Calendar.getInstance();
                    fromC.set(
                        dp.getYear(), dp.getMonth(), dp.getDayOfMonth(), 0, 0, 0);
                    fromC.set(Calendar.MILLISECOND, 0);
                    final long fromMs = fromC.getTimeInMillis();
                    first.dismiss();
                    showEndDatePicker(fromMs, fromC);
                  });
          first.getButton(DialogInterface.BUTTON_POSITIVE).setText(R.string.weekly_km_next);
        });
    first.show();
  }

  private void showEndDatePicker(final long fromMs, final Calendar fromC) {
    Calendar toCal = Calendar.getInstance();
    toCal.setTimeInMillis(rangeEndSecExclusive * 1000L);
    toCal.add(Calendar.DAY_OF_MONTH, -1);
    if (toCal.getTimeInMillis() < fromMs) {
      toCal.setTimeInMillis(fromMs);
    }

    DatePickerDialog second =
        new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
              Calendar toC = Calendar.getInstance();
              toC.set(year, month, dayOfMonth, 0, 0, 0);
              toC.set(Calendar.MILLISECOND, 0);
              if (toC.before(fromC)) {
                Toast.makeText(this, R.string.weekly_km_invalid_range, Toast.LENGTH_SHORT).show();
                return;
              }
              rangeStartSec = fromMs / 1000;
              Calendar endEx = (Calendar) toC.clone();
              endEx.add(Calendar.DAY_OF_MONTH, 1);
              rangeEndSecExclusive = endEx.getTimeInMillis() / 1000;
              refreshChart();
            },
            toCal.get(Calendar.YEAR),
            toCal.get(Calendar.MONTH),
            toCal.get(Calendar.DAY_OF_MONTH));
    second.setTitle(getString(R.string.weekly_km_pick_end_date));
    second.getDatePicker().setMinDate(fromMs);
    second.show();
  }

  private void updateRangeSummary() {
    DateFormat df = SimpleDateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
    Calendar fromC = Calendar.getInstance();
    fromC.setTimeInMillis(rangeStartSec * 1000L);
    Calendar toInclusive = Calendar.getInstance();
    toInclusive.setTimeInMillis(rangeEndSecExclusive * 1000L);
    toInclusive.add(Calendar.MILLISECOND, -1);
    rangeSummary.setText(
        df.format(fromC.getTime()) + " \u2013 " + df.format(toInclusive.getTime()));
  }

  private void refreshChart() {
    updateRangeSummary();

    List<Long> weekStarts =
        WeekCalendarUtil.buildMondayWeekStartsInRange(rangeStartSec, rangeEndSecExclusive);
    Map<Long, Double> kmByWeek = new HashMap<>();
    for (Long ws : weekStarts) {
      kmByWeek.put(ws, 0.0);
    }

    String sql =
        "SELECT "
            + Constants.DB.ACTIVITY.START_TIME
            + ", "
            + Constants.DB.ACTIVITY.DISTANCE
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
      String.valueOf(rangeStartSec),
      String.valueOf(rangeEndSecExclusive)
    };

    double totalKm = 0;
    try (Cursor cursor = mDB.rawQuery(sql, args)) {
      while (cursor.moveToNext()) {
        long startTimeSec = cursor.getLong(0);
        double meters = cursor.getDouble(1);
        Calendar act = Calendar.getInstance();
        act.setTimeInMillis(startTimeSec * 1000L);
        long weekStart = WeekCalendarUtil.mondayWeekStartMillis(act);
        Double prev = kmByWeek.get(weekStart);
        if (prev != null) {
          double km = meters / 1000.0;
          kmByWeek.put(weekStart, prev + km);
          totalKm += km;
        }
      }
    }

    emptyHint.setVisibility(totalKm <= 0 ? View.VISIBLE : View.GONE);

    int n = weekStarts.size();
    DataPoint[] points = new DataPoint[n];
    for (int i = 0; i < n; i++) {
      points[i] = new DataPoint(i, kmByWeek.get(weekStarts.get(i)));
    }

    graph.removeAllSeries();
    if (n == 0) {
      return;
    }

    LineGraphSeries<DataPoint> series = new LineGraphSeries<>(points);
    int lineColor = ContextCompat.getColor(this, R.color.colorAccent);
    series.setColor(lineColor);
    series.setThickness(4);
    series.setDrawDataPoints(true);
    series.setDataPointsRadius(6);
    series.setDrawBackground(true);
    series.setBackgroundColor(
        Color.argb(20, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor)));
    graph.addSeries(series);

    graph.getViewport().setXAxisBoundsManual(true);
    graph.getViewport().setYAxisBoundsManual(true);
    graph.getViewport().setMinX(0);
    graph.getViewport().setMaxX(Math.max(0, n - 1));

    double maxY = 1;
    for (int i = 0; i < n; i++) {
      maxY = Math.max(maxY, points[i].getY());
    }
    graph.getViewport().setMinY(0);
    graph.getViewport().setMaxY(maxY * 1.15);

    Locale locale = Locale.getDefault();
    Calendar cFirst = Calendar.getInstance();
    cFirst.setTimeInMillis(weekStarts.get(0));
    Calendar cLast = Calendar.getInstance();
    cLast.setTimeInMillis(weekStarts.get(n - 1));
    boolean spanYears = cFirst.get(Calendar.YEAR) != cLast.get(Calendar.YEAR);
    final SimpleDateFormat labelSdf = weekChartLabelFormat(locale, n, spanYears);

    final int numHorizontalLabels = weekChartHorizontalLabelCount(n);
    float density = getResources().getDisplayMetrics().density;
    boolean tiltLabels = n > 6 || numHorizontalLabels > 6;

    GridLabelRenderer renderer = graph.getGridLabelRenderer();
    renderer.setHumanRounding(false);
    renderer.setHorizontalLabelsVisible(true);
    renderer.setVerticalLabelsVisible(true);
    renderer.setGridStyle(GridLabelRenderer.GridStyle.HORIZONTAL);
    renderer.setHighlightZeroLines(false);
    renderer.setHorizontalLabelsAngle(tiltLabels ? 45 : 0);
    renderer.setLabelsSpace((int) ((tiltLabels ? 80 : 52) * density));
    renderer.setPadding((int) (14 * density));
    renderer.setLabelHorizontalHeight((int) ((tiltLabels ? 88 : 44) * density));
    graph.setBackgroundColor(Color.TRANSPARENT);

    final List<Long> weekStartsFinal = weekStarts;
    renderer.setLabelFormatter(
        new DefaultLabelFormatter() {
          @Override
          public String formatLabel(double value, boolean isValueX) {
            if (isValueX) {
              int idx = Math.round((float) value);
              if (idx < 0) {
                idx = 0;
              }
              if (idx >= n) {
                idx = n - 1;
              }
              return labelSdf.format(new Date(weekStartsFinal.get(idx)));
            }
            if (Math.abs(value - Math.rint(value)) < 1e-6) {
              return String.format(Locale.getDefault(), "%.0f", value);
            }
            return String.format(Locale.getDefault(), "%.1f", value);
          }
        });
    renderer.setVerticalAxisTitle(getString(R.string.weekly_km_axis_km));
    renderer.setNumHorizontalLabels(numHorizontalLabels);
  }

  /**
   * GraphView draws this many evenly spaced ticks from minX to maxX. For small week counts use
   * one tick per week so labels align with points; for long ranges use fewer ticks so text fits.
   */
  private static int weekChartHorizontalLabelCount(int n) {
    if (n <= 1) {
      return 2;
    }
    if (n <= 8) {
      return n;
    }
    if (n <= 14) {
      return 8;
    }
    if (n <= 22) {
      return 7;
    }
    if (n <= 35) {
      return 6;
    }
    return 5;
  }

  /** Shorter dates when there are many weeks so ticks stay readable. */
  private static SimpleDateFormat weekChartLabelFormat(
      Locale locale, int weekCount, boolean spanYears) {
    if (weekCount > 18) {
      return new SimpleDateFormat("M/d", locale);
    }
    if (spanYears) {
      return new SimpleDateFormat("MMM d, yy", locale);
    }
    return new SimpleDateFormat("MMM d", locale);
  }

}
