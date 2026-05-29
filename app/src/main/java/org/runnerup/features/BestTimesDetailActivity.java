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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.data.BestTimesDistances;
import org.runnerup.data.DBHelper;
import org.runnerup.data.entities.BestTimesEntity;
import org.runnerup.core.util.Formatter;

public class BestTimesDetailActivity extends AppCompatActivity implements Constants {

  private int targetDistance = 0;
  private SQLiteDatabase mDB = null;
  private Formatter formatter = null;
  private BestTimesListAdapter adapter = null;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.best_times_detail);

    targetDistance = getIntent().getIntExtra("DISTANCE", 1000);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(getDistanceLabel(targetDistance) + " - " + getString(R.string.best_times));
    }

    mDB = DBHelper.getReadableDatabase(this);
    formatter = new Formatter(this);

    RecyclerView recyclerView = findViewById(R.id.best_times_detail_list);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    int spacingPx = (int) (16 * getResources().getDisplayMetrics().density);
    recyclerView.addItemDecoration(new CardSpacingDecoration(spacingPx));
    adapter = new BestTimesListAdapter();
    recyclerView.setAdapter(adapter);
    adapter.setOnItemClickListener(
        bestTime -> {
          if (bestTime != null && bestTime.getActivityId() != null) {
            Intent intent = new Intent(this, DetailActivity.class);
            intent.putExtra("ID", bestTime.getActivityId());
            intent.putExtra("mode", "details");
            startActivity(intent);
          }
        });

    View rootView = findViewById(R.id.best_times_detail_layout);
    ViewCompat.setOnApplyWindowInsetsListener(
        rootView,
        new OnApplyWindowInsetsListener() {
          @NonNull
          @Override
          public WindowInsetsCompat onApplyWindowInsets(
              @NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, insets.bottom);

            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.topMargin = insets.top;
            return WindowInsetsCompat.CONSUMED;
          }
        });

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

  private void loadBestTimes() {
    String sql = "SELECT * FROM " + Constants.DB.BEST_TIMES.TABLE +
                 " WHERE " + Constants.DB.BEST_TIMES.DISTANCE + " = ?" +
                 " ORDER BY " + Constants.DB.BEST_TIMES.RANK;

    Cursor cursor = mDB.rawQuery(sql, new String[]{String.valueOf(targetDistance)});
    adapter.swapCursor(cursor);
  }

  private String getDistanceLabel(int distance) {
    return BestTimesDistances.getLabel(distance);
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

  class BestTimesListAdapter extends RecyclerView.Adapter<BestTimesListAdapter.Holder> {
    private Cursor cursor;
    private OnItemClickListener onItemClickListener;

    interface OnItemClickListener {
      void onItemClick(BestTimesEntity bestTime);
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

    BestTimesEntity getItem(int position) {
      if (cursor != null && cursor.moveToPosition(position)) {
        return new BestTimesEntity(cursor);
      }
      return null;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      LayoutInflater inflater = LayoutInflater.from(BestTimesDetailActivity.this);
      View view = inflater.inflate(R.layout.best_times_detail_row, parent, false);
      return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
      BestTimesEntity bestTime = getItem(position);
      if (bestTime == null) {
        return;
      }

      holder.rankText.setText("#" + bestTime.getRank());

      if (bestTime.getTime() != null) {
        long timeInSeconds = bestTime.getTime() / 1000;
        holder.timeText.setText(
            formatter.formatElapsedTime(Formatter.Format.TXT_LONG, timeInSeconds));
      }

      if (bestTime.getPace() != null) {
        holder.paceText.setText(
            formatter.formatPaceFromSecPerKm(Formatter.Format.TXT_LONG, bestTime.getPace()));
      }

      if (bestTime.getStartTime() != null) {
        Date date = new Date(bestTime.getStartTime() * 1000);
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        holder.dateText.setText(dateFormat.format(date));
      }

      holder.hrText.setText(
          formatter.formatBestTimesHeartRateLine(bestTime.getAvgHr(), bestTime.getMaxHr()));

      holder.itemView.setOnClickListener(
          v -> {
            if (onItemClickListener != null) {
              onItemClickListener.onItemClick(bestTime);
            }
          });
    }

    static class Holder extends RecyclerView.ViewHolder {
      final TextView rankText;
      final TextView timeText;
      final TextView paceText;
      final TextView dateText;
      final TextView hrText;

      Holder(View itemView) {
        super(itemView);
        rankText = itemView.findViewById(R.id.rank_text);
        timeText = itemView.findViewById(R.id.time_text);
        paceText = itemView.findViewById(R.id.pace_text);
        dateText = itemView.findViewById(R.id.date_text);
        hrText = itemView.findViewById(R.id.hr_text);
      }
    }
  }
}
