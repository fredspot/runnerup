/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;
import java.util.Calendar;
import java.util.Date;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.ActivityCleaner;
import org.runnerup.db.DBHelper;
import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.util.Formatter;
import org.runnerup.util.SimpleCursorLoader;
import org.runnerup.workout.Sport;

public class HistoryFragment extends Fragment
    implements Constants, OnItemClickListener, LoaderCallbacks<Cursor> {

  private SQLiteDatabase mDB = null;
  private Formatter formatter = null;

  CursorAdapter cursorAdapter = null;
  View fab = null;
  Button filterButton = null;
  int selectedYear = -1;
  int selectedMonth = -1;

  public HistoryFragment() {
    super(R.layout.history);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    ListView listView = view.findViewById(R.id.history_list);
    fab = view.findViewById(R.id.history_add);
    filterButton = view.findViewById(R.id.history_filter);

    Context context = requireContext();
    fab.setOnClickListener(
        v -> {
          Intent i = new Intent(context, ManualActivity.class);
          // TODO: Use the Activity Result API
          startActivityForResult(i, 0);
        });
    
    filterButton.setOnClickListener(v -> {
      if (selectedYear != -1 && selectedMonth != -1) {
        // If filter is active, clear it immediately
        selectedYear = -1;
        selectedMonth = -1;
        LoaderManager.getInstance(this).restartLoader(0, null, this);
        filterButton.setText("Filter");
      } else {
        // Otherwise, show the filter dialog
        showFilterDialog();
      }
    });

    mDB = DBHelper.getReadableDatabase(context);
    formatter = new Formatter(context);
    listView.setDividerHeight(16); // Spacing between cards
    listView.setOnItemClickListener(this);
    cursorAdapter = new HistoryListAdapter(context, null);
    listView.setAdapter(cursorAdapter);

    LoaderManager.getInstance(this).initLoader(0, null, this);
    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

    new ActivityCleaner().conditionalRecompute(mDB);
  }

  @Override
  public void onResume() {
    super.onResume();
    LoaderManager.getInstance(this).restartLoader(0, null, this);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    DBHelper.closeDB(mDB);
  }

  @NonNull
  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    String[] from =
        new String[] {
          "_id", DB.ACTIVITY.START_TIME, DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME, DB.ACTIVITY.SPORT
        };

    // Build where clause with filter
    String whereClause = "deleted == 0";
    if (selectedYear != -1 && selectedMonth != -1) {
      Calendar cal = Calendar.getInstance();
      // selectedMonth should be 0-11 (0=January, 11=December) for Calendar compatibility
      // But handle both formats: if month is 1-12, convert to 0-11
      int calendarMonth = selectedMonth;
      if (selectedMonth >= 1 && selectedMonth <= 12) {
        calendarMonth = selectedMonth - 1;
      }
      cal.set(selectedYear, calendarMonth, 1, 0, 0, 0);
      cal.set(Calendar.MILLISECOND, 0);
      long startTime = cal.getTimeInMillis() / 1000;
      
      cal.add(Calendar.MONTH, 1);
      long endTime = cal.getTimeInMillis() / 1000;
      
      whereClause += " AND " + DB.ACTIVITY.START_TIME + " >= " + startTime + 
                     " AND " + DB.ACTIVITY.START_TIME + " < " + endTime;
    }

    return new SimpleCursorLoader(
        requireContext(),
        mDB,
        DB.ACTIVITY.TABLE,
        from,
        whereClause,
        null,
        DB.ACTIVITY.START_TIME + " desc");
  }
  
  /**
   * Apply filter for a specific year and month.
   * This method is called externally (e.g., from MainLayout) to set filters programmatically.
   */
  public void applyFilter(int year, int month) {
    selectedYear = year;
    selectedMonth = month;
    
    // Update filter button text
    if (filterButton != null) {
      filterButton.setText("Clear");
    }
    
    // Restart loader to apply filter
    LoaderManager.getInstance(this).restartLoader(0, null, this);
  }

  private void showFilterDialog() {
    View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.filter_dialog, null);
    NumberPicker yearPicker = dialogView.findViewById(R.id.year_picker);
    NumberPicker monthPicker = dialogView.findViewById(R.id.month_picker);
    
    // Set up year picker (last 10 years)
    Calendar cal = Calendar.getInstance();
    int currentYear = cal.get(Calendar.YEAR);
    String[] years = new String[11];
    for (int i = 0; i <= 10; i++) {
      years[i] = String.valueOf(currentYear - i);
    }
    yearPicker.setMinValue(0);
    yearPicker.setMaxValue(10);
    yearPicker.setDisplayedValues(years);
    
    // Set up month picker
    String[] months = new String[]{"Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    monthPicker.setMinValue(0);
    monthPicker.setMaxValue(11);
    monthPicker.setDisplayedValues(months);
    
    // Set current values if filter is active
    if (selectedYear != -1 && selectedMonth != -1) {
      for (int i = 0; i < years.length; i++) {
        if (Integer.parseInt(years[i]) == selectedYear) {
          yearPicker.setValue(i);
          break;
        }
      }
      monthPicker.setValue(selectedMonth);
    }
    
    AlertDialog dialog = new AlertDialog.Builder(requireContext())
        .setView(dialogView)
        .setTitle("Filter by Month")
        .setPositiveButton("Apply", (d, which) -> {
          selectedYear = Integer.parseInt(years[yearPicker.getValue()]);
          selectedMonth = monthPicker.getValue();
          LoaderManager.getInstance(this).restartLoader(0, null, this);
          filterButton.setText("Clear");
        })
        .setNeutralButton("Clear", (d, which) -> {
          selectedYear = -1;
          selectedMonth = -1;
          LoaderManager.getInstance(this).restartLoader(0, null, this);
          filterButton.setText("Filter");
        })
        .setNegativeButton("Cancel", null)
        .create();
    
    dialog.show();
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> arg0, Cursor arg1) {
    cursorAdapter.swapCursor(arg1);
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> arg0) {
    cursorAdapter.swapCursor(null);
  }

  @Override
  public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
    Intent intent = new Intent(requireContext(), DetailActivity.class);
    intent.putExtra("ID", id);
    intent.putExtra("mode", "details");
    intent.putExtra("source_tab", 1); // History tab is at position 1
    startActivityForResult(intent, 0);
  }

  // TODO: Use Activity Result API
  @Override
  public void onActivityResult(int arg0, int arg1, Intent arg2) {
    super.onActivityResult(arg0, arg1, arg2);
    LoaderManager.getInstance(this).restartLoader(0, null, this);
  }

  class HistoryListAdapter extends CursorAdapter {
    final LayoutInflater inflater;

    HistoryListAdapter(Context context, Cursor c) {
      super(context, c, true);
      inflater = LayoutInflater.from(context);
    }

    private boolean sameMonthAsPrevious(int curYear, int curMonth, Cursor cursor) {
      int curPosition = cursor.getPosition();
      if (curPosition == 0) return false;

      cursor.moveToPrevious();
      long prevTimeInSecs = new ActivityEntity(cursor).getStartTime();

      Calendar prevCal = Calendar.getInstance();
      prevCal.setTime(new Date(prevTimeInSecs * 1000));
      return prevCal.get(Calendar.YEAR) == curYear && prevCal.get(Calendar.MONTH) == curMonth;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      ActivityEntity ae = new ActivityEntity(cursor);

      // month + day
      Date curDate = new Date(ae.getStartTime() * 1000);
      Calendar cal = Calendar.getInstance();
      cal.setTime(curDate);

      TextView sectionTitle = view.findViewById(R.id.history_section_title);
      int year = cal.get(Calendar.YEAR);
      int month = cal.get(Calendar.MONTH);
      if (sameMonthAsPrevious(year, month, cursor)) {
        sectionTitle.setVisibility(View.GONE);
      } else {
        sectionTitle.setVisibility(View.VISIBLE);
        sectionTitle.setText(formatter.formatMonth(curDate));
        // No need to set margins anymore, the layout handles it
      }

      TextView dateText = view.findViewById(R.id.history_list_date);
      dateText.setText(formatter.formatDateTime(ae.getStartTime()));

      // distance
      Double d = ae.getDistance();
      TextView distanceText = view.findViewById(R.id.history_list_distance);
      if (d != null) {
        distanceText.setText(formatter.formatDistance(Formatter.Format.TXT_SHORT, d.longValue()));
      } else {
        distanceText.setText("");
      }

      // sport + additional info
      Integer s = ae.getSport();
      ImageView emblem = view.findViewById(R.id.history_list_emblem);
      TextView additionalInfo = view.findViewById(R.id.history_list_additional);

      int sportColor = ContextCompat.getColor(context, Sport.colorOf(s));
      Drawable sportDrawable =
          AppCompatResources.getDrawable(context, Sport.drawableColored16Of(s));
      emblem.setImageDrawable(sportDrawable);
      distanceText.setTextColor(sportColor);
      additionalInfo.setTextColor(sportColor);
      Integer hr = ae.getAvgHr();
      if (hr != null) {
        additionalInfo.setText(formatter.formatHeartRate(Formatter.Format.TXT_SHORT, hr));
      } else {
        additionalInfo.setText(null);
      }

      // duration
      Long dur = ae.getTime();
      TextView durationText = view.findViewById(R.id.history_list_duration);
      if (dur != null) {
        durationText.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, dur));
      } else {
        durationText.setText("");
      }

      // pace
      TextView paceText = view.findViewById(R.id.history_list_pace);
      String paceTextContents = "";
      if (d != null && dur != null && dur != 0) {
        paceTextContents =
            formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, d / dur);
      }
      paceText.setText(paceTextContents);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      return inflater.inflate(R.layout.history_row, parent, false);
    }
  }
}
