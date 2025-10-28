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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.db.YearlyCumulativeCalculator;
import org.runnerup.util.ViewUtil;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class YearlyCumulativeActivity extends AppCompatActivity {

  private static final String TAG = "YearlyCumulativeAct";
  private SQLiteDatabase mDB = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.yearly_cumulative);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(R.string.yearly_progress_title);
    }

    // Initialize database
    mDB = DBHelper.getReadableDatabase(this);

    // Apply system bars insets to avoid UI overlap
    ViewUtil.Insets(findViewById(R.id.yearly_cumulative_root), true);

    // Load and display cumulative data
    loadCumulativeData();
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

  private void loadCumulativeData() {
    // Force recomputation for debugging
    Log.d(TAG, "Forcing yearly cumulative recomputation...");
    YearlyCumulativeCalculator.computeCumulative(mDB);

    // Get current year
    Calendar cal = Calendar.getInstance();
    int currentYear = cal.get(Calendar.YEAR);
    int lastYear = currentYear - 1;

    // Update year labels
    TextView labelCurrentYear = findViewById(R.id.label_current_year);
    TextView labelLastYear = findViewById(R.id.label_last_year);
    labelCurrentYear.setText(String.valueOf(currentYear));
    labelLastYear.setText(String.valueOf(lastYear));

    // Load data for both years
    List<DataPoint> currentYearData = loadYearData(currentYear);
    List<DataPoint> lastYearData = loadYearData(lastYear);

    // Filter current year data to only include days up to today
    Calendar calToday = Calendar.getInstance();
    int currentDayOfYear = calToday.get(Calendar.DAY_OF_YEAR);
    currentYearData = filterDataUpToDay(currentYearData, currentDayOfYear);
    Log.d(TAG, "Filtered current year data to " + currentYearData.size() + " points (up to day " + currentDayOfYear + ")");

    // Create and configure the graph
    GraphView graph = findViewById(R.id.graph);
    
    // Create series for current year
    LineGraphSeries<DataPoint> currentYearSeries = new LineGraphSeries<>(currentYearData.toArray(new DataPoint[0]));
    currentYearSeries.setColor(Color.RED);
    currentYearSeries.setTitle(String.valueOf(currentYear));
    currentYearSeries.setDrawDataPoints(true);
    currentYearSeries.setDataPointsRadius(5);

    // Create series for last year
    LineGraphSeries<DataPoint> lastYearSeries = new LineGraphSeries<>(lastYearData.toArray(new DataPoint[0]));
    lastYearSeries.setColor(Color.BLUE);
    lastYearSeries.setTitle(String.valueOf(lastYear));
    lastYearSeries.setDrawDataPoints(true);
    lastYearSeries.setDataPointsRadius(5);

    // Add series to graph
    graph.addSeries(currentYearSeries);
    graph.addSeries(lastYearSeries);

    // Configure graph
    graph.getViewport().setXAxisBoundsManual(true);
    graph.getViewport().setYAxisBoundsManual(true);
    
    // Set axis bounds based on data
    if (!currentYearData.isEmpty() || !lastYearData.isEmpty()) {
      double minX = Double.MAX_VALUE;
      double maxX = Double.MIN_VALUE;
      double minY = 0;
      double maxY = Double.MIN_VALUE;
      
      for (DataPoint point : currentYearData) {
        minX = Math.min(minX, point.getX());
        maxX = Math.max(maxX, point.getX());
        maxY = Math.max(maxY, point.getY());
      }
      
      for (DataPoint point : lastYearData) {
        minX = Math.min(minX, point.getX());
        maxX = Math.max(maxX, point.getX());
        maxY = Math.max(maxY, point.getY());
      }
      
      graph.getViewport().setMinX(minX);
      graph.getViewport().setMaxX(maxX);
      graph.getViewport().setMinY(minY);
      graph.getViewport().setMaxY(maxY * 1.1); // Add 10% padding
    }

    // Set axis labels
    graph.getGridLabelRenderer().setNumHorizontalLabels(12); // Every 30 days = 365/30 ≈ 12
    graph.getGridLabelRenderer().setNumVerticalLabels(6);
    graph.getGridLabelRenderer().setHumanRounding(false);
    
    // Hide X-axis labels
    graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
    
    // Set axis title
    graph.getGridLabelRenderer().setHorizontalAxisTitle("Day of Year");
    graph.getGridLabelRenderer().setVerticalAxisTitle("Cumulative KM");
  }

  private List<DataPoint> loadYearData(int year) {
    List<DataPoint> dataPoints = new ArrayList<>();
    
    String sql = "SELECT " + Constants.DB.YEARLY_CUMULATIVE.DATE + ", " + 
                 Constants.DB.YEARLY_CUMULATIVE.CUMULATIVE_KM +
                 " FROM " + Constants.DB.YEARLY_CUMULATIVE.TABLE +
                 " WHERE " + Constants.DB.YEARLY_CUMULATIVE.YEAR + " = ?" +
                 " ORDER BY " + Constants.DB.YEARLY_CUMULATIVE.DATE;

    try (Cursor cursor = mDB.rawQuery(sql, new String[]{String.valueOf(year)})) {
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
      
      while (cursor.moveToNext()) {
        String dateStr = cursor.getString(0);
        double cumulativeKm = cursor.getDouble(1);
        
        try {
          Date date = dateFormat.parse(dateStr);
          if (date != null) {
            // Calculate day of year (1-365)
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            int dayOfYear = cal.get(Calendar.DAY_OF_YEAR);
            
            // Use day of year as X coordinate
            dataPoints.add(new DataPoint(dayOfYear, cumulativeKm));
          }
        } catch (Exception e) {
          Log.e(TAG, "Error parsing date: " + dateStr, e);
        }
      }
    }
    
    Log.d(TAG, "Loaded " + dataPoints.size() + " data points for year " + year);
    return dataPoints;
  }

  private List<DataPoint> filterDataUpToDay(List<DataPoint> dataPoints, int maxDay) {
    List<DataPoint> filtered = new ArrayList<>();
    for (DataPoint point : dataPoints) {
      if (point.getX() <= maxDay) {
        filtered.add(point);
      }
    }
    return filtered;
  }
}
