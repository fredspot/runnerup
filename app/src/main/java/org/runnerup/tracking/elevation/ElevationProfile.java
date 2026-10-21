package org.runnerup.tracking.elevation;

/**
 * Prepares an elevation series for GraphView. The library's Y axis often includes 0, which hides
 * a 5 m roll at 50 m altitude. Plot Y is shifted down by {@code yOffset} so the profile fills the
 * chart; add {@code yOffset} back when formatting labels.
 */
public final class ElevationProfile {

  public static final class Series {
    public final double[] x;
    public final double[] plotY;
    public final double yOffset;

    Series(double[] x, double[] plotY, double yOffset) {
      this.x = x;
      this.plotY = plotY;
      this.yOffset = yOffset;
    }
  }

  private ElevationProfile() {}

  public static Series prepare(double[] x, double[] elevationM, int maxPoints) {
    if (x == null || elevationM == null || x.length == 0 || x.length != elevationM.length) {
      return new Series(new double[0], new double[0], 0);
    }
    double[] xs = x;
    double[] ys = x.length > 5 ? ElevationSmoother.smooth(x, elevationM) : elevationM;
    if (maxPoints > 1 && x.length > maxPoints) {
      int[] idx = TopographyInterpolator.downsampleIndices(x.length, maxPoints);
      xs = new double[idx.length];
      double[] downY = new double[idx.length];
      for (int i = 0; i < idx.length; i++) {
        xs[i] = x[idx[i]];
        downY[i] = ys[idx[i]];
      }
      ys = downY;
    }
    double min = ys[0];
    double max = ys[0];
    for (double y : ys) {
      if (y < min) {
        min = y;
      }
      if (y > max) {
        max = y;
      }
    }
    double[] plotY = new double[ys.length];
    for (int i = 0; i < ys.length; i++) {
      plotY[i] = ys[i] - min;
    }
    return new Series(xs, plotY, min);
  }
}
