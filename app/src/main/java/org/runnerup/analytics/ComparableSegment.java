package org.runnerup.analytics;

/**
 * One non-overlapping kilometre (or trailing partial segment >= 400 m) with pace, elevation change
 * per km, altitude, weather, activity timestamp, and prior heart-rate zones.
 */
public final class ComparableSegment {

  public final long activityId;
  /** Zero-based window. 0 is kilometres 0–1. */
  public final int windowIndex;
  public final double paceSecPerKm;
  public final double adjustedPaceSecPerKm;
  public final int avgHr;
  /** Net elevation change normalized per 1 km (`m / km`). */
  public final double netElevationM;
  public final Double avgElevationM;
  public final Double avgTempC;
  public final Double avgHumidityPct;
  /** Zone of the immediately previous kilometre. 0 when this is the first kilometre. */
  public final int prevZoneNear;
  /** Zone two kilometres back. 0 when this window is in the first two kilometres. */
  public final int prevZoneFar;
  /** Epoch seconds when this segment started (used for This Year vs All Years BPM engine). */
  public final long startTimeSec;
  /** Actual segment length in meters (1000.0 for full km segments, or 400..999 m for tail). */
  public final double distanceM;

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
        null,
        avgTempC,
        null,
        prevZoneNear,
        prevZoneFar,
        0L,
        1000.0);
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
    this(
        activityId,
        windowIndex,
        paceSecPerKm,
        adjustedPaceSecPerKm,
        avgHr,
        netElevationM,
        null,
        avgTempC,
        avgHumidityPct,
        prevZoneNear,
        prevZoneFar,
        0L,
        1000.0);
  }

  public ComparableSegment(
      long activityId,
      int windowIndex,
      double paceSecPerKm,
      double adjustedPaceSecPerKm,
      int avgHr,
      double netElevationM,
      Double avgElevationM,
      Double avgTempC,
      Double avgHumidityPct,
      int prevZoneNear,
      int prevZoneFar) {
    this(
        activityId,
        windowIndex,
        paceSecPerKm,
        adjustedPaceSecPerKm,
        avgHr,
        netElevationM,
        avgElevationM,
        avgTempC,
        avgHumidityPct,
        prevZoneNear,
        prevZoneFar,
        0L,
        1000.0);
  }

  public ComparableSegment(
      long activityId,
      int windowIndex,
      double paceSecPerKm,
      double adjustedPaceSecPerKm,
      int avgHr,
      double netElevationM,
      Double avgElevationM,
      Double avgTempC,
      Double avgHumidityPct,
      int prevZoneNear,
      int prevZoneFar,
      long startTimeSec,
      double distanceM) {
    this.activityId = activityId;
    this.windowIndex = windowIndex;
    this.paceSecPerKm = paceSecPerKm;
    this.adjustedPaceSecPerKm = adjustedPaceSecPerKm;
    this.avgHr = avgHr;
    this.netElevationM = netElevationM;
    this.avgElevationM = avgElevationM;
    this.avgTempC = avgTempC;
    this.avgHumidityPct = avgHumidityPct;
    this.prevZoneNear = prevZoneNear;
    this.prevZoneFar = prevZoneFar;
    this.startTimeSec = startTimeSec;
    this.distanceM = distanceM > 0 ? distanceM : 1000.0;
  }

  public boolean isStartOfRun() {
    return prevZoneNear == 0 && prevZoneFar == 0;
  }
}
