package org.runnerup.analytics;

/** One non-overlapping kilometre and the heart-rate zones of the two kilometres before it. */
public final class ComparableSegment {

  public final long activityId;
  /** Zero-based window. 0 is kilometres 0–1. */
  public final int windowIndex;
  public final double paceSecPerKm;
  public final double adjustedPaceSecPerKm;
  public final int avgHr;
  public final double netElevationM;
  public final Double avgTempC;
  public final Double avgHumidityPct;
  /** Zone of the immediately previous kilometre. 0 when this is the first kilometre. */
  public final int prevZoneNear;
  /** Zone two kilometres back. 0 when this window is in the first two kilometres. */
  public final int prevZoneFar;

  public ComparableSegment(
      long activityId,
      int windowIndex,
      double paceSecPerKm,
      double adjustedPaceSecPerKm,
      int avgHr,
      double netElevationM,
      Double avgTempC,
      int prevZoneNear,
      int prevZoneFar) {
    this(
        activityId,
        windowIndex,
        paceSecPerKm,
        adjustedPaceSecPerKm,
        avgHr,
        netElevationM,
        avgTempC,
        null,
        prevZoneNear,
        prevZoneFar);
  }

  public ComparableSegment(
      long activityId,
      int windowIndex,
      double paceSecPerKm,
      double adjustedPaceSecPerKm,
      int avgHr,
      double netElevationM,
      Double avgTempC,
      Double avgHumidityPct,
      int prevZoneNear,
      int prevZoneFar) {
    this.activityId = activityId;
    this.windowIndex = windowIndex;
    this.paceSecPerKm = paceSecPerKm;
    this.adjustedPaceSecPerKm = adjustedPaceSecPerKm;
    this.avgHr = avgHr;
    this.netElevationM = netElevationM;
    this.avgTempC = avgTempC;
    this.avgHumidityPct = avgHumidityPct;
    this.prevZoneNear = prevZoneNear;
    this.prevZoneFar = prevZoneFar;
  }

  public boolean isStartOfRun() {
    return prevZoneNear == 0 && prevZoneFar == 0;
  }
}
