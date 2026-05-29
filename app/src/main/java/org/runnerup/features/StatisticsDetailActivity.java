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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import org.runnerup.data.entities.MonthlyStatsEntity;
import org.runnerup.core.util.CardPressHelper;
import org.runnerup.core.util.Formatter;

public class StatisticsDetailActivity extends AppCompatActivity implements Constants {

  private ActivityResultLauncher<Intent> monthWeekLauncher;

  private int targetYear = 0;
  private SQLiteDatabase mDB = null;
  private Formatter formatter = null;
  private StatisticsListAdapter adapter = null;

  private static final String[] MONTH_NAMES = {
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.statistics_detail);

    targetYear = getIntent().getIntExtra("YEAR", 2024);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(String.valueOf(targetYear) + " - " + getString(R.string.statistics));
    }

    mDB = DBHelper.getReadableDatabase(this);
    formatter = new Formatter(this);

    monthWeekLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK) {
                finish();
              }
            });

    RecyclerView recyclerView = findViewById(R.id.statistics_detail_list);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    int spacingPx = (int) (16 * getResources().getDisplayMetrics().density);
    recyclerView.addItemDecoration(new CardSpacingDecoration(spacingPx));
    adapter = new StatisticsListAdapter();
    recyclerView.setAdapter(adapter);
    adapter.setOnItemClickListener(
        stats -> {
          if (stats != null && stats.getMonth() != null) {
            Intent intent = new Intent(this, MonthWeekBreakdownActivity.class);
            intent.putExtra(MonthWeekBreakdownActivity.EXTRA_YEAR, targetYear);
            intent.putExtra(MonthWeekBreakdownActivity.EXTRA_MONTH, stats.getMonth());
            monthWeekLauncher.launch(intent);
          }
        });

    View rootView = findViewById(R.id.statistics_detail_layout);
    ViewCompat.setOnApplyWindowInsetsListener(rootView, new OnApplyWindowInsetsListener() {
      @NonNull
      @Override
      public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
        Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        mlp.topMargin = insets.top;
        return WindowInsetsCompat.CONSUMED;
      }
    });

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

  private void loadMonthlyStats() {
    String sql = "SELECT * FROM " + Constants.DB.MONTHLY_STATS.TABLE +
                 " WHERE " + Constants.DB.MONTHLY_STATS.YEAR + " = ?" +
                 " ORDER BY " + Constants.DB.MONTHLY_STATS.MONTH;

    Cursor cursor = mDB.rawQuery(sql, new String[]{String.valueOf(targetYear)});
    adapter.swapCursor(cursor);
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

  class StatisticsListAdapter extends RecyclerView.Adapter<StatisticsListAdapter.Holder> {
    private Cursor cursor;
    private OnItemClickListener onItemClickListener;

    interface OnItemClickListener {
      void onItemClick(MonthlyStatsEntity stats);
    }

    void setOnItemClickListener(OnItemClickListener listener) {
      onItemClickListener = listener;
    }

    public void swapCursor(Cursor newCursor) {
      if (cursor != null) {
        cursor.close();
      }
      cursor = newCursor;
      notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
      return cursor != null ? cursor.getCount() : 0;
    }

    MonthlyStatsEntity getItem(int position) {
      if (cursor != null && cursor.moveToPosition(position)) {
        return new MonthlyStatsEntity(cursor);
      }
      return null;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      LayoutInflater inflater = LayoutInflater.from(StatisticsDetailActivity.this);
      View view = inflater.inflate(R.layout.statistics_detail_row, parent, false);
      return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
      CardPressHelper.clearPressState(holder.itemView);
      MonthlyStatsEntity stats = getItem(position);
      if (stats == null) {
        return;
      }

      if (stats.getMonth() != null && stats.getMonth() >= 1 && stats.getMonth() <= 12) {
        holder.monthText.setText(MONTH_NAMES[stats.getMonth() - 1]);
      } else {
        holder.monthText.setText("Month " + stats.getMonth());
      }

      if (stats.getRunCount() != null) {
        holder.runCountText.setText(stats.getRunCount() + " runs");
      } else {
        holder.runCountText.setText("0 runs");
      }

      if (stats.getTotalDistance() != null) {
        holder.totalDistanceText.setText(
            formatter.formatDistance(Formatter.Format.TXT_SHORT, stats.getTotalDistance().longValue()));
      } else {
        holder.totalDistanceText.setText("-");
      }

      if (stats.getAvgPace() != null) {
        holder.avgPaceText.setText(
            formatter.formatPaceFromSecPerKm(Formatter.Format.TXT_SHORT, stats.getAvgPace()));
      } else {
        holder.avgPaceText.setText("-");
      }

      if (stats.getAvgRunLength() != null) {
        holder.avgRunLengthText.setText(
            formatter.formatDistance(Formatter.Format.TXT_SHORT, stats.getAvgRunLength().longValue()));
      } else {
        holder.avgRunLengthText.setText("-");
      }

      holder.itemView.setOnClickListener(
          v -> {
            if (onItemClickListener != null) {
              onItemClickListener.onItemClick(stats);
            }
          });
    }

    static class Holder extends RecyclerView.ViewHolder {
      final TextView monthText;
      final TextView runCountText;
      final TextView totalDistanceText;
      final TextView avgPaceText;
      final TextView avgRunLengthText;

      Holder(View itemView) {
        super(itemView);
        monthText = itemView.findViewById(R.id.month_text);
        runCountText = itemView.findViewById(R.id.run_count_text);
        totalDistanceText = itemView.findViewById(R.id.total_distance_text);
        avgPaceText = itemView.findViewById(R.id.avg_pace_text);
        avgRunLengthText = itemView.findViewById(R.id.avg_run_length_text);
      }
    }
  }
}
