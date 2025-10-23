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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.db.entities.BestTimesEntity;
import org.runnerup.util.Formatter;

public class BestTimesDetailActivity extends AppCompatActivity implements Constants, OnItemClickListener {

  private int targetDistance = 0;
  private SQLiteDatabase mDB = null;
  private Formatter formatter = null;
  private BestTimesListAdapter adapter = null;

  // Distance labels
  private static final int[] TARGET_DISTANCES = {1000, 5000, 10000, 15000, 20000, 21097, 30000, 40000, 42195};
  private static final String[] DISTANCE_LABELS = {"1km", "5km", "10km", "15km", "20km", "Half Marathon", "30km", "40km", "Marathon"};

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.best_times_detail);

    // Get target distance from intent
    targetDistance = getIntent().getIntExtra("DISTANCE", 1000);

    // Set up toolbar
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(getDistanceLabel(targetDistance) + " - " + getString(R.string.best_times));
    }

    // Initialize database and formatter
    mDB = DBHelper.getReadableDatabase(this);
    formatter = new Formatter(this);

    // Set up list view
    ListView listView = findViewById(R.id.best_times_detail_list);
    adapter = new BestTimesListAdapter();
    listView.setAdapter(adapter);
    listView.setOnItemClickListener(this);

    // Load data
    loadBestTimes();
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
    BestTimesEntity bestTime = adapter.getItem(position);
    if (bestTime != null && bestTime.getActivityId() != null) {
      // Open the activity detail
      Intent intent = new Intent(this, DetailActivity.class);
      intent.putExtra("ID", bestTime.getActivityId());
      intent.putExtra("mode", "details");
      startActivity(intent);
    }
  }

  private void loadBestTimes() {
    String sql = "SELECT * FROM " + Constants.DB.BEST_TIMES.TABLE +
                 " WHERE " + Constants.DB.BEST_TIMES.DISTANCE + " = ?" +
                 " ORDER BY " + Constants.DB.BEST_TIMES.RANK;

    Cursor cursor = mDB.rawQuery(sql, new String[]{String.valueOf(targetDistance)});
    adapter.swapCursor(cursor);
  }

  private String getDistanceLabel(int distance) {
    for (int i = 0; i < TARGET_DISTANCES.length; i++) {
      if (TARGET_DISTANCES[i] == distance) {
        return DISTANCE_LABELS[i];
      }
    }
    return distance + "m";
  }

  /**
   * Adapter for displaying best times list.
   */
  class BestTimesListAdapter extends BaseAdapter {
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
    public BestTimesEntity getItem(int position) {
      if (cursor != null && cursor.moveToPosition(position)) {
        return new BestTimesEntity(cursor);
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
        LayoutInflater inflater = LayoutInflater.from(BestTimesDetailActivity.this);
        view = inflater.inflate(R.layout.best_times_detail_row, parent, false);
      }

      BestTimesEntity bestTime = getItem(position);
      if (bestTime != null) {
        TextView rankText = view.findViewById(R.id.rank_text);
        TextView timeText = view.findViewById(R.id.time_text);
        TextView paceText = view.findViewById(R.id.pace_text);
        TextView dateText = view.findViewById(R.id.date_text);
        TextView hrText = view.findViewById(R.id.hr_text);

        // Rank
        rankText.setText("#" + bestTime.getRank());

        // Time
        if (bestTime.getTime() != null) {
          // Convert milliseconds to seconds for formatter
          long timeInSeconds = bestTime.getTime() / 1000;
          timeText.setText(formatter.formatElapsedTime(Formatter.Format.TXT_LONG, timeInSeconds));
        }

        // Pace
        if (bestTime.getPace() != null) {
          // Convert pace from seconds per km to seconds per meter
          double pacePerMeter = bestTime.getPace() / 1000.0;
          paceText.setText(formatter.formatPace(Formatter.Format.TXT_LONG, pacePerMeter));
        }

        // Date
        if (bestTime.getStartTime() != null) {
          // startTime is stored in seconds (Unix timestamp), convert to milliseconds
          Date date = new Date(bestTime.getStartTime() * 1000);
          SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
          dateText.setText(dateFormat.format(date));
        }

        // Heart rate
        if (bestTime.getAvgHr() != null && bestTime.getAvgHr() > 0) {
          hrText.setText(formatter.formatHeartRate(Formatter.Format.TXT_SHORT, bestTime.getAvgHr()));
        } else {
          hrText.setText("-");
        }
      }

      return view;
    }
  }
}
