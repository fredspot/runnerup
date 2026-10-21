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
    if (currentDistanceM <= 0 || otherDistanceM <= 0 || currentTimeSec <= 0 || otherTimeSec <= 0) {
      return false;
    }
    double distErr = Math.abs(currentDistanceM - otherDistanceM) / currentDistanceM;
    if (distErr > DISTANCE_TOLERANCE) {
      return false;
    }
    double currentPace = paceSecPerKm(currentDistanceM, currentTimeSec);
    double otherPace = paceSecPerKm(otherDistanceM, otherTimeSec);
    if (currentPace <= 0 || otherPace <= 0) {
      return false;
    }
    return Math.abs(currentPace - otherPace) / currentPace <= PACE_TOLERANCE;
  }

  /** Combined relative error; lower is a closer match. */
  public static double similarityScore(
      double currentDistanceM,
      long currentTimeSec,
      double otherDistanceM,
      long otherTimeSec) {
    if (!isSimilar(currentDistanceM, currentTimeSec, otherDistanceM, otherTimeSec)) {
      return Double.POSITIVE_INFINITY;
    }
    double distErr = Math.abs(currentDistanceM - otherDistanceM) / currentDistanceM;
    double currentPace = paceSecPerKm(currentDistanceM, currentTimeSec);
    double otherPace = paceSecPerKm(otherDistanceM, otherTimeSec);
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
