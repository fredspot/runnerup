package org.runnerup.core.util;

import org.runnerup.data.entities.LocationEntity;

/** Lap marker info strings shared by map implementations. */
public final class RouteMarkerLabels {

  private RouteMarkerLabels() {}

  public static String lapMarkerInfo(
      Formatter formatter, LocationEntity loc, Double lapDistanceM) {
    long meters = lapDistanceM != null ? Math.round(lapDistanceM) : 0;
    return formatter.formatDistance(Formatter.Format.TXT_SHORT, meters)
        + " "
        + formatter.formatElapsedTime(
            Formatter.Format.TXT_SHORT, Math.round(loc.getElapsed() / 1000.0));
  }
}
