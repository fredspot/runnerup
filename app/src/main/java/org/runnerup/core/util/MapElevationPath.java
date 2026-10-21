package org.runnerup.core.util;

import java.util.ArrayList;
import java.util.List;
import org.runnerup.tracking.elevation.TopographyInterpolator;

/** Splits a GPS track into colored polylines so elevation is visible on the map. */
public final class MapElevationPath {

  public static final int MAX_POINTS = 250;
  public static final int OUTLINE_COLOR = 0xFF111827;

  public static final class Segment {
    public final double[] lat;
    public final double[] lng;
    public final int color;

    Segment(double[] lat, double[] lng, int color) {
      this.lat = lat;
      this.lng = lng;
      this.color = color;
    }
  }

  private MapElevationPath() {}

  public static double[] minMax(List<Double> elevations) {
    if (elevations == null) {
      return null;
    }
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (Double z : elevations) {
      if (z == null || Double.isNaN(z)) {
        continue;
      }
      if (z < min) {
        min = z;
      }
      if (z > max) {
        max = z;
      }
    }
    if (min > max) {
      return null;
    }
    return new double[] {min, max};
  }

  public static List<Segment> segments(double[] lat, double[] lng, double[] elevM) {
    return segments(lat, lng, elevM, MAX_POINTS);
  }

  public static List<Segment> segments(
      double[] lat, double[] lng, double[] elevM, int maxPoints) {
    List<Segment> out = new ArrayList<>();
    if (lat == null
        || lng == null
        || elevM == null
        || lat.length < 2
        || lat.length != lng.length
        || lat.length != elevM.length) {
      return out;
    }
    int[] idx = TopographyInterpolator.downsampleIndices(lat.length, maxPoints);
    double[] xs = new double[idx.length];
    double[] ys = new double[idx.length];
    double[] zs = new double[idx.length];
    for (int i = 0; i < idx.length; i++) {
      xs[i] = lat[idx[i]];
      ys[i] = lng[idx[i]];
      zs[i] = elevM[idx[i]];
    }
    fillGaps(zs);
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (double z : zs) {
      if (Double.isNaN(z)) {
        continue;
      }
      if (z < min) {
        min = z;
      }
      if (z > max) {
        max = z;
      }
    }
    if (min > max) {
      out.add(copyRange(xs, ys, 0, xs.length - 1, MapElevationColor.FALLBACK));
      return out;
    }
    int start = 0;
    int color = MapElevationColor.color(zs[0], min, max);
    for (int i = 1; i < xs.length; i++) {
      int next = MapElevationColor.color(zs[i], min, max);
      if (next != color) {
        out.add(copyRange(xs, ys, start, i, color));
        start = i;
        color = next;
      }
    }
    if (start < xs.length - 1 || out.isEmpty()) {
      out.add(copyRange(xs, ys, start, xs.length - 1, color));
    }
    return out;
  }

  static void fillGaps(double[] elevM) {
    double last = Double.NaN;
    for (int i = 0; i < elevM.length; i++) {
      if (!Double.isNaN(elevM[i])) {
        last = elevM[i];
      } else if (!Double.isNaN(last)) {
        elevM[i] = last;
      }
    }
    last = Double.NaN;
    for (int i = elevM.length - 1; i >= 0; i--) {
      if (!Double.isNaN(elevM[i])) {
        last = elevM[i];
      } else if (!Double.isNaN(last)) {
        elevM[i] = last;
      }
    }
  }

  private static Segment copyRange(
      double[] lat, double[] lng, int from, int to, int color) {
    int n = to - from + 1;
    double[] xs = new double[n];
    double[] ys = new double[n];
    System.arraycopy(lat, from, xs, 0, n);
    System.arraycopy(lng, from, ys, 0, n);
    return new Segment(xs, ys, color);
  }
}
