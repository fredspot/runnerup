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

import static org.runnerup.core.util.Formatter.Format.TXT_SHORT;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewTreeObserver;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.PreferenceManager;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.plugins.annotation.LineManager;
import com.mapbox.mapboxsdk.plugins.annotation.LineOptions;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions;
import com.mapbox.mapboxsdk.style.layers.Property;
import com.mapbox.mapboxsdk.utils.BitmapUtils;
import com.mapbox.mapboxsdk.utils.ColorUtils;
import com.mapbox.pluginscalebar.ScaleBarOptions;
import com.mapbox.pluginscalebar.ScaleBarPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.data.entities.LocationEntity;

public class MapWrapper implements Constants {

  private final MapView mapView;
  private LineManager lineManager;
  private MapboxMap mapboxMapRef;
  private SymbolManager symbolManager;

  private final long mID;
  private final SQLiteDatabase mDB;
  private final Context context;
  private final Formatter formatter;

  public MapWrapper(
      Context context, SQLiteDatabase mDB, long mID, Formatter formatter, Object mapView) {
    this.context = context;
    this.mDB = mDB;
    this.mID = mID;
    this.formatter = formatter;
    this.mapView = (MapView) mapView;
  }

  public static void start(Context context) {
    Mapbox.getInstance(context, BuildConfig.MAPBOX_ACCESS_TOKEN);
  }

  public void onCreate(Bundle savedInstanceState) {
    mapView.onCreate(savedInstanceState);
    mapView.getMapAsync(
        mapboxMap -> {
          mapboxMapRef = mapboxMap;
          SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
          Resources res = context.getResources();
          String val =
              prefs.getString(
                  res.getString(R.string.pref_mapbox_default_style),
                  res.getString(R.string.mapboxDefaultStyle));

          Style.Builder style = new Style.Builder().fromUri(val);
          mapboxMap.setStyle(
              style,
              mapStyle -> {
                lineManager = new LineManager(mapView, mapboxMap, mapStyle);
                symbolManager = new SymbolManager(mapView, mapboxMap, mapStyle);
                // Map is set up and the style has loaded
                LoadParam loadParam = new LoadParam(context, mDB, mID, mapboxMap);
                BgTasks.runDb(() -> buildRoute(loadParam), route -> applyRoute(loadParam, route));
              });
        });
  }

  public void reload() {
    if (mapboxMapRef == null || lineManager == null || symbolManager == null) {
      return;
    }
    lineManager.deleteAll();
    symbolManager.deleteAll();
    LoadParam loadParam = new LoadParam(context, mDB, mID, mapboxMapRef);
    BgTasks.runDb(() -> buildRoute(loadParam), route -> applyRoute(loadParam, route));
  }

  public void onResume() {
    mapView.onResume();
  }

  public void onStart() {
    mapView.onStart();
  }

  public void onStop() {
    mapView.onStop();
  }

  public void onPause() {
    mapView.onPause();
  }

  public void onSaveInstanceState(Bundle outState) {
    mapView.onSaveInstanceState(outState);
  }

  public void onLowMemory() {
    mapView.onLowMemory();
  }

  public void onDestroy() {
    if (lineManager != null) {
      lineManager.onDestroy();
    }
    if (symbolManager != null) {
      symbolManager.onDestroy();
    }
    mapView.onDestroy();
  }

    class Route {
    Route(Context context, MapboxMap map) {
      this.context = context;
      this.map = map;
    }

    final List<LatLng> path = new ArrayList<>(10);
    List<MapElevationPath.Segment> elevationSegments = new ArrayList<>();
    double elevMin = Double.NaN;
    double elevMax = Double.NaN;
    final ArrayList<SymbolOptions> markers = new ArrayList<>(10);
    final Context context;
    final MapboxMap map;
  }

  private class LoadParam {
    LoadParam(Context context, SQLiteDatabase mDB, long mID, MapboxMap map) {
      this.context = context;
      this.mDB = mDB;
      this.mID = mID;
      this.map = map;
    }

    final Context context;
    final SQLiteDatabase mDB;
    final long mID;
    final MapboxMap map;
  }

  private Route buildRoute(LoadParam params) {
      Route route = new Route(params.context, params.map);
      LocationEntity.LocationList<LocationEntity> ll =
          new LocationEntity.LocationList<>(params.mDB, params.mID);
      int lastLap = 0;
      List<Double> elevations = new ArrayList<>();
      for (LocationEntity loc : ll) {
        LatLng point = new LatLng(loc.getLatitude(), loc.getLongitude());
        route.path.add(point);
        elevations.add(loc.getPreferredElevationM());

        Integer type;
        // Start/end markers are not set in db, special handling
        if (route.markers.isEmpty()) {
          type = DB.LOCATION.TYPE_START;
        } else {
          type = loc.getType();
        }

        String iconImage;
        if (type != DB.LOCATION.TYPE_START && lastLap != loc.getLap()) {
          lastLap = loc.getLap();
          iconImage = "lap";
        } else if (type == DB.LOCATION.TYPE_START
            || type == DB.LOCATION.TYPE_END
            || type == DB.LOCATION.TYPE_PAUSE
            || type == DB.LOCATION.TYPE_RESUME
            || type == DB.LOCATION.TYPE_AUTO_PAUSE
            || type == DB.LOCATION.TYPE_AUTO_RESUME) {
          // Auto-pause/resume reuse the manual icons; no UI change today.
          int iconType =
              type == DB.LOCATION.TYPE_AUTO_PAUSE
                  ? DB.LOCATION.TYPE_PAUSE
                  : type == DB.LOCATION.TYPE_AUTO_RESUME ? DB.LOCATION.TYPE_RESUME : type;
          iconImage = Integer.toString(iconType);
        } else {
          iconImage = null;
        }

        if (iconImage != null) {
          // TBD Implement Info popup with the info instead, using the annotaion plugin (currently
          // no examples)
          String info;
          if (type == DB.LOCATION.TYPE_START) {
            info = null;
          } else {
            if (iconImage.equals("lap")) {
              info =
                  "#"
                      + loc.getLap()
                      + "\n"
                      + org.runnerup.core.util.RouteMarkerLabels.lapMarkerInfo(
                          formatter, loc, loc.getDistance());
            } else {
              info = null;
            }
          }

          SymbolOptions m =
              new SymbolOptions()
                  .withLatLng(point)
                  .withIconImage(iconImage)
                  .withIconAnchor(Property.ICON_ANCHOR_BOTTOM)
                  .withTextField(info)
                  .withTextAnchor(Property.TEXT_ANCHOR_TOP);
          route.markers.add(m);
        }
      }
      ll.close();
      double[] elevRange = MapElevationPath.minMax(elevations);
      if (elevRange != null) {
        route.elevMin = elevRange[0];
        route.elevMax = elevRange[1];
      }
      route.elevationSegments = toElevationSegments(route.path, elevations);

      // Track is normally ended with a pause, not always followed by an end
      // Ignore the pause
      if (route.markers.size() >= 2) {
        SymbolOptions m = route.markers.get(route.markers.size() - 2);
        SymbolOptions me = route.markers.get(route.markers.size() - 1);
        if (m.getIconImage().equals(((Integer) DB.LOCATION.TYPE_PAUSE).toString())
            && me.getIconImage().equals(((Integer) DB.LOCATION.TYPE_END).toString())) {
          route.markers.remove(route.markers.size() - 2);
        }
      }

      if (route.markers.size() >= 1) {
        SymbolOptions me = route.markers.get(route.markers.size() - 1);
        if (me.getIconImage().equals(((Integer) DB.LOCATION.TYPE_PAUSE).toString())) {
          me.withIconImage(((Integer) DB.LOCATION.TYPE_END).toString());
        }
      }

      return route;
  }

  private void applyRoute(LoadParam loadParam, Route route) {
      if (route != null && route.map != null) {

        ScaleBarPlugin scaleBarPlugin = new ScaleBarPlugin(mapView, route.map);

        scaleBarPlugin.create(new ScaleBarOptions(route.context));
        MapElevationLegend.bind(mapView, formatter, route.elevMin, route.elevMax);

        if (route.path.size() > 1) {
          LineOptions outline =
              new LineOptions()
                  .withLatLngs(route.path)
                  .withLineColor(ColorUtils.colorToRgbaString(MapElevationPath.OUTLINE_COLOR))
                  .withLineWidth(7.0f);
          lineManager.create(outline);
          if (route.elevationSegments != null && !route.elevationSegments.isEmpty()) {
            for (MapElevationPath.Segment seg : route.elevationSegments) {
              lineManager.create(
                  new LineOptions()
                      .withLatLngs(toLatLngs(seg))
                      .withLineColor(ColorUtils.colorToRgbaString(seg.color))
                      .withLineWidth(4.5f));
            }
          } else {
            lineManager.create(
                new LineOptions()
                    .withLatLngs(route.path)
                    .withLineColor(ColorUtils.colorToRgbaString(MapElevationColor.FALLBACK))
                    .withLineWidth(4.5f));
          }
          Log.v(getClass().getName(), "Added line");

          final LatLngBounds box = new LatLngBounds.Builder().includes(route.path).build();
          final CameraUpdate initialCameraPosition = CameraUpdateFactory.newLatLngBounds(box, 50);
          route.map.moveCamera(initialCameraPosition);

          // Since MapBox 4.2.0-beta.3 moving the camera in onMapReady is not working if map is not
          // visible
          // The proper solution is a redesign using fragments, see
          // https://github.com/mapbox/mapbox-gl-native/issues/6855#event-841575956
          // A workaround is to try move the camera at view updates as long as position is 0,0
          mapView
              .getViewTreeObserver()
              .addOnGlobalLayoutListener(
                  new ViewTreeObserver.OnGlobalLayoutListener() {
                    // We check which build version we are using.
                    @Override
                    public void onGlobalLayout() {
                      if (route.map.getCameraPosition().target.getLatitude() != 0
                          || route.map.getCameraPosition().target.getLongitude() != 0) {
                        mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                      } else {
                        route.map.moveCamera(initialCameraPosition);
                      }
                    }
                  });
        }

        // Images id from text
        route
            .map
            .getStyle()
            .addImage(
                ((Integer) DB.LOCATION.TYPE_START).toString(),
                Objects.requireNonNull(
                    BitmapUtils.getBitmapFromDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.ic_map_marker_start))),
                false);
        route
            .map
            .getStyle()
            .addImage(
                ((Integer) DB.LOCATION.TYPE_END).toString(),
                Objects.requireNonNull(
                    BitmapUtils.getBitmapFromDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.ic_map_marker_end))),
                false);
        route
            .map
            .getStyle()
            .addImage(
                ((Integer) DB.LOCATION.TYPE_PAUSE).toString(),
                Objects.requireNonNull(
                    BitmapUtils.getBitmapFromDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.ic_map_marker_pause))),
                false);
        route
            .map
            .getStyle()
            .addImage(
                ((Integer) DB.LOCATION.TYPE_RESUME).toString(),
                Objects.requireNonNull(
                    BitmapUtils.getBitmapFromDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.ic_map_marker_resume))),
                false);
        route
            .map
            .getStyle()
            .addImage(
                "lap",
                Objects.requireNonNull(
                    BitmapUtils.getBitmapFromDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.ic_map_marker_lap))),
                false);

        symbolManager.setIconAllowOverlap(true);
        if (route.markers.size() > 0) {
          for (SymbolOptions m : route.markers) {
            symbolManager.create(m);
          }
        }
        Log.v(getClass().getName(), "Added " + route.markers.size() + " markers");
      }
  }

  private static List<MapElevationPath.Segment> toElevationSegments(
      List<LatLng> path, List<Double> elevations) {
    int n = path.size();
    if (n != elevations.size() || n < 2) {
      return new ArrayList<>();
    }
    double[] lat = new double[n];
    double[] lng = new double[n];
    double[] elev = new double[n];
    for (int i = 0; i < n; i++) {
      lat[i] = path.get(i).getLatitude();
      lng[i] = path.get(i).getLongitude();
      elev[i] = elevations.get(i);
    }
    return MapElevationPath.segments(lat, lng, elev);
  }

  private static List<LatLng> toLatLngs(MapElevationPath.Segment seg) {
    List<LatLng> points = new ArrayList<>(seg.lat.length);
    for (int i = 0; i < seg.lat.length; i++) {
      points.add(new LatLng(seg.lat[i], seg.lng[i]));
    }
    return points;
  }
}
