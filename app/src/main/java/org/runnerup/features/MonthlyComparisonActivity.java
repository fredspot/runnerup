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
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.core.util.Formatter;
import org.runnerup.core.util.HRZones;
import org.runnerup.data.DBHelper;
import org.runnerup.analytics.MonthlyComparisonCalculator;

public class MonthlyComparisonActivity extends AppCompatActivity {

  private static final String TAG = "MonthlyComparisonAct";
  private SQLiteDatabase mDB = null;
  private Formatter formatter = null;
  private View rootView;
  private ScrollView contentScroll;
  private TextView emptyHint;
  private ProgressBar loadingIndicator;
  private boolean isLoading = false;

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

    rootView = findViewById(R.id.monthly_comparison_root);
    contentScroll = findViewById(R.id.monthly_comparison_content);
    emptyHint = findViewById(R.id.monthly_comparison_empty_hint);
    loadingIndicator = findViewById(R.id.monthly_comparison_loading);

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

    mDB = DBHelper.getReadableDatabase(this);
    formatter = new Formatter(this);

    MonthlyComparisonBinder.bindZoneMetricLabels(
        this, rootView, MonthlyComparisonCalculator.resolveZoneBounds(new HRZones(this)));
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
        MonthlyComparisonBinder.Data data = MonthlyComparisonBinder.readFromCursor(cursor);
        return data.currentZonePace[1] <= 0
            && data.currentZonePace[2] <= 0
            && data.currentZonePace[3] <= 0
            && data.currentZonePace[4] <= 0;
      }
    }
    return true;
  }

  private void recomputeComparisonAsync() {
    setLoading(true);
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
          MonthlyComparisonBinder.Data data = MonthlyComparisonBinder.readFromCursor(cursor);
          MonthlyComparisonBinder.bind(this, rootView, formatter, data);
          showContent(MonthlyComparisonBinder.hasMeaningfulData(data));
        } catch (Exception e) {
          android.util.Log.e(TAG, "Error reading monthly comparison data: " + e.getMessage(), e);
          showContent(false);
        }
      }
    } catch (Exception e) {
      android.util.Log.e(TAG, "Error loading comparison data: " + e.getMessage(), e);
      showContent(false);
    } finally {
      setLoading(false);
    }
  }

  private void setLoading(boolean loading) {
    isLoading = loading;
    loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
    if (loading) {
      contentScroll.setVisibility(View.GONE);
      emptyHint.setVisibility(View.GONE);
    }
  }

  private void showContent(boolean hasData) {
    if (isLoading) {
      return;
    }
    emptyHint.setVisibility(hasData ? View.GONE : View.VISIBLE);
    contentScroll.setVisibility(hasData ? View.VISIBLE : View.GONE);
  }
}
