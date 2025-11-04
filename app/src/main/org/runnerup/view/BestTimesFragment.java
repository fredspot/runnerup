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

import android.content.Context;
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
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.BestTimesCalculator;
import org.runnerup.db.DBHelper;
import org.runnerup.db.entities.BestTimesSummaryEntity;
import org.runnerup.db.entities.BestTimesEntity;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BestTimesFragment extends Fragment
    implements Constants, OnItemClickListener {

  private SQLiteDatabase mDB = null;
  private BestTimesListAdapter adapter = null;
  private List<BestTimesSummaryEntity> summaries = new ArrayList<>();
  private List<BestTimesEntity> bestTimes = new ArrayList<>(); // Rank 1 best times for each distance
  private org.runnerup.util.Formatter formatter;

  // Target distances in meters
  private static final int[] TARGET_DISTANCES = {1000, 5000, 10000, 15000, 20000, 21097, 30000, 40000, 42195};
  private static final String[] DISTANCE_LABELS = {"1km", "5km", "10km", "15km", "20km", "Half Marathon", "30km", "40km", "Marathon"};

  public BestTimesFragment() {
    super(R.layout.best_times);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    ListView listView = view.findViewById(R.id.best_times_list);

    Context context = requireContext();

    mDB = DBHelper.getWritableDatabase(context);
    formatter = new org.runnerup.util.Formatter(context);
    listView.setDividerHeight(16); // Spacing between cards
    // Remove row-level click listener - individual parts handle clicks
    listView.setOnItemLongClickListener((parent, view1, position, id) -> {
      // Long press to force recomputation
      android.util.Log.d("BestTimesFragment", "Long press detected - forcing recomputation");
      forceRecomputation();
      return true;
    });
    adapter = new BestTimesListAdapter(context);
    listView.setAdapter(adapter);

    loadDistances();
  }

  @Override
  public void onResume() {
    super.onResume();
    loadDistances();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    DBHelper.closeDB(mDB);
  }

  private void loadDistances() {
    summaries.clear();
    bestTimes.clear();
    
    // Load summaries (averages and counts)
    String sql = "SELECT " + Constants.DB.BEST_TIMES.DISTANCE + 
                 ", AVG(" + Constants.DB.BEST_TIMES.TIME + ") as avg_time" +
                 ", COUNT(*) as count" +
                 " FROM " + Constants.DB.BEST_TIMES.TABLE + 
                 " GROUP BY " + Constants.DB.BEST_TIMES.DISTANCE +
                 " ORDER BY " + Constants.DB.BEST_TIMES.DISTANCE;
    
    android.util.Log.d("BestTimesFragment", "Loading best times summaries from database...");
    try (Cursor cursor = mDB.rawQuery(sql, null)) {
      int count = 0;
      while (cursor.moveToNext()) {
        BestTimesSummaryEntity summary = new BestTimesSummaryEntity(cursor);
        summaries.add(summary);
        android.util.Log.d("BestTimesFragment", "Found summary: " + summary.getDistance() + "m, avg=" + summary.getAverageTime() + "ms, count=" + summary.getCount());
        count++;
      }
      android.util.Log.d("BestTimesFragment", "Total summaries found: " + count);
    }
    
    // Load rank 1 best time for each distance
    for (BestTimesSummaryEntity summary : summaries) {
      String bestTimeSql = "SELECT * FROM " + Constants.DB.BEST_TIMES.TABLE +
                           " WHERE " + Constants.DB.BEST_TIMES.DISTANCE + " = ?" +
                           " AND " + Constants.DB.BEST_TIMES.RANK + " = 1" +
                           " LIMIT 1";
      try (Cursor cursor = mDB.rawQuery(bestTimeSql, new String[]{String.valueOf(summary.getDistance())})) {
        if (cursor.moveToFirst()) {
          BestTimesEntity bestTime = new BestTimesEntity(cursor);
          bestTimes.add(bestTime);
        } else {
          bestTimes.add(null); // No best time found for this distance
        }
      }
    }
    
    adapter.notifyDataSetChanged();
  }

  private void forceRecomputation() {
    android.util.Log.d("BestTimesFragment", "Forcing recomputation of best times...");
    
    // Clear computation tracking to force recomputation
    mDB.delete(Constants.DB.COMPUTATION_TRACKING.TABLE, 
               Constants.DB.COMPUTATION_TRACKING.COMPUTATION_TYPE + " = ?", 
               new String[]{"best_times"});
    
    android.util.Log.d("BestTimesFragment", "Cleared computation tracking, triggering recomputation...");
    
    // Trigger recomputation in background
    new android.os.AsyncTask<Void, Void, Integer>() {
      @Override
      protected Integer doInBackground(Void... params) {
        return BestTimesCalculator.computeBestTimes(mDB);
      }
      
      @Override
      protected void onPostExecute(Integer result) {
        android.util.Log.d("BestTimesFragment", "Recomputation completed: " + result + " best times computed");
        loadDistances(); // Refresh the list
      }
    }.execute();
  }

  @Override
  public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
    // Clicks are handled individually by distance TextView and card in the adapter
    // This method is required by OnItemClickListener interface but not used
  }


  /**
   * Adapter for displaying distance list.
   */
  class BestTimesListAdapter extends BaseAdapter {
    private final LayoutInflater inflater;

    BestTimesListAdapter(Context context) {
      inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
      return summaries.size();
    }

    @Override
    public Object getItem(int position) {
      return summaries.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        convertView = inflater.inflate(R.layout.best_times_row, parent, false);
      }
      
      BestTimesSummaryEntity summary = summaries.get(position);
      BestTimesEntity bestTime = position < bestTimes.size() ? bestTimes.get(position) : null;
      
      TextView distanceText = convertView.findViewById(R.id.distance_text);
      View cardLayout = convertView.findViewById(R.id.best_run_card);
      TextView timeText = convertView.findViewById(R.id.time_text);
      TextView paceText = convertView.findViewById(R.id.pace_text);
      TextView dateText = convertView.findViewById(R.id.date_text);
      TextView hrText = convertView.findViewById(R.id.hr_text);
      
      // Set click listener for distance (opens top 25 runs)
      distanceText.setOnClickListener(v -> {
        int distance = summary.getDistance();
        Intent intent = new Intent(requireContext(), BestTimesDetailActivity.class);
        intent.putExtra("DISTANCE", distance);
        startActivity(intent);
      });
      
      // Set click listener for card (opens activity detail)
      if (bestTime != null && bestTime.getActivityId() != null) {
        cardLayout.setOnClickListener(v -> {
          Intent intent = new Intent(requireContext(), DetailActivity.class);
          intent.putExtra("ID", bestTime.getActivityId());
          intent.putExtra("mode", "details");
          intent.putExtra("source_tab", 2); // Best Times tab is at position 2
          startActivity(intent);
        });
        cardLayout.setClickable(true);
      } else {
        cardLayout.setClickable(false);
      }
      
      // Find the label for this distance
      String label = getDistanceLabel(summary.getDistance());
      distanceText.setText(label);
      
      // Display best run card data
      if (bestTime != null) {
        // Time
        if (bestTime.getTime() != null) {
          long timeInSeconds = bestTime.getTime() / 1000;
          timeText.setText(formatter.formatElapsedTime(
              org.runnerup.util.Formatter.Format.TXT_LONG, timeInSeconds));
        } else {
          timeText.setText("-");
        }
        
        // Pace
        if (bestTime.getPace() != null) {
          double pacePerMeter = bestTime.getPace() / 1000.0;
          paceText.setText(formatter.formatPace(
              org.runnerup.util.Formatter.Format.TXT_LONG, pacePerMeter));
        } else {
          paceText.setText("-");
        }
        
        // Date
        if (bestTime.getStartTime() != null) {
          Date date = new Date(bestTime.getStartTime() * 1000);
          SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
          dateText.setText(dateFormat.format(date));
        } else {
          dateText.setText("-");
        }
        
        // Heart rate
        if (bestTime.getAvgHr() != null && bestTime.getAvgHr() > 0) {
          hrText.setText(formatter.formatHeartRate(
              org.runnerup.util.Formatter.Format.TXT_SHORT, bestTime.getAvgHr()));
        } else {
          hrText.setText("-");
        }
      } else {
        // No best time data available
        timeText.setText("-");
        paceText.setText("-");
        dateText.setText("-");
        hrText.setText("-");
      }
      
      return convertView;
    }

    private String getDistanceLabel(int distance) {
      for (int i = 0; i < TARGET_DISTANCES.length; i++) {
        if (TARGET_DISTANCES[i] == distance) {
          return DISTANCE_LABELS[i];
        }
      }
      return distance + "m";
    }
  }
}
