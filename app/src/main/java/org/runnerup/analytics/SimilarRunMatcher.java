package org.runnerup.analytics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.runnerup.data.RunningActivityReader.ActivityRow;

/** Matches activities with similar distance and pace for run-to-run comparison. */
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

  /**
   * Smart tiered matching so the Compare tab is never empty when past runs exist:
   *
   * <ol>
   *   <li>Strict: ±10% distance and ±10% pace.
   *   <li>Broadened: ±25% distance and ±20% pace.
   *   <li>Closest overall: top {@link #MAX_RESULTS} runs sorted by combined distance + pace error.
   * </ol>
   */
  public static List<ActivityRow> findBestComparable(
      ActivityRow current, List<ActivityRow> allCandidates) {
    if (current == null || allCandidates == null || allCandidates.isEmpty()) {
      return Collections.emptyList();
    }
    List<ActivityRow> strict = findSimilar(current, allCandidates);
    if (!strict.isEmpty()) {
      return sortByCloseness(current, strict);
    }
    List<ActivityRow> broadened = new ArrayList<>();
    for (ActivityRow row : allCandidates) {
      if (row == null || row.activityId == current.activityId) continue;
      if (isWithinTolerance(
          current.totalDistance,
          current.totalTime,
          row.totalDistance,
          row.totalTime,
          0.25,
          0.20)) {
        broadened.add(row);
      }
    }
    if (!broadened.isEmpty()) {
      return sortByCloseness(current, broadened);
    }
    List<ActivityRow> pool = new ArrayList<>();
    for (ActivityRow row : allCandidates) {
      if (row == null || row.activityId == current.activityId) continue;
      if (row.totalDistance > 0 && row.totalTime > 0) {
        pool.add(row);
      }
    }
    return cap(sortByCloseness(current, pool), MAX_RESULTS);
  }

  public static List<ActivityRow> sortByCloseness(
      ActivityRow current, List<ActivityRow> matches) {
    if (current == null || matches == null || matches.isEmpty()) {
      return Collections.emptyList();
    }
    List<ActivityRow> sorted = new ArrayList<>(matches);
    Collections.sort(
        sorted,
        (a, b) ->
            Double.compare(
                rawDistanceAndPaceError(
                    current.totalDistance, current.totalTime, a.totalDistance, a.totalTime),
                rawDistanceAndPaceError(
                    current.totalDistance, current.totalTime, b.totalDistance, b.totalTime)));
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
