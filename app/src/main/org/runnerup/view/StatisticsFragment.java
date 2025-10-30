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
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;

public class StatisticsFragment extends Fragment
    implements Constants, OnItemClickListener {

  private SQLiteDatabase mDB = null;
  private StatisticsGridAdapter adapter = null;

  // Categories for the grid
  private static final int[] CATEGORY_ICONS = {
    R.drawable.ic_tab_main_24dp, // VS icon (placeholder)
    R.drawable.ic_tab_history_24dp, // Calendar icon (placeholder)
    R.drawable.ic_heartrate_white_24dp, // Heart icon
    R.drawable.ic_tab_besttimes_24dp, // Line chart icon (placeholder)
    R.drawable.ic_bell_curve_24dp, // Bell curve icon
  };

  private static final int[] CATEGORY_LABELS = {
    R.string.statistics_monthly_comparison,
    R.string.statistics_year_month_breakdown,
    R.string.statistics_hr_zones,
    R.string.statistics_yearly_progress,
    R.string.statistics_distribution,
  };

  public StatisticsFragment() {
    super(R.layout.statistics);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    GridView gridView = view.findViewById(R.id.statistics_grid);

    Context context = requireContext();

    mDB = DBHelper.getWritableDatabase(context);
    gridView.setOnItemClickListener(this);
    adapter = new StatisticsGridAdapter(context);
    gridView.setAdapter(adapter);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    DBHelper.closeDB(mDB);
  }

  @Override
  public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
    Intent intent;
    switch (position) {
      case 0: // Monthly Comparison
        intent = new Intent(requireContext(), MonthlyComparisonActivity.class);
        startActivity(intent);
        break;
      case 1: // Year/Month Breakdown
        // Show yearly stats list in a new activity
        intent = new Intent(requireContext(), YearlyStatsActivity.class);
        startActivity(intent);
        break;
      case 2: // HR Zones
        intent = new Intent(requireContext(), HRZoneActivity.class);
        startActivity(intent);
        break;
      case 3: // Yearly Progress
        intent = new Intent(requireContext(), YearlyCumulativeActivity.class);
        startActivity(intent);
        break;
      case 4: // Distribution
        intent = new Intent(requireContext(), DistributionActivity.class);
        startActivity(intent);
        break;
      default:
        break;
    }
  }

  /**
   * Adapter for displaying statistics grid.
   */
  class StatisticsGridAdapter extends BaseAdapter {
    private final LayoutInflater inflater;

    StatisticsGridAdapter(Context context) {
      inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
      return CATEGORY_ICONS.length;
    }

    @Override
    public Object getItem(int position) {
      return position;
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        convertView = inflater.inflate(R.layout.statistics_card, parent, false);
      }
      
      ImageView iconView = convertView.findViewById(R.id.statistics_card_icon);
      TextView labelView = convertView.findViewById(R.id.statistics_card_label);
      
      iconView.setImageResource(CATEGORY_ICONS[position]);
      labelView.setText(CATEGORY_LABELS[position]);
      
      return convertView;
    }
  }
}