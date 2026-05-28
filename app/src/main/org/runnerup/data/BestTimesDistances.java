package org.runnerup.data;

/** Standard race distances used for best-times and distribution views. */
public final class BestTimesDistances {

  public static final int[] TARGET_DISTANCES = {
    1000, 5000, 10000, 15000, 20000, 21097, 30000, 40000, 42195
  };

  private static final String[] DISTANCE_LABELS = {
    "1 km",
    "5 km",
    "10 km",
    "15 km",
    "20 km",
    "Half Marathon",
    "30 km",
    "40 km",
    "Marathon"
  };

  private BestTimesDistances() {}

  public static String getLabel(int distanceMeters) {
    for (int i = 0; i < TARGET_DISTANCES.length; i++) {
      if (TARGET_DISTANCES[i] == distanceMeters) {
        return DISTANCE_LABELS[i];
      }
    }
    if (distanceMeters >= 1000) {
      return String.format("%.1f km", distanceMeters / 1000.0);
    }
    return distanceMeters + " m";
  }

  public static int indexOf(int distanceMeters) {
    for (int i = 0; i < TARGET_DISTANCES.length; i++) {
      if (TARGET_DISTANCES[i] == distanceMeters) {
        return i;
      }
    }
    return -1;
  }
}
