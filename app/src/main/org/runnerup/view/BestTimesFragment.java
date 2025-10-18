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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.BestTimesCalculator;
import org.runnerup.db.DBHelper;
import java.util.ArrayList;
import java.util.List;

public class BestTimesFragment extends Fragment
    implements Constants, OnItemClickListener {

  private SQLiteDatabase mDB = null;
  private BestTimesListAdapter adapter = null;
  private Button computeButton = null;
  private List<Integer> distances = new ArrayList<>();

  // Target distances in meters
  private static final int[] TARGET_DISTANCES = {1000, 5000, 10000, 21097, 42195};
  private static final String[] DISTANCE_LABELS = {"1km", "5km", "10km", "Half Marathon", "Marathon"};

  public BestTimesFragment() {
    super(R.layout.best_times);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    ListView listView = view.findViewById(R.id.best_times_list);
    computeButton = view.findViewById(R.id.compute_best_times_button);

    Context context = requireContext();
    
    // Set up compute button
    computeButton.setOnClickListener(v -> {
      new ComputeBestTimesTask().execute();
    });

    mDB = DBHelper.getWritableDatabase(context);
    listView.setDividerHeight(2);
    listView.setOnItemClickListener(this);
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
    distances.clear();
    String sql = "SELECT DISTINCT " + Constants.DB.BEST_TIMES.DISTANCE + 
                 " FROM " + Constants.DB.BEST_TIMES.TABLE + 
                 " ORDER BY " + Constants.DB.BEST_TIMES.DISTANCE;
    
    try (Cursor cursor = mDB.rawQuery(sql, null)) {
      while (cursor.moveToNext()) {
        distances.add(cursor.getInt(0));
      }
    }
    adapter.notifyDataSetChanged();
  }

  @Override
  public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
    if (position < distances.size()) {
      int distance = distances.get(position);
      Intent intent = new Intent(requireContext(), BestTimesDetailActivity.class);
      intent.putExtra("DISTANCE", distance);
      startActivity(intent);
    }
  }

  /**
   * AsyncTask to compute best times in background.
   */
  private class ComputeBestTimesTask extends AsyncTask<Void, Void, Integer> {
    private ProgressDialog progressDialog;

    @Override
    protected void onPreExecute() {
      progressDialog = new ProgressDialog(requireContext());
      progressDialog.setMessage(getString(R.string.computing_best_times));
      progressDialog.setCancelable(false);
      progressDialog.show();
    }

    @Override
    protected Integer doInBackground(Void... params) {
      try {
        return BestTimesCalculator.computeBestTimes(mDB);
      } catch (Exception e) {
        return 0;
      }
    }

    @Override
    protected void onPostExecute(Integer result) {
      progressDialog.dismiss();
      
      if (result > 0) {
        Toast.makeText(requireContext(), 
            getString(R.string.best_times_computed, result), 
            Toast.LENGTH_LONG).show();
        // Close current DB connection and reopen to ensure fresh data
        DBHelper.closeDB(mDB);
        mDB = DBHelper.getWritableDatabase(requireContext());
        loadDistances();
      } else {
        Toast.makeText(requireContext(), 
            getString(R.string.best_times_compute_error), 
            Toast.LENGTH_LONG).show();
      }
    }
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
      return distances.size();
    }

    @Override
    public Object getItem(int position) {
      return distances.get(position);
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
      
      int distance = distances.get(position);
      
      TextView distanceText = convertView.findViewById(R.id.distance_text);
      TextView descriptionText = convertView.findViewById(R.id.description_text);
      
      // Find the label for this distance
      String label = getDistanceLabel(distance);
      distanceText.setText(label);
      descriptionText.setText(getString(R.string.best_times_description));
      
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
