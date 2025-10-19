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
import org.runnerup.db.StatisticsCalculator;
import org.runnerup.db.DBHelper;
import org.runnerup.db.entities.YearlyStatsEntity;
import org.runnerup.util.Formatter;
import java.util.ArrayList;
import java.util.List;

public class StatisticsFragment extends Fragment
    implements Constants, OnItemClickListener {

  private SQLiteDatabase mDB = null;
  private StatisticsListAdapter adapter = null;
  private List<YearlyStatsEntity> yearlyStats = new ArrayList<>();
  private Formatter formatter = null;

  public StatisticsFragment() {
    super(R.layout.statistics);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    ListView listView = view.findViewById(R.id.statistics_list);

    Context context = requireContext();

    mDB = DBHelper.getWritableDatabase(context);
    formatter = new Formatter(context);
    listView.setDividerHeight(2);
    listView.setOnItemClickListener(this);
    adapter = new StatisticsListAdapter(context);
    listView.setAdapter(adapter);

    loadYears();
  }

  @Override
  public void onResume() {
    super.onResume();
    loadYears();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    DBHelper.closeDB(mDB);
  }

  private void loadYears() {
    yearlyStats.clear();
    String sql = "SELECT * FROM " + Constants.DB.YEARLY_STATS.TABLE + 
                 " ORDER BY " + Constants.DB.YEARLY_STATS.YEAR + " DESC";
    
    try (Cursor cursor = mDB.rawQuery(sql, null)) {
      while (cursor.moveToNext()) {
        yearlyStats.add(new YearlyStatsEntity(cursor));
      }
    }
    adapter.notifyDataSetChanged();
  }

  @Override
  public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
    if (position < yearlyStats.size()) {
      YearlyStatsEntity stats = yearlyStats.get(position);
      Intent intent = new Intent(requireContext(), StatisticsDetailActivity.class);
      intent.putExtra("YEAR", stats.getYear());
      startActivity(intent);
    }
  }


  /**
   * Adapter for displaying yearly statistics list.
   */
  class StatisticsListAdapter extends BaseAdapter {
    private final LayoutInflater inflater;

    StatisticsListAdapter(Context context) {
      inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
      return yearlyStats.size();
    }

    @Override
    public Object getItem(int position) {
      return yearlyStats.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        convertView = inflater.inflate(R.layout.statistics_row, parent, false);
      }
      
      YearlyStatsEntity stats = yearlyStats.get(position);
      
      TextView yearText = convertView.findViewById(R.id.year_text);
      TextView statsSummaryText = convertView.findViewById(R.id.stats_summary_text);
      
      // Year
      yearText.setText(String.valueOf(stats.getYear()));
      
      // Summary stats
      if (stats.getTotalDistance() != null && stats.getAvgPace() != null && stats.getRunCount() != null) {
        String distanceStr = formatter.formatDistance(Formatter.Format.TXT_SHORT, stats.getTotalDistance().longValue());
        String paceStr = formatter.formatPace(Formatter.Format.TXT_SHORT, stats.getAvgPace() / 1000.0); // Convert to seconds per meter
        String summary = distanceStr + " • " + paceStr + " • " + stats.getRunCount() + " runs";
        statsSummaryText.setText(summary);
      } else {
        statsSummaryText.setText("");
      }
      
      return convertView;
    }
  }
}
