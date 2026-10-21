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

package org.runnerup.core.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Paint;
import org.runnerup.core.util.BgTasks;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.data.entities.LocationEntity;
import org.runnerup.features.HRZonesBar;
import org.runnerup.tracking.elevation.ElevationProfile;
import org.runnerup.core.workout.SpeedUnit;

public class GraphWrapper implements Constants {
  private final GraphView graphView;
  private final GraphView graphView2;
  private final GraphView graphView3;

  private final LinearLayout graphTab;
  private final HRZonesBar hrzonesBar;
  private final Formatter formatter;
  @Nullable private final LinearLayout hrzonesBarLayout;
  private final LoadParam loadParam;
  private volatile int loadGeneration = 0;
  private boolean syncingX = false;

  interface XAxis {
    String label();
    String formatValue(double val);
    double getX(double distance, double time_ms);
  };

  private final XAxis distanceXAxis;
  private final XAxis timeXAxis;
  boolean useDistanceAsX = true;
  private XAxis xAxis;

  /** Called when the activity is first created. */
  @SuppressLint("ObsoleteSdkInt")
  public GraphWrapper(
      Context context,
      LinearLayout graphTab,
      @Nullable LinearLayout hrzonesBarLayout,
      final Formatter formatter,
      SQLiteDatabase mDB,
      long mID,
      boolean use_distance_as_x) {
    this.graphTab = graphTab;
    this.hrzonesBarLayout = hrzonesBarLayout;
    this.formatter = formatter;

    this.distanceXAxis = new XAxis() {
        @Override
        public String label() {
          return context.getString(org.runnerup.common.R.string.Distance);
        }
        @Override
        public String formatValue(double value) {
          return formatGraphAxisDistance(value);
        }
        @Override
        public double getX(double distance, double time_ms) {
          return distance;
        }
      };

    this.timeXAxis = new XAxis() {
        @Override
        public String label() {
          return context.getString(org.runnerup.common.R.string.Time);
        }
        @Override
        public String formatValue(double value) {
          return formatter.formatElapsedTime(Formatter.Format.TXT_SHORT,
                                             (long)value);
        }
        @Override
        public double getX(double distance, double time_ms) {
          return time_ms / 1000;
        }
      };

    this.useDistanceAsX = use_distance_as_x;
    if (use_distance_as_x) {
      this.xAxis = distanceXAxis;
    } else {
      this.xAxis = timeXAxis;
    }

    loadParam = new LoadParam(context, mDB, mID);

    graphView = new GraphView(context);
    graphView.setTitle(
        formatter.formatVelocityLabel() + " (" + formatter.getVelocityUnit(context) + ")");
    graphView
        .getGridLabelRenderer()
        .setLabelFormatter(
            new DefaultLabelFormatter() {
              @Override
              public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                  return xAxis.formatValue(value);
                } else {
                  return formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, value);
                }
              }
            });
    graphView.getGridLabelRenderer().setHorizontalAxisTitle("");
    graphView.getViewport().setScalable(true);
    graphView.getViewport().setScrollable(true);
    configureSharedChartStyle(graphView);

    graphView2 = new GraphView(context);
    graphView2.setTitle(context.getString(org.runnerup.common.R.string.Heart_rate) + " (bpm)");
    configureHeartRateHorizontalAxis(graphView2);
    graphView2
        .getGridLabelRenderer()
        .setLabelFormatter(
            new DefaultLabelFormatter() {
              @Override
              public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                  return xAxis.formatValue(value);
                } else {
                  return formatter.formatHeartRate(Formatter.Format.TXT_SHORT, value);
                }
              }
            });
    graphView2.getViewport().setScalable(true);
    graphView2.getViewport().setScrollable(true);

    graphView3 = new GraphView(context);
    graphView3.setTitle(context.getString(R.string.elevation_graph) + " (m)");
    graphView3.getViewport().setScalable(true);
    graphView3.getViewport().setScrollable(true);
    configureSharedChartStyle(graphView2);
    configureSharedChartStyle(graphView3);
    linkHorizontalAxes();

    hrzonesBar = new HRZonesBar(context);
    loadGraphAsync();
  }

  private int sharedPaddingPx() {
    return (int) (12 * graphView.getContext().getResources().getDisplayMetrics().density + 0.5f);
  }

  /** Same padding and no vertical title, so the plot's left edge is only the Y labels. */
  private void configureSharedChartStyle(GraphView graph) {
    GridLabelRenderer labels = graph.getGridLabelRenderer();
    labels.setVerticalAxisTitle("");
    labels.setPadding(sharedPaddingPx());
  }

  private void linkHorizontalAxes() {
    final GraphView[] charts = new GraphView[] {graphView, graphView2, graphView3};
    for (final GraphView source : charts) {
      source
          .getViewport()
          .setOnXAxisBoundsChangedListener(
              (minX, maxX, reason) -> {
                if (syncingX) {
                  return;
                }
                syncingX = true;
                try {
                  for (GraphView other : charts) {
                    if (other == source) {
                      continue;
                    }
                    other.getViewport().setXAxisBoundsManual(true);
                    other.getViewport().setMinX(minX);
                    other.getViewport().setMaxX(maxX);
                  }
                } finally {
                  syncingX = false;
                }
              });
    }
  }

  /** One Y-label column and one X window so the same moment lines up on every chart. */
  private void alignCharts() {
    GraphView[] charts = new GraphView[] {graphView, graphView2, graphView3};
    int padding = sharedPaddingPx();
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    int maxLabel = 0;
    float density = graphView.getContext().getResources().getDisplayMetrics().density;
    for (GraphView chart : charts) {
      if (chart.getSeries().isEmpty()) {
        continue;
      }
      GridLabelRenderer labels = chart.getGridLabelRenderer();
      labels.setPadding(padding);
      labels.setVerticalAxisTitle("");
      paint.setTextSize(labels.getTextSize());
      boolean manualY = chart.getViewport().isYAxisBoundsManual();
      double minY = chart.getViewport().getMinY(!manualY);
      double maxY = chart.getViewport().getMaxY(!manualY);
      for (int i = 0; i <= 4; i++) {
        double y = minY + (maxY - minY) * i / 4.0;
        String text = labels.getLabelFormatter().formatLabel(y, false);
        if (text != null) {
          maxLabel = Math.max(maxLabel, (int) Math.ceil(paint.measureText(text)));
        }
      }
    }
    if (maxLabel > 0) {
      maxLabel += (int) (8 * density + 0.5f);
      for (GraphView chart : charts) {
        if (chart.getSeries().isEmpty()) {
          continue;
        }
        chart.getGridLabelRenderer().setLabelVerticalWidth(maxLabel);
      }
    }
    pinSharedX(charts);
  }

  private void pinSharedX(GraphView[] charts) {
    double[][] ranges = new double[charts.length][];
    for (int i = 0; i < charts.length; i++) {
      if (charts[i].getSeries().isEmpty()) {
        continue;
      }
      ranges[i] =
          new double[] {
            charts[i].getViewport().getMinX(true), charts[i].getViewport().getMaxX(true)
          };
    }
    double[] shared = GraphAxis.sharedXRange(false, ranges);
    if (shared == null) {
      return;
    }
    syncingX = true;
    try {
      for (GraphView chart : charts) {
        if (chart.getSeries().isEmpty()) {
          continue;
        }
        chart.getViewport().setXAxisBoundsManual(true);
        chart.getViewport().setMinX(shared[0]);
        chart.getViewport().setMaxX(shared[1]);
      }
    } finally {
      syncingX = false;
    }
  }

  /** Whole km/mi on the graph x-axis (axis title already names the unit). */
  private String formatGraphAxisDistance(double meters) {
    return String.valueOf(Math.round(meters / formatter.getUnitMeters()));
  }

  private String getHeartRateHorizontalAxisTitle(Context context) {
    if (useDistanceAsX) {
      String unit = formatter.getDistanceUnit(Formatter.Format.TXT_SHORT);
      if (unit == null || unit.isEmpty()) {
        return "Km";
      }
      return unit.substring(0, 1).toUpperCase(Locale.ROOT) + unit.substring(1);
    }
    return context.getString(org.runnerup.common.R.string.Time);
  }

  private void configureHeartRateHorizontalAxis(GraphView graph) {
    Context context = graph.getContext();
    GridLabelRenderer labels = graph.getGridLabelRenderer();
    labels.setHorizontalAxisTitle(getHeartRateHorizontalAxisTitle(context));
    labels.setPadding(sharedPaddingPx());
    labels.setHorizontalAxisTitleTextSize(labels.getTextSize() * 0.9f);
  }

  /** Fit the HR chart Y axis to the data range so BPM variation is visible. */
  private void applyHeartRateYAxisBounds(GraphView graph, LineGraphSeries<DataPoint> series) {
    applyZoomedYAxisBounds(graph, series, 30, true);
  }

  private void applyZoomedYAxisBounds(
      GraphView graph, LineGraphSeries<DataPoint> series, double minSpan, boolean clampMinToZero) {
    double[] bounds =
        GraphAxis.zoomedBounds(
            series.getLowestValueY(), series.getHighestValueY(), minSpan, clampMinToZero);
    graph.getViewport().setYAxisBoundsManual(true);
    graph.getViewport().setMinY(bounds[0]);
    graph.getViewport().setMaxY(bounds[1]);
    graph.onDataChanged(false, true);
  }

  public void reload() {
    graphView.removeAllSeries();
    graphView2.removeAllSeries();
    graphView3.removeAllSeries();
    loadGraphAsync();
  }

  public void setUseDistanceAsX(boolean val) {
    if (val == useDistanceAsX) {
      return;
    }
    useDistanceAsX = val;
    if (val) {
      xAxis = distanceXAxis;
    } else {
      xAxis = timeXAxis;
    }
    graphView.getGridLabelRenderer().setHorizontalAxisTitle("");
    configureHeartRateHorizontalAxis(graphView2);
    graphView.removeAllSeries();
    graphView2.removeAllSeries();
    graphView3.removeAllSeries();
    loadGraphAsync();
  }

  private void loadGraphAsync() {
    final int gen = ++loadGeneration;
    BgTasks.runDb(() -> produceGraph(loadParam), data -> {
      if (gen != loadGeneration) {
        return;
      }
      applyGraphProducer(data);
    });
  }

  private GraphProducer produceGraph(LoadParam params) {
    LocationEntity.LocationList<LocationEntity> ll =
        new LocationEntity.LocationList<>(params.mDB, params.mID);
    GraphProducer graphData = new GraphProducer(params.context, ll.getCount());
    double lastDistance = 0;
    long lastTime = 0;
    int lastLap = -1;
    Double tot_distance = 0.0;
    Double tot_time = 0.0;
    int cnt = 0;
    for (LocationEntity loc : ll) {
      cnt++;
      Long time = loc.getElapsed();
      time = time != null ? time : lastTime;
      if (time != null) {
        tot_time = time.doubleValue();
      }
      Integer lap = loc.getLap();
      lap = lap != null ? lap : 0;
      tot_distance = tot_distance != null ? loc.getDistance() : lastDistance;

      double tot_X = xAxis.getX(tot_distance, time);
      if (lap != lastLap) {
        graphData.clearSmooth(tot_X);
        lastLap = lap;
      }

      graphData.addObservation(
          time - lastTime, tot_distance - lastDistance, tot_X, loc);
      lastTime = time;
      lastDistance = tot_distance;
    }
    graphData.clearSmooth(xAxis.getX(tot_distance, tot_time));
    graphData.omitWarmupIfNeeded(tot_distance, useDistanceAsX);
    graphData.elevationPoints = loadElevationPoints(params.mDB, params.mID);

    ll.close();
    Log.e(getClass().getName(), "Finished loading " + cnt + " points"
        + " => " + graphData.velocityList.size() + " points");
    return graphData;
  }

  private static ElevationProfile.Series toElevationProfile(List<DataPoint> points) {
    double[] x = new double[points.size()];
    double[] y = new double[points.size()];
    for (int i = 0; i < points.size(); i++) {
      x[i] = points.get(i).getX();
      y[i] = points.get(i).getY();
    }
    return ElevationProfile.prepare(x, y, 200);
  }

  private static double maxValue(double[] values) {
    double max = values[0];
    for (double v : values) {
      if (v > max) {
        max = v;
      }
    }
    return max;
  }

  /** GraphView requires strictly increasing X values. */
  static List<DataPoint> uniqueIncreasingX(List<DataPoint> points) {
    List<DataPoint> out = new ArrayList<>();
    Double lastX = null;
    for (DataPoint p : points) {
      if (lastX != null && p.getX() <= lastX) {
        continue;
      }
      out.add(p);
      lastX = p.getX();
    }
    return out;
  }

  private List<DataPoint> loadElevationPoints(SQLiteDatabase db, long activityId) {
    List<DataPoint> topo = queryElevationPoints(db, activityId, true);
    if (topo.size() >= 2) {
      return topo;
    }
    return queryElevationPoints(db, activityId, false);
  }

  private List<DataPoint> queryElevationPoints(
      SQLiteDatabase db, long activityId, boolean topoOnly) {
    List<DataPoint> points = new ArrayList<>();
    String xColumn = useDistanceAsX ? DB.LOCATION.DISTANCE : DB.LOCATION.ELAPSED;
    String yExpr =
        topoOnly
            ? DB.LOCATION.TOPO_ALTITUDE
            : "COALESCE(" + DB.LOCATION.TOPO_ALTITUDE + ", " + DB.LOCATION.ALTITUDE + ")";
    String yFilter =
        topoOnly
            ? DB.LOCATION.TOPO_ALTITUDE + " IS NOT NULL"
            : "("
                + DB.LOCATION.TOPO_ALTITUDE
                + " IS NOT NULL OR "
                + DB.LOCATION.ALTITUDE
                + " IS NOT NULL)";
    String sql =
        "SELECT "
            + xColumn
            + ", "
            + yExpr
            + " FROM "
            + DB.LOCATION.TABLE
            + " WHERE "
            + DB.LOCATION.ACTIVITY
            + " = ? AND "
            + yFilter
            + " AND "
            + xColumn
            + " IS NOT NULL ORDER BY "
            + DB.LOCATION.ELAPSED
            + " ASC, "
            + DB.LOCATION.TIME
            + " ASC";
    try (Cursor cursor = db.rawQuery(sql, new String[] {String.valueOf(activityId)})) {
      while (cursor.moveToNext()) {
        if (cursor.isNull(0) || cursor.isNull(1)) {
          continue;
        }
        double x = cursor.getDouble(0);
        if (!useDistanceAsX) {
          x = x / 1000.0;
        }
        points.add(new DataPoint(x, cursor.getDouble(1)));
      }
    } catch (Exception e) {
      Log.w(getClass().getName(), "Failed to load elevation points", e);
    }
    return points;
  }

  private void applyGraphProducer(GraphProducer graphData) {
    graphData.complete(graphView);
    graphTab.removeView(graphView);
    graphTab.removeView(graphView2);
    graphTab.removeView(graphView3);
    boolean pace = graphData.HasPaceInfo();
    boolean hr = graphData.HasHRInfo();
    boolean elev = graphData.HasElevationInfo();
    int n = (pace ? 1 : 0) + (hr ? 1 : 0) + (elev ? 1 : 0);
    float weight = n > 0 ? 1f / n : 1f;
    if (pace) {
      graphTab.addView(graphView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, weight));
    }
    if (hr) {
      graphTab.addView(graphView2, new LayoutParams(LayoutParams.MATCH_PARENT, 0, weight));
    }
    if (elev) {
      List<DataPoint> elevPoints = uniqueIncreasingX(graphData.elevationPoints);
      ElevationProfile.Series profile = toElevationProfile(elevPoints);
      if (profile.x.length >= 2) {
        DataPoint[] plot = new DataPoint[profile.x.length];
        for (int i = 0; i < profile.x.length; i++) {
          plot[i] = new DataPoint(profile.x[i], profile.plotY[i]);
        }
        LineGraphSeries<DataPoint> elevSeries = new LineGraphSeries<>(plot);
        elevSeries.setColor(0xFF34D399);
        elevSeries.setThickness(6);
        graphView3.removeAllSeries();
        graphView3.addSeries(elevSeries);
        double[] bounds = GraphAxis.zoomedBounds(0, maxValue(profile.plotY), 6, true);
        graphView3.getViewport().setYAxisBoundsManual(true);
        graphView3.getViewport().setMinY(bounds[0]);
        graphView3.getViewport().setMaxY(bounds[1]);
        final double yOffset = profile.yOffset;
        graphView3.getGridLabelRenderer().setHorizontalAxisTitle(xAxis.label());
        graphView3
            .getGridLabelRenderer()
            .setLabelFormatter(
                new DefaultLabelFormatter() {
                  @Override
                  public String formatLabel(double value, boolean isValueX) {
                    if (isValueX) {
                      return xAxis.formatValue(value);
                    }
                    return formatter.formatElevation(
                        Formatter.Format.TXT_SHORT, value + yOffset);
                  }
                });
        graphTab.addView(
            graphView3, new LayoutParams(LayoutParams.MATCH_PARENT, 0, weight * 1.4f));
      }
    }
    alignCharts();

    if (hrzonesBarLayout != null) {
      hrzonesBarLayout.removeView(hrzonesBar);
      if (graphData.HasHRZHist()) {
        hrzonesBarLayout.setVisibility(View.VISIBLE);
        hrzonesBarLayout.addView(hrzonesBar);
      } else {
        hrzonesBarLayout.setVisibility(View.GONE);
      }
    }
    if (!firstLoad) {
      graphTab.invalidate();
    } else {
      firstLoad = false;
    }
  }

  class GraphProducer {
    final int interval;
    boolean first = true;
    int pos = 0;
    final double[] time;
    final double[] distance;
    double sum_time = 0;
    double sum_distance = 0;
    double acc_time = 0;

    final int[] hr;
    double[] hrzHist = null;

    double tot_avg_hr = 0;

    double avg_velocity = 0;
    double min_velocity = Double.MAX_VALUE;
    double max_velocity = Double.MIN_VALUE;
    final List<DataPoint> velocityList;
    final List<DataPoint> hrList;
    List<DataPoint> elevationPoints = new ArrayList<>();

    boolean showPace = false;
    boolean showHR = false;
    boolean showHRZhist = false;
    final HRZones hrCalc;

    final SpeedUnit preferred_speedunit;

    public GraphProducer(Context context, int noPoints) {
      final int GRAPH_INTERVAL_SECONDS = 5; // 1 point every 5 sec
      final int GRAPH_AVERAGE_SECONDS = 30; // moving average 30 sec

      final int graphAverageSeconds;
      if (noPoints < 60) {
        // This is short, maybe when testing. Dont bother to check time between points
        graphAverageSeconds = 1;
        this.interval = 2;
      } else {
        graphAverageSeconds = GRAPH_AVERAGE_SECONDS;
        this.interval = GRAPH_INTERVAL_SECONDS;
      }
      this.velocityList = new ArrayList<>();
      this.time = new double[graphAverageSeconds];
      this.distance = new double[graphAverageSeconds];

      this.hrList = new ArrayList<>();
      this.hr = new int[graphAverageSeconds];

      Resources res = context.getResources();
      Context ctx = context.getApplicationContext();
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
      this.hrCalc = new HRZones(res, prefs);
      if (hrCalc.isConfigured()) {
        this.hrzHist = new double[hrCalc.getCount() + 1];
        Arrays.fill(this.hrzHist, 0);
        showHRZhist = true;
      }
      this.preferred_speedunit = Formatter.getPreferredSpeedUnit(context);

      clearSmooth(0);
    }

    void clearSmooth(double tot_X) {
      if (pos >= (this.time.length / 2)
          && (acc_time >= 1000 * (interval / 2.0))
          && xAxis.getX(sum_distance, sum_time) > 0) {
        emit(tot_X);
      }

      for (int i = 0; i < this.distance.length; i++) {
        time[i] = 0;
        distance[i] = 0;
        hr[i] = 0;
      }
      pos = 0;
      sum_time = 0;
      sum_distance = 0;
      acc_time = 0;
    }

    void addObservation(
        double delta_time, double delta_distance, double tot_X, LocationEntity loc) {
      // delta_time is in ms, dist in m
      if (delta_time < 500) return;

      // Update moving average
      int p = pos % this.time.length;
      sum_time -= this.time[p];
      sum_time += delta_time;
      sum_distance -= this.distance[p];
      sum_distance += delta_distance;
      this.time[p] = delta_time;
      this.distance[p] = delta_distance;

      if (loc.getHr() != null) {
        showHR = true;
        int hr = loc.getHr();
        this.hr[p] = hr;

        if (showHRZhist && hr > 0) {
          this.hrzHist[hrCalc.getZoneInt(hr)] += delta_time;
        }
      } else {
        this.hr[p] = 0;
      }

      if (delta_distance > 0 && delta_time > 0) {
        showPace = true;
      }

      pos += 1;
      acc_time += delta_time;

      if (pos >= this.time.length && (acc_time >= 1000 * interval) &&
          xAxis.getX(sum_distance, sum_time) > 0) {
        emit(tot_X);
      }
    }

    void emit(double tot_X) {
      double avg_time = sum_time;
      double avg_dist = sum_distance;
      double avg_hr = calculateAverageHr(hr);

      double velocity = avg_time == 0 ? 0.0 : avg_dist * 1000.0 / avg_time;
      final double paceLimit = 1000.0 / 60.0 / 15.0;
      if (this.preferred_speedunit == SpeedUnit.PACE) {
        // Avoid presenting very slow pace (easier than to handle manual y axis scaling)
        velocity = Math.max(velocity, paceLimit);
      }
      if (first) {
        if (tot_X > 0) {
          velocityList.add(new DataPoint(0, velocity));
          if (avg_hr > 0) {
            hrList.add(new DataPoint(0, Math.round(avg_hr)));
          }
        }
        first = false;
      }
      velocityList.add(new DataPoint(tot_X, velocity));
      if (avg_hr > 0) {
        hrList.add(new DataPoint(tot_X, Math.round(avg_hr)));
      }
      acc_time = 0;

      tot_avg_hr += avg_hr;
      avg_velocity += velocity;
      min_velocity = Math.min(min_velocity, velocity);
      max_velocity = Math.max(max_velocity, velocity);

      if (sum_distance > 0 && sum_time > 0) {
        showPace = true;
      }
    }

    class GraphFilter {

      final double[] data;
      final List<DataPoint> source;

      GraphFilter(List<DataPoint> velocityList) {
        source = velocityList;
        data = new double[velocityList.size()];
        for (int i = 0; i < velocityList.size(); i++) data[i] = velocityList.get(i).getY();
      }

      void complete() {
        for (int i = 0; i < source.size(); i++)
          source.set(i, new DataPoint(source.get(i).getX(), data[i]));
      }

      void init(double[] window, double val) {
        for (int j = 0; j < window.length - 1; j++) window[j] = val;
      }

      void shiftLeft(double[] window, double newVal) {
        System.arraycopy(window, 1, window, 0, window.length - 1);
        window[window.length - 1] = newVal;
      }

      /** Perform in place moving average */
      void movingAvergage(int windowLen) {
        double[] window = new double[windowLen];
        init(window, data[0]);

        final int mid = (window.length - 1) / 2;
        final int last = window.length - 1;
        for (int i = 0; i < data.length && i <= mid; i++) {
          window[i + mid] = data[i];
        }

        double sum = 0;
        for (double aWindow : window) sum += aWindow;

        for (int i = 0; i < data.length; i++) {
          double newY = sum / windowLen;
          data[i] = newY;
          sum -= window[0];
          shiftLeft(window, (i + mid) < data.length ? data[i + mid] : avg_velocity);
          sum += window[last];
        }
      }

      /** Perform in place moving average */
      void movingMedian(int windowLen) {
        double[] window = new double[windowLen];
        init(window, data[0]);

        final int mid = (window.length - 1) / 2;
        for (int i = 0; i < data.length && i <= mid; i++) {
          window[i + mid] = data[i];
        }

        double[] sort = new double[windowLen];
        for (int i = 0; i < data.length; i++) {
          System.arraycopy(window, 0, sort, 0, windowLen);
          Arrays.sort(sort);
          data[i] = sort[mid];
          shiftLeft(window, (i + mid) < data.length ? data[i + mid] : avg_velocity);
        }
      }

      /** Perform in place SavitzkyGolay windowLen = 5 */
      void SavitzkyGolay5() {
        final int len = 5;
        double[] window = new double[len];
        init(window, data[0]);

        final int mid = (window.length - 1) / 2;
        for (int i = 0; i < data.length && i <= mid; i++) {
          window[i + mid] = data[i];
        }
        for (int i = 0; i < data.length; i++) {
          double newY =
              (-3 * window[0] + 12 * window[1] + 17 * window[2] + 12 * window[3] - 3 * window[4])
                  / 35;
          data[i] = newY;
          shiftLeft(window, (i + mid) < data.length ? data[i + mid] : avg_velocity);
        }
      }

      /** Perform in place SavitzkyGolay windowLen = 7 */
      void SavitzkyGolay7() {
        final int len = 7;
        double[] window = new double[len];
        init(window, data[0]);

        final int mid = (window.length - 1) / 2;
        for (int i = 0; i < data.length && i <= mid; i++) {
          window[i + mid] = data[i];
        }
        for (int i = 0; i < data.length; i++) {
          double newY =
              (-2 * window[0]
                      + 3 * window[1]
                      + 6 * window[2]
                      + 7 * window[3]
                      + 6 * window[4]
                      + 3 * window[5]
                      - 2 * window[6])
                  / 21;
          data[i] = newY;
          shiftLeft(window, (i + mid) < data.length ? data[i + mid] : avg_velocity);
        }
      }

      void KolmogorovZurbenko(int n, int len) {
        for (int i = 0; i < n; i++) movingAvergage(len);
      }
    }

    public void complete(final GraphView graphView) {
      avg_velocity /= velocityList.size();
      Log.e(getClass().getName(), "graph: " + velocityList.size() + " points");

      boolean smoothData =
          PreferenceManager.getDefaultSharedPreferences(graphView.getContext())
              .getBoolean(
                  graphView
                      .getContext()
                      .getResources()
                      .getString(R.string.pref_pace_graph_smoothing),
                  true);
      if (velocityList.size() > 0 && smoothData) {
        GraphFilter f = new GraphFilter(velocityList);
        final String defaultFilterList =
            graphView.getContext().getResources().getString(R.string.mm31kz513sg5);
        final String filterList =
            PreferenceManager.getDefaultSharedPreferences(graphView.getContext())
                .getString(
                    graphView
                        .getContext()
                        .getResources()
                        .getString(R.string.pref_pace_graph_smoothing_filters),
                    defaultFilterList);
        final String[] filters = filterList.split(";");
        System.err.print("Applying filters(" + filters.length + ", >" + filterList + "<):");
        for (String filter : filters) {
          int[] args = getArgs(filter);
          if (filter.startsWith("mm")) {
            if (args.length == 1) {
              f.movingMedian(args[0]);
              System.err.print(" mm(" + args[0] + ")");
            }
          } else if (filter.startsWith("ma")) {
            if (args.length == 1) {
              f.movingAvergage(args[0]);
              System.err.print(" ma(" + args[0] + ")");
            }
          } else if (filter.startsWith("kz")) {
            if (args.length == 2) {
              f.KolmogorovZurbenko(args[0], args[1]);
              System.err.print(" kz(" + args[0] + "," + args[1] + ")");
            }
          } else if (filter.startsWith("sg")) {
            if (args.length == 1 && args[0] == 5) {
              f.SavitzkyGolay5();
              System.err.print(" sg(5)");
            } else if (args.length == 1 && args[0] == 7) {
              f.SavitzkyGolay7();
              System.err.print(" sg(7)");
            }
          }
        }
        Log.e(getClass().getName(), "");
        f.complete();
      }
      LineGraphSeries<DataPoint> graphViewData =
          new LineGraphSeries<>(velocityList.toArray(new DataPoint[0]));
      graphView.addSeries(graphViewData); // data
      graphView.getViewport().setMinX(graphView.getViewport().getMinX(true));
      graphView.getViewport().setMaxX(graphView.getViewport().getMaxX(true));
      graphViewData.setOnDataPointTapListener(
          (series, dataPoint) -> {
            String msg =
                String.format(
                    "%s: %s\n%s: %s %s",
                    graphView.getContext().getString(org.runnerup.common.R.string.Distance),
                    xAxis.formatValue(dataPoint.getX()),
                    formatter.formatVelocityLabel(),
                    formatter.formatVelocityByPreferredUnit(
                        Formatter.Format.TXT_SHORT, dataPoint.getY()),
                    formatter.getVelocityUnit(graphView.getContext()));
            Toast.makeText(graphView.getContext(), msg, Toast.LENGTH_SHORT).show();
          });
      if (showHR) {
        LineGraphSeries<DataPoint> graphViewData2 =
            new LineGraphSeries<>(hrList.toArray(new DataPoint[0]));
        graphView2.addSeries(graphViewData2); // data
        graphView2.getViewport().setMinX(graphView2.getViewport().getMinX(true));
        graphView2.getViewport().setMaxX(graphView2.getViewport().getMaxX(true));
        applyHeartRateYAxisBounds(graphView2, graphViewData2);
        graphViewData2.setOnDataPointTapListener(
            (series, dataPoint) -> {
              String msg =
                  graphView.getContext().getString(org.runnerup.common.R.string.Distance)
                      + ": "
                      + xAxis.formatValue(dataPoint.getX())
                      + "\n"
                      + graphView.getContext().getString(org.runnerup.common.R.string.Heart_rate)
                      + ": "
                      + formatter.formatHeartRate(Formatter.Format.TXT_SHORT, dataPoint.getY());
              Toast.makeText(graphView.getContext(), msg, Toast.LENGTH_SHORT).show();
            });

        if (showHRZhist) {
          System.err.print("HR Zones:");
          double sum = 0;
          for (double aHrzHist : hrzHist) {
            sum += aHrzHist;
          }
          for (int i = 0; i < hrzHist.length; i++) {
            hrzHist[i] = hrzHist[i] / sum;
            System.err.print(" " + hrzHist[i]);
          }
          Log.e(getClass().getName(), "\n");
          hrzonesBar.pushHrzData(hrzHist);
        }
      }
    }

    private int[] getArgs(String s) {
      try {
        s = s.substring(s.indexOf('(') + 1);
        s = s.substring(0, s.indexOf(')'));
        String[] sargs = s.split(",");
        int[] args = new int[sargs.length];
        for (int i = 0; i < args.length; i++) {
          args[i] = Integer.parseInt(sargs[i]);
        }
        return args;
      } catch (Exception e) {
        e.printStackTrace();
        return new int[0];
      }
    }

    public boolean HasPaceInfo() {
      return showPace;
    }

    public boolean HasHRInfo() {
      return showHR;
    }

    public boolean HasHRZHist() {
      return showHR && showHRZhist;
    }

    public boolean HasElevationInfo() {
      return elevationPoints != null && elevationPoints.size() > 1;
    }

    void omitWarmupIfNeeded(double totalRunMeters, boolean distanceOnX) {
      if (!GraphAxis.omitWarmup(totalRunMeters, distanceOnX)) {
        return;
      }
      double cutoff = GraphAxis.WARMUP_OMIT_METERS;
      velocityList.removeIf(p -> p.getX() < cutoff);
      hrList.removeIf(p -> p.getX() < cutoff);
      if (elevationPoints != null) {
        elevationPoints.removeIf(p -> p.getX() < cutoff);
      }
    }
  }

  private double calculateAverageHr(int[] data) {
    int sum = 0;
    int no = 0;

    for (int aData : data) {
      if (aData > 0) {
        sum = sum + aData;
        no++;
      }
    }
    // TODO Average of pointe, not over time
    if (no == 0) {
      return 0;
    } else {
      return (double) sum / no;
    }
  }

  class LoadParam {
    public LoadParam(Context context, SQLiteDatabase mDB, long mID) {
      this.context = context;
      this.mDB = mDB;
      this.mID = mID;
    }

    final Context context;
    final SQLiteDatabase mDB;
    final long mID;
  }

  boolean firstLoad = true;
}
