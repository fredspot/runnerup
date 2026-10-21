package org.runnerup.tracking.elevation;

/** Cumulative ascent/descent with a damping threshold to ignore GPS/DEM noise. */
public final class ElevationGain {

  public static final double DEFAULT_THRESHOLD_M = 3.0;

  public static final class Result {
    public final double gainM;
    public final double lossM;

    public Result(double gainM, double lossM) {
      this.gainM = gainM;
      this.lossM = lossM;
    }
  }

  private ElevationGain() {}

  public static Result compute(double[] elevationsM, double thresholdM) {
    return compute(null, elevationsM, thresholdM);
  }

  /**
   * Computes cumulative ascent/descent after smoothing {@code elevationsM} across physical distance
   * {@code distanceM} (when available and >5 points) so slower-pace runs with denser GPS sampling
   * or jitter do not accumulate false gain/loss.
   */
  public static Result compute(double[] distanceM, double[] elevationsM, double thresholdM) {
    if (elevationsM == null || elevationsM.length == 0) {
      return new Result(0, 0);
    }
    double[] series =
        elevationsM.length > 5 ? ElevationSmoother.smooth(distanceM, elevationsM) : elevationsM;
    double gain = 0;
    double loss = 0;
    Double last = null;
    for (double elevation : series) {
      if (Double.isNaN(elevation)) {
        continue;
      }
      if (last == null) {
        last = elevation;
        continue;
      }
      double delta = elevation - last;
      if (Math.abs(delta) >= thresholdM) {
        if (delta > 0) {
          gain += delta;
        } else {
          loss -= delta;
        }
        last = elevation;
      }
    }
    return new Result(gain, loss);
  }
}
