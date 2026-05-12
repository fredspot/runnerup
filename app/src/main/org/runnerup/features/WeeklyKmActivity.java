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
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.core.util.WeekCalendarUtil;
import org.runnerup.data.DBHelper;

public class WeeklyKmActivity extends AppCompatActivity {

  private SQLiteDatabase mDB;
  private WeeklyKmChart chart;
  private HorizontalScrollView scroll;
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
    chart = findViewById(R.id.weekly_km_chart);
    scroll = findViewById(R.id.weekly_km_scroll);
    emptyHint = findViewById(R.id.weekly_km_empty_hint);

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

  /**
   * Default range: ~52 Monday-Sunday weeks ending with the current (in-progress) week.
   *
   * <p>End is the Monday strictly after today (exclusive), start is 52 weeks before that Monday.
   * That guarantees the current week is always the rightmost dot.
   */
  private void applyDefaultRange() {
    Calendar today = Calendar.getInstance();
    long currentWeekStart = WeekCalendarUtil.mondayWeekStartMillis(today);
    Calendar end = Calendar.getInstance();
    end.setTimeInMillis(currentWeekStart);
    end.add(Calendar.WEEK_OF_YEAR, 1); // exclusive upper bound: Monday after current week
    Calendar start = Calendar.getInstance();
    start.setTimeInMillis(currentWeekStart);
    start.add(Calendar.WEEK_OF_YEAR, -52);

    rangeStartSec = start.getTimeInMillis() / 1000L;
    rangeEndSecExclusive = end.getTimeInMillis() / 1000L;
  }

  private void refreshChart() {
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
    scroll.setVisibility(totalKm <= 0 ? View.GONE : View.VISIBLE);

    int n = weekStarts.size();
    WeeklyKmChart.WeekPoint[] points = new WeeklyKmChart.WeekPoint[n];
    double maxKm = 0;
    for (int i = 0; i < n; i++) {
      long ws = weekStarts.get(i);
      double km = kmByWeek.get(ws);
      boolean isCurrent = (i == n - 1);
      points[i] = new WeeklyKmChart.WeekPoint(ws, km, isCurrent);
      if (km > maxKm) {
        maxKm = km;
      }
    }
    if (maxKm <= 0) {
      maxKm = 1;
    }

    chart.setData(points, maxKm);
    // Scroll to the rightmost (latest) week once layout settles.
    scroll.post(() -> scroll.fullScroll(View.FOCUS_RIGHT));
  }
}
