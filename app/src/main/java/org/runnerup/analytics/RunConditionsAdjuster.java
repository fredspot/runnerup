package org.runnerup.analytics;

/**
 * Turns raw pace into a flat, sea-level, mild-temperature, moderate-humidity equivalent so hills,
 * heat, humidity, and high-altitude hypoxia (e.g. running at 2200 m in Mexico) do not obscure real
 * aerobic fitness.
 */
public final class RunConditionsAdjuster {

  /** Minetti cost is stable inside about this grade (rise / run). */
  public static final double GRADE_CLAMP = 0.20;

  /** Energy cost of flat running from the Minetti polynomial. */
  public static final double FLAT_COST = 3.6;

  /** Altitude above sea level (m) where aerobic VO2max loss begins to noticeably slow running. */
  public static final double ALTITUDE_THRESHOLD_M = 300.0;

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
   * Flat-equivalent pace considering both net elevation change and cumulative climb/descent on
   * rolling/out-and-back courses. {@code rawPace} is seconds per kilometre.
   */
  public static double gradeAdjustedPace(
      double rawPaceSecPerKm, double netElevationM, double distanceM) {
    return gradeAdjustedPace(rawPaceSecPerKm, netElevationM, 0.0, distanceM);
  }

  /**
   * Flat-equivalent pace that accounts for both net grade (`netElevationM / distanceM`) and
   * out-and-back / rolling climb (`elevationGainM / distanceM`). On a loop or out-and-back where
   * net elevation is 0 m, climbing and descending still cost extra metabolic energy (~22% per 100 m
   * climbed per km).
   */
  public static double gradeAdjustedPace(
      double rawPaceSecPerKm,
      double netElevationM,
      double elevationGainM,
      double distanceM) {
    if (rawPaceSecPerKm <= 0 || distanceM <= 0) {
      return rawPaceSecPerKm;
    }
    double netCost = energyCost(grade(netElevationM, distanceM));
    double netFactor = netCost > 0 ? (netCost / FLAT_COST) : 1.0;

    // Out-and-back / rolling hills: climb beyond what net elevation already explains.
    double unexplainedClimbM = Math.max(0.0, elevationGainM - Math.max(0.0, netElevationM));
    double rollingGrade = Math.min(0.15, unexplainedClimbM / distanceM);
    double rollingExtraFactor = 1.0 + rollingGrade * 2.2;

    double totalGradeFactor = netFactor * rollingExtraFactor;
    if (totalGradeFactor <= 0) {
      return rawPaceSecPerKm;
    }
    return rawPaceSecPerKm / totalGradeFactor;
  }

  /**
   * Expected slowdown versus mild weather (15°C, 50% RH). Above 15°C, 0.4% per degree. Below 5°C,
   * 0.2% per degree.
   */
  public static double temperatureFactor(Double tempC) {
    return weatherFactor(tempC, null);
  }

  /**
   * Expected slowdown versus mild, moderate-humidity conditions (15°C, 50% RH).
   *
   * <ul>
   *   <li>Temperature: +0.4% per °C above 15°C, +0.2% per °C below 5°C.
   *   <li>Humidity: above 55% RH when warm (&gt;12°C), adds an evaporative cooling penalty scaled
   *       by temperature (`(RH - 55) * 0.0005 * clamp((T - 12)/10, 0.25, 2.0)`).
   * </ul>
   */
  public static double weatherFactor(Double tempC, Double humidityPct) {
    if (tempC == null || tempC.isNaN()) {
      return 1.0;
    }
    double hot = Math.max(0, tempC - 15.0) * 0.004;
    double cold = Math.max(0, 5.0 - tempC) * 0.002;
    double humid = 0.0;
    if (humidityPct != null && !humidityPct.isNaN() && tempC > 12.0 && humidityPct > 55.0) {
      double warmScale = Math.max(0.25, Math.min(2.0, (tempC - 12.0) / 10.0));
      humid = (humidityPct - 55.0) * 0.0005 * warmScale;
    }
    return 1.0 + hot + cold + humid;
  }

  /**
   * Expected aerobic slowdown from reduced partial pressure of oxygen at altitude above sea level
   * (Péronnet et al. 1991 / Daniels):
   *
   * <ul>
   *   <li>Below 300 m ASL: no penalty (`1.0`).
   *   <li>Above 300 m ASL: ~2.6% per 1000 m plus a quadratic term (`0.4% * km^2`), giving ~4.6% at
   *       1600 m (Denver) and ~6.8% at 2240 m (Mexico City).
   * </ul>
   */
  public static double altitudeFactor(Double avgElevationM) {
    if (avgElevationM == null || avgElevationM.isNaN() || avgElevationM <= ALTITUDE_THRESHOLD_M) {
      return 1.0;
    }
    double kmAboveThreshold = Math.min(5.0, (avgElevationM - ALTITUDE_THRESHOLD_M) / 1000.0);
    double penalty = 0.026 * kmAboveThreshold + 0.004 * kmAboveThreshold * kmAboveThreshold;
    return 1.0 + penalty;
  }

  public static boolean hasTemperature(Double tempC) {
    return tempC != null && !tempC.isNaN();
  }

  public static boolean hasHumidity(Double humidityPct) {
    return humidityPct != null && !humidityPct.isNaN();
  }

  public static boolean hasAltitude(Double avgElevationM) {
    return avgElevationM != null && !avgElevationM.isNaN();
  }

  /** Grade adjustment, then the temperature factor is divided out. */
  public static double adjustedPace(
      double rawPaceSecPerKm, double netElevationM, double distanceM, Double tempC) {
    return adjustedPace(rawPaceSecPerKm, netElevationM, 0.0, distanceM, tempC, null, null);
  }

  /** Grade adjustment, then the combined temperature + humidity factor is divided out. */
  public static double adjustedPace(
      double rawPaceSecPerKm,
      double netElevationM,
      double distanceM,
      Double tempC,
      Double humidityPct) {
    return adjustedPace(rawPaceSecPerKm, netElevationM, 0.0, distanceM, tempC, humidityPct, null);
  }

  /**
   * Full environmental normalization: grade (net + rolling climb), temperature + humidity, and
   * altitude above sea level (hypoxia).
   */
  public static double adjustedPace(
      double rawPaceSecPerKm,
      double netElevationM,
      double elevationGainM,
      double distanceM,
      Double tempC,
      Double humidityPct,
      Double avgAltitudeM) {
    double pace = gradeAdjustedPace(rawPaceSecPerKm, netElevationM, elevationGainM, distanceM);
    double envFactor = weatherFactor(tempC, humidityPct) * altitudeFactor(avgAltitudeM);
    if (envFactor <= 0) {
      return pace;
    }
    return pace / envFactor;
  }
}
