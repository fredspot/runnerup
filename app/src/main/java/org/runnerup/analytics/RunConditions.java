package org.runnerup.analytics;

/** Elevation, temperature, and relative humidity summary for one activity. */
public final class RunConditions {

  public static final RunConditions EMPTY = new RunConditions(0, 0, null, null);

  public final double elevationGainM;
  public final double netElevationM;
  public final Double avgTempC;
  public final Double avgHumidityPct;

  public RunConditions(double elevationGainM, double netElevationM, Double avgTempC) {
    this(elevationGainM, netElevationM, avgTempC, null);
  }

  public RunConditions(
      double elevationGainM, double netElevationM, Double avgTempC, Double avgHumidityPct) {
    this.elevationGainM = elevationGainM;
    this.netElevationM = netElevationM;
    this.avgTempC = avgTempC;
    this.avgHumidityPct = avgHumidityPct;
  }

  public double adjustedPaceSecPerKm(double distanceM, long timeSec) {
    double raw = SimilarRunMatcher.paceSecPerKm(distanceM, timeSec);
    return RunConditionsAdjuster.adjustedPace(
        raw, netElevationM, distanceM, avgTempC, avgHumidityPct);
  }
}
