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

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.db.entities.MonthlyStatsEntity;
import org.runnerup.util.Formatter;
import java.util.ArrayList;
import java.util.List;

public class StatisticsDetailActivity extends AppCompatActivity implements Constants, OnItemClickListener {

  private int targetYear = 0;
  private SQLiteDatabase mDB = null;
  private Formatter formatter = null;
  private StatisticsListAdapter adapter = null;

  // Month names
  private static final String[] MONTH_NAMES = {
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.statistics_detail);

    // Get target year from intent
    targetYear = getIntent().getIntExtra("YEAR", 2024);

    // Set up toolbar
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(String.valueOf(targetYear) + " - " + getString(R.string.statistics));
    }

    // Initialize database and formatter
    mDB = DBHelper.getReadableDatabase(this);
    formatter = new Formatter(this);

    // Set up list view
    ListView listView = findViewById(R.id.statistics_detail_list);
    adapter = new StatisticsListAdapter();
    listView.setAdapter(adapter);
    listView.setOnItemClickListener(this);

    // Load data
    loadMonthlyStats();
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

  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    // For now, monthly stats don't have click actions
    // Could be extended to show daily stats or activity list
  }

  private void loadMonthlyStats() {
    String sql = "SELECT * FROM " + Constants.DB.MONTHLY_STATS.TABLE +
                 " WHERE " + Constants.DB.MONTHLY_STATS.YEAR + " = ?" +
                 " ORDER BY " + Constants.DB.MONTHLY_STATS.MONTH;

    Cursor cursor = mDB.rawQuery(sql, new String[]{String.valueOf(targetYear)});
    adapter.swapCursor(cursor);
  }

  /**
   * Adapter for displaying monthly statistics list.
   */
  class StatisticsListAdapter extends BaseAdapter {
    private Cursor cursor;

    public void swapCursor(Cursor newCursor) {
      if (cursor != null) {
        cursor.close();
      }
      cursor = newCursor;
      notifyDataSetChanged();
    }

    @Override
    public int getCount() {
      return cursor != null ? cursor.getCount() : 0;
    }

    @Override
    public MonthlyStatsEntity getItem(int position) {
      if (cursor != null && cursor.moveToPosition(position)) {
        return new MonthlyStatsEntity(cursor);
      }
      return null;
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        LayoutInflater inflater = LayoutInflater.from(StatisticsDetailActivity.this);
        view = inflater.inflate(R.layout.statistics_detail_row, parent, false);
      }

      MonthlyStatsEntity stats = getItem(position);
      if (stats != null) {
        TextView monthText = view.findViewById(R.id.month_text);
        TextView runCountText = view.findViewById(R.id.run_count_text);
        TextView totalDistanceText = view.findViewById(R.id.total_distance_text);
        TextView avgPaceText = view.findViewById(R.id.avg_pace_text);
        TextView avgRunLengthText = view.findViewById(R.id.avg_run_length_text);

        // Month name
        if (stats.getMonth() != null && stats.getMonth() >= 1 && stats.getMonth() <= 12) {
          monthText.setText(MONTH_NAMES[stats.getMonth() - 1]);
        } else {
          monthText.setText("Month " + stats.getMonth());
        }

        // Run count
        if (stats.getRunCount() != null) {
          runCountText.setText(stats.getRunCount() + " runs");
        } else {
          runCountText.setText("0 runs");
        }

        // Total distance
        if (stats.getTotalDistance() != null) {
          totalDistanceText.setText(formatter.formatDistance(Formatter.Format.TXT_SHORT, stats.getTotalDistance().longValue()));
        } else {
          totalDistanceText.setText("-");
        }

        // Average pace
        if (stats.getAvgPace() != null) {
          // Convert pace from seconds per km to seconds per meter
          double pacePerMeter = stats.getAvgPace() / 1000.0;
          avgPaceText.setText(formatter.formatPace(Formatter.Format.TXT_SHORT, pacePerMeter));
        } else {
          avgPaceText.setText("-");
        }

        // Average run length
        if (stats.getAvgRunLength() != null) {
          avgRunLengthText.setText(formatter.formatDistance(Formatter.Format.TXT_SHORT, stats.getAvgRunLength().longValue()));
        } else {
          avgRunLengthText.setText("-");
        }
      }

      return view;
    }
  }
}
