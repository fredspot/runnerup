package org.runnerup.tracking.elevation;

import java.util.Arrays;

/**
 * Pace-invariant, distance-based elevation smoothing so a slower run (with more GPS samples per
 * metre and more jitter) produces the same clean elevation profile and gain/loss as a faster run on
 * the same route.
 *
 * <p>Uses a two-stage filter:
 *
 * <ol>
 *   <li>Distance-window median filter (`±25 m`) to strip single-point GPS/DEM spikes (e.g. bridges,
 *       trees, buildings, or slow-pace GPS wander).
 *   <li>Distance-window triangular moving average (`±45 m` half-width) so dense slow-pace samples
 *       are weighted over physical distance rather than sample index.
 * </ol>
 */
public final class ElevationSmoother {

  /** Half-window in metres for outlier/spike median rejection. */
  public static final double DEFAULT_MEDIAN_HALF_WINDOW_M = 25.0;

  /** Half-window in metres for triangular distance-weighted smoothing. */
  public static final double DEFAULT_MEAN_HALF_WINDOW_M = 45.0;

  /** Fallback sample-count half-window when distance is missing/non-increasing. */
  private static final int FALLBACK_INDEX_HALF_WINDOW = 4;

  private ElevationSmoother() {}

  public static double[] smooth(double[] distanceM, double[] elevationsM) {
    return smooth(
        distanceM, elevationsM, DEFAULT_MEDIAN_HALF_WINDOW_M, DEFAULT_MEAN_HALF_WINDOW_M);
  }

  public static double[] smooth(
      double[] distanceM,
      double[] elevationsM,
      double medianHalfWindowM,
      double meanHalfWindowM) {
    if (elevationsM == null || elevationsM.length == 0) {
      return new double[0];
    }
    int n = elevationsM.length;
    if (n <= 2) {
      return Arrays.copyOf(elevationsM, n);
    }
    boolean useDistance =
        distanceM != null
            && distanceM.length == n
            && TopographyElevationService.hasIncreasingDistance(distanceM);
    double[] despiked =
        useDistance
            ? medianByDistance(distanceM, elevationsM, Math.max(5.0, medianHalfWindowM))
            : medianByIndex(elevationsM, FALLBACK_INDEX_HALF_WINDOW);
    return useDistance
        ? triangularMeanByDistance(distanceM, despiked, Math.max(10.0, meanHalfWindowM))
        : triangularMeanByIndex(despiked, FALLBACK_INDEX_HALF_WINDOW);
  }

  private static double[] medianByDistance(
      double[] distM, double[] elevM, double halfWindowM) {
    int n = elevM.length;
    double[] out = new double[n];
    double[] buf = new double[64];
    int left = 0;
    int right = 0;
    for (int i = 0; i < n; i++) {
      if (Double.isNaN(elevM[i])) {
        out[i] = Double.NaN;
        continue;
      }
      double center = distM[i];
      while (left < i && center - distM[left] > halfWindowM) {
        left++;
      }
      if (right < i) right = i;
      while (right + 1 < n && distM[right + 1] - center <= halfWindowM) {
        right++;
      }
      int count = 0;
      int needed = right - left + 1;
      if (buf.length < needed) {
        buf = new double[Math.max(needed, buf.length * 2)];
      }
      for (int j = left; j <= right; j++) {
        double v = elevM[j];
        if (!Double.isNaN(v) && !Double.isInfinite(v)) {
          buf[count++] = v;
        }
      }
      if (count == 0) {
        out[i] = elevM[i];
      } else {
        Arrays.sort(buf, 0, count);
        int mid = count / 2;
        out[i] = (count % 2 == 1) ? buf[mid] : 0.5 * (buf[mid - 1] + buf[mid]);
      }
    }
    return out;
  }

  private static double[] triangularMeanByDistance(
      double[] distM, double[] elevM, double halfWindowM) {
    int n = elevM.length;
    double[] out = new double[n];
    int left = 0;
    int right = 0;
    for (int i = 0; i < n; i++) {
      if (Double.isNaN(elevM[i])) {
        out[i] = Double.NaN;
        continue;
      }
      double center = distM[i];
      while (left < i && center - distM[left] > halfWindowM) {
        left++;
      }
      if (right < i) right = i;
      while (right + 1 < n && distM[right + 1] - center <= halfWindowM) {
        right++;
      }
      double weightedSum = 0.0;
      double weightTotal = 0.0;
      for (int j = left; j <= right; j++) {
        double v = elevM[j];
        if (Double.isNaN(v) || Double.isInfinite(v)) continue;
        double d = Math.abs(distM[j] - center);
        double w = Math.max(0.05, 1.0 - (d / halfWindowM));
        weightedSum += w * v;
        weightTotal += w;
      }
      out[i] = weightTotal > 0 ? weightedSum / weightTotal : elevM[i];
    }
    return out;
  }

  private static double[] medianByIndex(double[] elevM, int halfWin) {
    int n = elevM.length;
    double[] out = new double[n];
    double[] buf = new double[2 * halfWin + 1];
    for (int i = 0; i < n; i++) {
      if (Double.isNaN(elevM[i])) {
        out[i] = Double.NaN;
        continue;
      }
      int lo = Math.max(0, i - halfWin);
      int hi = Math.min(n - 1, i + halfWin);
      int count = 0;
      for (int j = lo; j <= hi; j++) {
        if (!Double.isNaN(elevM[j]) && !Double.isInfinite(elevM[j])) {
          buf[count++] = elevM[j];
        }
      }
      if (count == 0) {
        out[i] = elevM[i];
      } else {
        Arrays.sort(buf, 0, count);
        int mid = count / 2;
        out[i] = (count % 2 == 1) ? buf[mid] : 0.5 * (buf[mid - 1] + buf[mid]);
      }
    }
    return out;
  }

  private static double[] triangularMeanByIndex(double[] elevM, int halfWin) {
    int n = elevM.length;
    double[] out = new double[n];
    for (int i = 0; i < n; i++) {
      if (Double.isNaN(elevM[i])) {
        out[i] = Double.NaN;
        continue;
      }
      int lo = Math.max(0, i - halfWin);
      int hi = Math.min(n - 1, i + halfWin);
      double sum = 0;
      double wSum = 0;
      for (int j = lo; j <= hi; j++) {
        if (Double.isNaN(elevM[j]) || Double.isInfinite(elevM[j])) continue;
        double w = (halfWin + 1) - Math.abs(j - i);
        sum += w * elevM[j];
        wSum += w;
      }
      out[i] = wSum > 0 ? sum / wSum : elevM[i];
    }
    return out;
  }
}
