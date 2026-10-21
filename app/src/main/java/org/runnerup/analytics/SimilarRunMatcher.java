package org.runnerup.analytics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.runnerup.data.RunningActivityReader.ActivityRow;

/** Matches activities with similar distance and pace (raw or conditions-adjusted) for comparison. */
public final class SimilarRunMatcher {

  public static final double DISTANCE_TOLERANCE = 0.10;
  public static final double PACE_TOLERANCE = 0.10;
  public static final int MAX_RESULTS = 10;

  private SimilarRunMatcher() {}

  /** Seconds per kilometre. Zero when distance or time is missing. */
  public static double paceSecPerKm(double distanceM, long timeSec) {
    if (distanceM <= 0 || timeSec <= 0) {
      return 0;
    }
    return timeSec / (distanceM / 1000.0);
  }

  public static boolean isSimilar(
      double currentDistanceM,
      long currentTimeSec,
      double otherDistanceM,
      long otherTimeSec) {
    return isWithinTolerance(
        currentDistanceM,
        currentTimeSec,
        otherDistanceM,
        otherTimeSec,
        DISTANCE_TOLERANCE,
        PACE_TOLERANCE);
  }

  public static boolean isWithinTolerance(
      double currentDistanceM,
      long currentTimeSec,
      double otherDistanceM,
      long otherTimeSec,
      double distTol,
      double paceTol) {
    if (currentDistanceM <= 0 || otherDistanceM <= 0 || currentTimeSec <= 0 || otherTimeSec <= 0) {
      return false;
    }
    double distErr = Math.abs(currentDistanceM - otherDistanceM) / currentDistanceM;
    if (distErr > distTol) {
      return false;
    }
    double currentPace = paceSecPerKm(currentDistanceM, currentTimeSec);
    double otherPace = paceSecPerKm(otherDistanceM, otherTimeSec);
    if (currentPace <= 0 || otherPace <= 0) {
      return false;
    }
    return Math.abs(currentPace - otherPace) / currentPace <= paceTol;
  }

  /**
   * True when distance is within {@code distTol} and EITHER raw pace OR conditions-adjusted pace
   * (weather + humidity + hills + altitude) is within {@code paceTol}. This allows a run in
   * 30°C/80% humidity or at 2200 m altitude in Mexico to match the same aerobic effort at sea
   * level.
   */
  public static boolean isWithinSmartTolerance(
      ActivityRow current,
      ActivityRow other,
      Map<Long, RunConditions> conditions,
      double distTol,
      double paceTol) {
    if (current == null
        || other == null
        || current.totalDistance <= 0
        || other.totalDistance <= 0
        || current.totalTime <= 0
        || other.totalTime <= 0) {
      return false;
    }
    double distErr = Math.abs(current.totalDistance - other.totalDistance) / current.totalDistance;
    if (distErr > distTol) {
      return false;
    }
    double curRaw = paceSecPerKm(current.totalDistance, current.totalTime);
    double othRaw = paceSecPerKm(other.totalDistance, other.totalTime);
    if (curRaw <= 0 || othRaw <= 0) {
      return false;
    }
    if (Math.abs(curRaw - othRaw) / curRaw <= paceTol) {
      return true;
    }
    double curAdj = adjustedPaceFor(current, conditions);
    double othAdj = adjustedPaceFor(other, conditions);
    if (curAdj > 0 && othAdj > 0 && Math.abs(curAdj - othAdj) / curAdj <= paceTol) {
      return true;
    }
    return false;
  }

  private static double adjustedPaceFor(ActivityRow row, Map<Long, RunConditions> conditions) {
    if (conditions != null && conditions.containsKey(row.activityId)) {
      return conditions.get(row.activityId).adjustedPaceSecPerKm(row.totalDistance, row.totalTime);
    }
    return paceSecPerKm(row.totalDistance, row.totalTime);
  }

  /** Combined relative error within strict 10% tolerances; lower is a closer match. */
  public static double similarityScore(
      double currentDistanceM,
      long currentTimeSec,
      double otherDistanceM,
      long otherTimeSec) {
    if (!isSimilar(currentDistanceM, currentTimeSec, otherDistanceM, otherTimeSec)) {
      return Double.POSITIVE_INFINITY;
    }
    return rawDistanceAndPaceError(currentDistanceM, currentTimeSec, otherDistanceM, otherTimeSec);
  }

  /** Unclamped relative error across distance + pace so any pair of runs can be ranked. */
  public static double rawDistanceAndPaceError(
      double currentDistanceM,
      long currentTimeSec,
      double otherDistanceM,
      long otherTimeSec) {
    if (currentDistanceM <= 0 || otherDistanceM <= 0 || currentTimeSec <= 0 || otherTimeSec <= 0) {
      return Double.POSITIVE_INFINITY;
    }
    double distErr = Math.abs(currentDistanceM - otherDistanceM) / currentDistanceM;
    double currentPace = paceSecPerKm(currentDistanceM, currentTimeSec);
    double otherPace = paceSecPerKm(otherDistanceM, otherTimeSec);
    if (currentPace <= 0 || otherPace <= 0) {
      return Double.POSITIVE_INFINITY;
    }
    double paceErr = Math.abs(currentPace - otherPace) / currentPace;
    return distErr + paceErr;
  }

  /**
   * Smart distance + effort error that uses the better of raw-pace closeness and
   * conditions-adjusted-pace closeness (plus a small HR-zone closeness tiebreaker when HR is
   * available).
   */
  public static double smartClosenessScore(
      ActivityRow current, ActivityRow other, Map<Long, RunConditions> conditions) {
    if (current == null || other == null) {
      return Double.POSITIVE_INFINITY;
    }
    double rawErr =
        rawDistanceAndPaceError(
            current.totalDistance, current.totalTime, other.totalDistance, other.totalTime);
    if (Double.isInfinite(rawErr)) {
      return rawErr;
    }
    double distErr = Math.abs(current.totalDistance - other.totalDistance) / current.totalDistance;
    double curAdj = adjustedPaceFor(current, conditions);
    double othAdj = adjustedPaceFor(other, conditions);
    double adjPaceErr =
        (curAdj > 0 && othAdj > 0) ? Math.abs(curAdj - othAdj) / curAdj : Double.POSITIVE_INFINITY;
    double rawPaceErr = rawErr - distErr;
    double bestPaceErr = Math.min(rawPaceErr, adjPaceErr);
    double condPenalty = 0.0;
    if (conditions != null) {
      RunConditions c1 = conditions.get(current.activityId);
      RunConditions c2 = conditions.get(other.activityId);
      if (c1 != null && c2 != null) {
        double km1 = Math.max(1.0, current.totalDistance / 1000.0);
        double km2 = Math.max(1.0, other.totalDistance / 1000.0);
        double netPerKm1 = c1.netElevationM / km1;
        double netPerKm2 = c2.netElevationM / km2;
        double elevChangeDiff =
            RunConditionsAdjuster.effectiveElevationChangeDiffM(netPerKm1, netPerKm2);
        double altDiff =
            Math.abs(
                RunConditionsAdjuster.effectiveAltitudeM(c1.avgElevationM)
                    - RunConditionsAdjuster.effectiveAltitudeM(c2.avgElevationM));
        double tempDiff =
            (c1.avgTempC != null && c2.avgTempC != null)
                ? Math.abs(c1.avgTempC - c2.avgTempC)
                : 0.0;
        double humDiff =
            (c1.avgHumidityPct != null && c2.avgHumidityPct != null)
                ? Math.abs(c1.avgHumidityPct - c2.avgHumidityPct)
                : 0.0;
        // Priority order: Pace (1.0) > ΔElev (0.70) > Altitude >500m (0.45) > Temp (0.25) > Humidity (0.12)
        condPenalty =
            0.07 * (elevChangeDiff / 10.0)
                + 0.045 * (altDiff / 500.0)
                + 0.025 * (tempDiff / 10.0)
                + 0.012 * (humDiff / 25.0);
      }
    }
    double hrBonus = 0.0;
    if (current.avgHr > 0 && other.avgHr > 0) {
      hrBonus = 0.10 * (Math.abs(current.avgHr - other.avgHr) / (double) current.avgHr);
    }
    return bestPaceErr + 0.65 * distErr + condPenalty + hrBonus;
  }

  public static List<ActivityRow> findSimilar(ActivityRow current, List<ActivityRow> candidates) {
    if (current == null || candidates == null || candidates.isEmpty()) {
      return Collections.emptyList();
    }
    List<ActivityRow> matched = new ArrayList<>();
    for (ActivityRow row : candidates) {
      if (row == null || row.activityId == current.activityId) {
        continue;
      }
      if (isSimilar(current.totalDistance, current.totalTime, row.totalDistance, row.totalTime)) {
        matched.add(row);
      }
    }
    return matched;
  }

  public static List<ActivityRow> findBestComparable(
      ActivityRow current, List<ActivityRow> allCandidates) {
    return findBestComparable(current, allCandidates, null);
  }

  /**
   * Smart tiered matching using both raw and conditions-adjusted pace (heat, humidity, hills, and
   * altitude above sea level):
   *
   * <ol>
   *   <li>Strict: ±12% distance and ±10% pace (raw or conditions-adjusted).
   *   <li>Broadened: ±25% distance and ±20% pace (raw or conditions-adjusted).
   *   <li>Closest overall: top {@link #MAX_RESULTS} runs sorted by {@link #smartClosenessScore}.
   * </ol>
   */
  public static List<ActivityRow> findBestComparable(
      ActivityRow current,
      List<ActivityRow> allCandidates,
      Map<Long, RunConditions> conditions) {
    if (current == null || allCandidates == null || allCandidates.isEmpty()) {
      return Collections.emptyList();
    }
    List<ActivityRow> strict = new ArrayList<>();
    for (ActivityRow row : allCandidates) {
      if (row == null || row.activityId == current.activityId) continue;
      if (isWithinSmartTolerance(current, row, conditions, 0.12, 0.10)) {
        strict.add(row);
      }
    }
    if (!strict.isEmpty() && (conditions == null || strict.size() >= 8)) {
      return sortBySmartCloseness(current, strict, conditions);
    }
    List<ActivityRow> broadened = new ArrayList<>(strict);
    for (ActivityRow row : allCandidates) {
      if (row == null || row.activityId == current.activityId) continue;
      if (strict.contains(row)) continue;
      if (isWithinSmartTolerance(current, row, conditions, 0.25, 0.20)) {
        broadened.add(row);
      }
    }
    if (!broadened.isEmpty()) {
      return sortBySmartCloseness(current, broadened, conditions);
    }
    List<ActivityRow> pool = new ArrayList<>();
    for (ActivityRow row : allCandidates) {
      if (row == null || row.activityId == current.activityId) continue;
      if (row.totalDistance > 0 && row.totalTime > 0) {
        pool.add(row);
      }
    }
    return cap(sortBySmartCloseness(current, pool, conditions), MAX_RESULTS);
  }

  public static List<ActivityRow> sortByCloseness(
      ActivityRow current, List<ActivityRow> matches) {
    return sortBySmartCloseness(current, matches, null);
  }

  public static List<ActivityRow> sortBySmartCloseness(
      ActivityRow current,
      List<ActivityRow> matches,
      Map<Long, RunConditions> conditions) {
    if (current == null || matches == null || matches.isEmpty()) {
      return Collections.emptyList();
    }
    List<ActivityRow> sorted = new ArrayList<>(matches);
    Collections.sort(
        sorted,
        (a, b) ->
            Double.compare(
                smartClosenessScore(current, a, conditions),
                smartClosenessScore(current, b, conditions)));
    return sorted;
  }

  public static List<ActivityRow> rankSimilar(ActivityRow current, List<ActivityRow> candidates) {
    return cap(sortBySimilarity(current, findSimilar(current, candidates)), MAX_RESULTS);
  }

  public static List<ActivityRow> sortBySimilarity(
      ActivityRow current, List<ActivityRow> matches) {
    if (current == null || matches == null || matches.isEmpty()) {
      return Collections.emptyList();
    }
    List<ActivityRow> sorted = new ArrayList<>(matches);
    Collections.sort(
        sorted,
        (a, b) ->
            Double.compare(
                similarityScore(
                    current.totalDistance, current.totalTime, a.totalDistance, a.totalTime),
                similarityScore(
                    current.totalDistance, current.totalTime, b.totalDistance, b.totalTime)));
    return sorted;
  }

  public static List<ActivityRow> cap(List<ActivityRow> rows, int max) {
    if (rows.size() <= max) {
      return rows;
    }
    return new ArrayList<>(rows.subList(0, max));
  }
}
