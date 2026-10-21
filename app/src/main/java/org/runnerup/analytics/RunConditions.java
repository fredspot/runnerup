package org.runnerup.analytics;

/**
 * Elevation (gain, net, and average altitude above sea level), temperature, and relative humidity
 * summary for one activity.
 */
public final class RunConditions {

  public static final RunConditions EMPTY = new RunConditions(0, 0, null, null, null);

  public final double elevationGainM;
  public final double netElevationM;
  public final Double avgElevationM;
  public final Double avgTempC;
  public final Double avgHumidityPct;

  public RunConditions(double elevationGainM, double netElevationM, Double avgTempC) {
    this(elevationGainM, netElevationM, null, avgTempC, null);
  }

  public RunConditions(
      double elevationGainM, double netElevationM, Double avgTempC, Double avgHumidityPct) {
    this(elevationGainM, netElevationM, null, avgTempC, avgHumidityPct);
  }

  public RunConditions(
      double elevationGainM,
      double netElevationM,
      Double avgElevationM,
      Double avgTempC,
      Double avgHumidityPct) {
    this.elevationGainM = elevationGainM;
    this.netElevationM = netElevationM;
    this.avgElevationM = avgElevationM;
    this.avgTempC = avgTempC;
    this.avgHumidityPct = avgHumidityPct;
  }

  public double adjustedPaceSecPerKm(double distanceM, long timeSec) {
    double raw = SimilarRunMatcher.paceSecPerKm(distanceM, timeSec);
    return RunConditionsAdjuster.adjustedPace(
        raw, netElevationM, elevationGainM, distanceM, avgTempC, avgHumidityPct, avgElevationM);
  }
}
