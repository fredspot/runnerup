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

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.data.DBHelper;
import org.runnerup.data.entities.YearlyStatsEntity;
import org.runnerup.core.util.CardPressHelper;
import org.runnerup.core.util.Formatter;
import java.util.ArrayList;
import java.util.List;

public class YearlyStatsActivity extends AppCompatActivity implements Constants {

  private SQLiteDatabase mDB = null;
  private YearlyStatsListAdapter adapter = null;
  private List<YearlyStatsEntity> yearlyStats = new ArrayList<>();
  private Formatter formatter = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.statistics_detail);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(getString(R.string.statistics_year_month_breakdown));
    }

    mDB = DBHelper.getReadableDatabase(this);
    formatter = new Formatter(this);

    RecyclerView recyclerView = findViewById(R.id.statistics_detail_list);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    int spacingPx = (int) (16 * getResources().getDisplayMetrics().density);
    recyclerView.addItemDecoration(new CardSpacingDecoration(spacingPx));
    adapter = new YearlyStatsListAdapter();
    recyclerView.setAdapter(adapter);
    adapter.setOnItemClickListener(
        stats -> {
          Intent intent = new Intent(this, StatisticsDetailActivity.class);
          intent.putExtra("YEAR", stats.getYear());
          startActivity(intent);
        });

    View rootView = findViewById(R.id.statistics_detail_layout);
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

    loadYears();
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

  private void loadYears() {
    yearlyStats.clear();
    String sql =
        "SELECT * FROM "
            + Constants.DB.YEARLY_STATS.TABLE
            + " ORDER BY "
            + Constants.DB.YEARLY_STATS.YEAR
            + " DESC";

    try (Cursor cursor = mDB.rawQuery(sql, null)) {
      while (cursor.moveToNext()) {
        yearlyStats.add(new YearlyStatsEntity(cursor));
      }
    }
    adapter.setItems(yearlyStats);
  }

  private static final class CardSpacingDecoration extends RecyclerView.ItemDecoration {
    private final int spacingPx;

    CardSpacingDecoration(int spacingPx) {
      this.spacingPx = spacingPx;
    }

    @Override
    public void getItemOffsets(
        @NonNull Rect outRect,
        @NonNull View view,
        @NonNull RecyclerView parent,
        @NonNull RecyclerView.State state) {
      int position = parent.getChildAdapterPosition(view);
      if (position == RecyclerView.NO_POSITION) return;
      if (position < state.getItemCount() - 1) {
        outRect.bottom = spacingPx;
      }
    }
  }

  class YearlyStatsListAdapter extends RecyclerView.Adapter<YearlyStatsListAdapter.Holder> {
    private final List<YearlyStatsEntity> items = new ArrayList<>();
    private OnItemClickListener onItemClickListener;

    interface OnItemClickListener {
      void onItemClick(YearlyStatsEntity stats);
    }

    void setOnItemClickListener(OnItemClickListener listener) {
      onItemClickListener = listener;
    }

    void setItems(List<YearlyStatsEntity> newItems) {
      items.clear();
      items.addAll(newItems);
      notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
      return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      LayoutInflater inflater = LayoutInflater.from(YearlyStatsActivity.this);
      View view = inflater.inflate(R.layout.statistics_row, parent, false);
      return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
      CardPressHelper.clearPressState(holder.itemView);
      YearlyStatsEntity stats = items.get(position);
      holder.yearText.setText(String.valueOf(stats.getYear()));
      if (stats.getTotalDistance() != null
          && stats.getAvgPace() != null
          && stats.getRunCount() != null) {
        String distanceStr =
            formatter.formatDistance(
                Formatter.Format.TXT_SHORT, stats.getTotalDistance().longValue());
        String paceStr =
            formatter.formatPaceFromSecPerKm(Formatter.Format.TXT_SHORT, stats.getAvgPace());
        String summary =
            distanceStr + " • " + paceStr + " • " + stats.getRunCount() + " runs";
        holder.statsSummaryText.setText(summary);
      } else {
        holder.statsSummaryText.setText("");
      }
      holder.itemView.setOnClickListener(
          v -> {
            if (onItemClickListener != null) {
              onItemClickListener.onItemClick(stats);
            }
          });
    }

    static class Holder extends RecyclerView.ViewHolder {
      final TextView yearText;
      final TextView statsSummaryText;

      Holder(View itemView) {
        super(itemView);
        yearText = itemView.findViewById(R.id.year_text);
        statsSummaryText = itemView.findViewById(R.id.stats_summary_text);
      }
    }
  }
}
