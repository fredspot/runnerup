package org.runnerup.tracking.elevation;

/** Linear interpolation of DEM sample elevations onto a denser track. */
public final class TopographyInterpolator {

  private TopographyInterpolator() {}

  /**
   * Interpolates {@code sampleElevM} (at {@code sampleDistanceM}) onto {@code trackDistanceM}.
   * Values outside the sample range clamp to the nearest sample. Missing samples are NaN.
   */
  public static double[] interpolate(
      double[] sampleDistanceM, double[] sampleElevM, double[] trackDistanceM) {
    double[] out = new double[trackDistanceM.length];
    if (sampleDistanceM == null
        || sampleElevM == null
        || sampleDistanceM.length == 0
        || sampleDistanceM.length != sampleElevM.length) {
      for (int i = 0; i < out.length; i++) {
        out[i] = Double.NaN;
      }
      return out;
    }
    for (int i = 0; i < trackDistanceM.length; i++) {
      out[i] = interpolateOne(sampleDistanceM, sampleElevM, trackDistanceM[i]);
    }
    return out;
  }

  static double interpolateOne(double[] sampleDistanceM, double[] sampleElevM, double distanceM) {
    if (distanceM <= sampleDistanceM[0]) {
      return sampleElevM[0];
    }
    int last = sampleDistanceM.length - 1;
    if (distanceM >= sampleDistanceM[last]) {
      return sampleElevM[last];
    }
    for (int i = 1; i < sampleDistanceM.length; i++) {
      if (distanceM <= sampleDistanceM[i]) {
        double d0 = sampleDistanceM[i - 1];
        double d1 = sampleDistanceM[i];
        double span = d1 - d0;
        if (span <= 0) {
          return sampleElevM[i];
        }
        double t = (distanceM - d0) / span;
        return sampleElevM[i - 1] + t * (sampleElevM[i] - sampleElevM[i - 1]);
      }
    }
    return sampleElevM[last];
  }

  /** Evenly pick up to {@code maxPoints} indices from {@code count} points, always including ends. */
  public static int[] downsampleIndices(int count, int maxPoints) {
    if (count <= 0) {
      return new int[0];
    }
    if (count <= maxPoints) {
      int[] all = new int[count];
      for (int i = 0; i < count; i++) {
        all[i] = i;
      }
      return all;
    }
    int[] indices = new int[maxPoints];
    indices[0] = 0;
    indices[maxPoints - 1] = count - 1;
    for (int i = 1; i < maxPoints - 1; i++) {
      indices[i] = (int) Math.round(i * (count - 1) / (double) (maxPoints - 1));
    }
    return indices;
  }
}
