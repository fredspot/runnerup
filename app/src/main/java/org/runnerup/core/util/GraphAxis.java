package org.runnerup.core.util;

/** Shared axis math for the stacked activity graphs. */
public final class GraphAxis {

  /** Drop this much of the start of a long run so warmup does not set the scale. */
  public static final double WARMUP_OMIT_METERS = 500;

  public static final double WARMUP_OMIT_MIN_RUN_METERS = 3000;

  private GraphAxis() {}

  /** True when pace, heart rate, and elevation should start after the warmup distance. */
  public static boolean omitWarmup(double totalRunMeters, boolean distanceOnX) {
    return distanceOnX && totalRunMeters > WARMUP_OMIT_MIN_RUN_METERS;
  }

  /**
   * Union of series {@code [minX, maxX]} ranges. When {@code omitWarmup} is set, samples before
   * {@link #WARMUP_OMIT_METERS} are left out. Returns null when nothing remains.
   */
  public static double[] sharedXRange(boolean omitWarmup, double[]... seriesRanges) {
    if (seriesRanges == null) {
      return null;
    }
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (double[] range : seriesRanges) {
      if (range == null || range.length < 2) {
        continue;
      }
      double lo = range[0];
      double hi = range[1];
      if (Double.isNaN(lo) || Double.isNaN(hi) || Double.isInfinite(lo) || Double.isInfinite(hi)) {
        continue;
      }
      if (hi < lo) {
        double tmp = lo;
        lo = hi;
        hi = tmp;
      }
      if (omitWarmup) {
        if (hi < WARMUP_OMIT_METERS) {
          continue;
        }
        lo = Math.max(lo, WARMUP_OMIT_METERS);
      }
      if (hi < lo) {
        continue;
      }
      min = Math.min(min, lo);
      max = Math.max(max, hi);
    }
    if (min == Double.POSITIVE_INFINITY) {
      return null;
    }
    return new double[] {min, max};
  }

  /**
   * Returns {@code [lo, hi]} around {@code min}…{@code max}. {@code minSpan} is the smallest window;
   * {@code clampMinToZero} keeps the floor at 0 (heart rate), while elevation may go below sea
   * level.
   */
  public static double[] zoomedBounds(
      double min, double max, double minSpan, boolean clampMinToZero) {
    if (Double.isNaN(min) || Double.isNaN(max) || Double.isInfinite(min) || Double.isInfinite(max)) {
      return new double[] {0, Math.max(minSpan, 1)};
    }
    if (max < min) {
      double tmp = min;
      min = max;
      max = tmp;
    }
    double range = max - min;
    double pad = Math.max(minSpan * 0.15, range * 0.08);
    double lo = min - pad;
    double hi = max + pad;
    if (hi - lo < minSpan) {
      double mid = (min + max) / 2.0;
      lo = mid - minSpan / 2.0;
      hi = mid + minSpan / 2.0;
    }
    if (clampMinToZero) {
      lo = Math.max(0, lo);
      if (hi < lo + minSpan) {
        hi = lo + minSpan;
      }
    }
    return new double[] {lo, hi};
  }
}
