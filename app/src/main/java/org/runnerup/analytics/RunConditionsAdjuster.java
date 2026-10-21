package org.runnerup.analytics;

/**
 * Turns raw pace into a flat, mild-temperature equivalent so hills and heat do not look like
 * fitness.
 */
public final class RunConditionsAdjuster {

  /** Minetti cost is stable inside about this grade (rise / run). */
  public static final double GRADE_CLAMP = 0.20;

  /** Energy cost of flat running from the Minetti polynomial. */
  public static final double FLAT_COST = 3.6;

  private RunConditionsAdjuster() {}

  public static double clampGrade(double grade) {
    if (Double.isNaN(grade)) {
      return 0;
    }
    return Math.max(-GRADE_CLAMP, Math.min(GRADE_CLAMP, grade));
  }

  /** Rise over run. Zero when the window has no length. */
  public static double grade(double netElevationM, double distanceM) {
    if (distanceM <= 0 || Double.isNaN(netElevationM)) {
      return 0;
    }
    return clampGrade(netElevationM / distanceM);
  }

  /** Minetti 2002 energy cost for a grade expressed as rise/run. */
  public static double energyCost(double grade) {
    double g = clampGrade(grade);
    return 155.4 * Math.pow(g, 5)
        - 30.4 * Math.pow(g, 4)
        - 43.3 * Math.pow(g, 3)
        + 46.3 * Math.pow(g, 2)
        + 19.5 * g
        + FLAT_COST;
  }

  /**
   * Flat-equivalent pace. Uphill effort comes out faster than the watch pace. {@code rawPace} is
   * seconds per kilometre.
   */
  public static double gradeAdjustedPace(
      double rawPaceSecPerKm, double netElevationM, double distanceM) {
    if (rawPaceSecPerKm <= 0) {
      return rawPaceSecPerKm;
    }
    double cost = energyCost(grade(netElevationM, distanceM));
    if (cost <= 0) {
      return rawPaceSecPerKm;
    }
    return rawPaceSecPerKm * (FLAT_COST / cost);
  }

  /**
   * Expected slowdown versus mild weather. Above 15°C, 0.4% per degree. Below 5°C, 0.2% per
   * degree. Missing temperature does not change pace.
   */
  public static double temperatureFactor(Double tempC) {
    if (tempC == null || tempC.isNaN()) {
      return 1.0;
    }
    double hot = Math.max(0, tempC - 15.0) * 0.004;
    double cold = Math.max(0, 5.0 - tempC) * 0.002;
    return 1.0 + hot + cold;
  }

  public static boolean hasTemperature(Double tempC) {
    return tempC != null && !tempC.isNaN();
  }

  /** Grade adjustment, then the temperature factor is divided out. */
  public static double adjustedPace(
      double rawPaceSecPerKm, double netElevationM, double distanceM, Double tempC) {
    double pace = gradeAdjustedPace(rawPaceSecPerKm, netElevationM, distanceM);
    double factor = temperatureFactor(tempC);
    if (factor <= 0) {
      return pace;
    }
    return pace / factor;
  }
}
