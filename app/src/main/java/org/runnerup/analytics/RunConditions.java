package org.runnerup.analytics;

/** Elevation and temperature summary for one activity. */
public final class RunConditions {

  public static final RunConditions EMPTY = new RunConditions(0, 0, null);

  public final double elevationGainM;
  public final double netElevationM;
  public final Double avgTempC;

  public RunConditions(double elevationGainM, double netElevationM, Double avgTempC) {
    this.elevationGainM = elevationGainM;
    this.netElevationM = netElevationM;
    this.avgTempC = avgTempC;
  }

  public double adjustedPaceSecPerKm(double distanceM, long timeSec) {
    double raw = SimilarRunMatcher.paceSecPerKm(distanceM, timeSec);
    return RunConditionsAdjuster.adjustedPace(raw, netElevationM, distanceM, avgTempC);
  }
}
