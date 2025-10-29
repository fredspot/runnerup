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
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
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
import android.widget.TextView;

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

    // Handle window insets for proper spacing
    View rootView = findViewById(R.id.yearly_cumulative_root);
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
    
    // Calculate total KM for each year (last data point value)
    // Database stores in meters, so divide by 1000 to get km
    double currentYearTotalKm = currentYearData.isEmpty() ? 0 : currentYearData.get(currentYearData.size() - 1).getY() / 1000.0;
    double lastYearTotalKm = lastYearData.isEmpty() ? 0 : lastYearData.get(lastYearData.size() - 1).getY() / 1000.0;
    
    // Create series for current year - use accent blue
    int currentYearColor = ContextCompat.getColor(this, R.color.colorAccent);
    LineGraphSeries<DataPoint> currentYearSeries = new LineGraphSeries<>(currentYearData.toArray(new DataPoint[0]));
    currentYearSeries.setColor(currentYearColor);
    currentYearSeries.setTitle(String.valueOf(currentYear));
    currentYearSeries.setDrawDataPoints(false); // No dots, just lines
    currentYearSeries.setThickness(4);
    currentYearSeries.setDrawBackground(true);
    currentYearSeries.setBackgroundColor(Color.argb(15, Color.red(currentYearColor), Color.green(currentYearColor), Color.blue(currentYearColor)));

    // Create series for last year - use grey
    int lastYearColor = ContextCompat.getColor(this, R.color.colorTextSecondary);
    LineGraphSeries<DataPoint> lastYearSeries = new LineGraphSeries<>(lastYearData.toArray(new DataPoint[0]));
    lastYearSeries.setColor(lastYearColor);
    lastYearSeries.setTitle(String.valueOf(lastYear));
    lastYearSeries.setDrawDataPoints(false); // No dots, just lines
    lastYearSeries.setThickness(3);
    lastYearSeries.setDrawBackground(true);
    lastYearSeries.setBackgroundColor(Color.argb(10, Color.red(lastYearColor), Color.green(lastYearColor), Color.blue(lastYearColor)));

    // Add series to graph
    graph.addSeries(currentYearSeries);
    graph.addSeries(lastYearSeries);

    // Configure graph
    graph.getViewport().setXAxisBoundsManual(true);
    graph.getViewport().setYAxisBoundsManual(true);
    
    // Set axis bounds based on data
    double minX = Double.MAX_VALUE;
    double maxX = Double.MIN_VALUE;
    double minY = 0;
    double maxY = Double.MIN_VALUE;
    double currentYearLastY = 0;
    double lastYearLastY = 0;
    double viewportMaxY = 0;
    
    if (!currentYearData.isEmpty() || !lastYearData.isEmpty()) {
      for (DataPoint point : currentYearData) {
        minX = Math.min(minX, point.getX());
        maxX = Math.max(maxX, point.getX());
        maxY = Math.max(maxY, point.getY());
      }
      
      // Get last Y value for current year (for label positioning)
      if (!currentYearData.isEmpty()) {
        currentYearLastY = currentYearData.get(currentYearData.size() - 1).getY();
      }
      
      for (DataPoint point : lastYearData) {
        minX = Math.min(minX, point.getX());
        maxX = Math.max(maxX, point.getX());
        maxY = Math.max(maxY, point.getY());
      }
      
      // Get last Y value for last year (for label positioning)
      if (!lastYearData.isEmpty()) {
        lastYearLastY = lastYearData.get(lastYearData.size() - 1).getY();
      }
      
      graph.getViewport().setMinX(minX);
      graph.getViewport().setMaxX(maxX);
      graph.getViewport().setMinY(minY);
      viewportMaxY = maxY * 1.15; // Add 15% padding for labels
      graph.getViewport().setMaxY(viewportMaxY);
    }

    // Configure graph styling for dark theme - hide all axes, grids, and labels
    GridLabelRenderer renderer = graph.getGridLabelRenderer();
    
    // Hide all labels and grids - use only available API methods
    renderer.setHorizontalLabelsVisible(false);
    renderer.setVerticalLabelsVisible(false);
    renderer.setGridStyle(GridLabelRenderer.GridStyle.NONE);
    // Set label count to 0 to completely hide them
    renderer.setNumHorizontalLabels(0);
    renderer.setNumVerticalLabels(0);
    // Hide axis titles by setting them to empty strings
    renderer.setHorizontalAxisTitle("");
    renderer.setVerticalAxisTitle("");
    
    // Set graph background transparent (card background will show)
    graph.setBackgroundColor(Color.TRANSPARENT);
    
    // Add text views to show total KM at the end of each line
    TextView currentYearTotalView = findViewById(R.id.current_year_total);
    TextView lastYearTotalView = findViewById(R.id.last_year_total);
    final double finalViewportMaxY = viewportMaxY;
    final double finalCurrentYearLastY = currentYearLastY;
    final double finalLastYearLastY = lastYearLastY;
    
    if (currentYearTotalView != null) {
      currentYearTotalView.setText(String.format(Locale.getDefault(), "%.1f km", currentYearTotalKm));
      currentYearTotalView.setTextColor(currentYearColor);
      currentYearTotalView.setVisibility(View.VISIBLE);
      
      // Position at the Y value of the last data point (top of current year line)
      if (finalCurrentYearLastY > 0 && finalViewportMaxY > 0) {
        currentYearTotalView.post(() -> {
          ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) currentYearTotalView.getLayoutParams();
          // Invert because bias 0 = top, 1 = bottom. Higher Y values should be near top (low bias)
          float bias = (float) (1.0 - (finalCurrentYearLastY / finalViewportMaxY));
          params.verticalBias = Math.max(0.05f, Math.min(0.95f, bias)); // Clamp between 5% and 95%
          currentYearTotalView.setLayoutParams(params);
        });
      }
    }
    
    if (lastYearTotalView != null && lastYearTotalKm > 0) {
      lastYearTotalView.setText(String.format(Locale.getDefault(), "%.1f km", lastYearTotalKm));
      lastYearTotalView.setTextColor(lastYearColor);
      lastYearTotalView.setVisibility(View.VISIBLE);
      
      // Position at the Y value of the last data point (top of last year line)
      if (finalLastYearLastY > 0 && finalViewportMaxY > 0) {
        lastYearTotalView.post(() -> {
          ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) lastYearTotalView.getLayoutParams();
          // Invert because bias 0 = top, 1 = bottom. Higher Y values should be near top (low bias)
          // Position it slightly above the line for better visibility (subtract offset)
          float normalizedY = (float) (finalLastYearLastY / finalViewportMaxY);
          float bias = (float) (1.0 - normalizedY - 0.03); // Subtract 0.03 to move it up more
          params.verticalBias = Math.max(0.05f, Math.min(0.95f, bias)); // Clamp between 5% and 95%
          lastYearTotalView.setLayoutParams(params);
        });
      }
    }
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
