package org.runnerup.analytics;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.runnerup.data.RunningActivityReader.ActivityRow;

/**
 * Interpretable heart-performance numbers versus comparable runs.
 *
 * <p>When environmental conditions (`RunConditions`: grade, temperature, humidity, and altitude
 * above sea level) are available, each peer run's heart rate is normalized to the current run's
 * conditions-adjusted pace using cardiac cost (`beats per adjusted km`), so a slower raw pace in
 * Mexico altitude or summer heat is evaluated fairly against a cool sea-level run.
 */
public final class RunEngineStats {

  public static final int MIN_SIMILAR_FOR_PERCENTILE = 3;

  public static final class Snapshot {
    public final int similarWithHrAll;
    public final int similarWithHrYear;
    public final Integer percentileAll;
    public final Integer percentileYear;
    public final Integer meanHrAll;
    public final Integer meanHrYear;
    public final Integer deltaVsAll;
    public final Integer deltaVsYear;

    Snapshot(
        int similarWithHrAll,
        int similarWithHrYear,
        Integer percentileAll,
        Integer percentileYear,
        Integer meanHrAll,
        Integer meanHrYear,
        Integer deltaVsAll,
        Integer deltaVsYear) {
      this.similarWithHrAll = similarWithHrAll;
      this.similarWithHrYear = similarWithHrYear;
      this.percentileAll = percentileAll;
      this.percentileYear = percentileYear;
      this.meanHrAll = meanHrAll;
      this.meanHrYear = meanHrYear;
      this.deltaVsAll = deltaVsAll;
      this.deltaVsYear = deltaVsYear;
    }

    public boolean hasHeadline() {
      return percentileAll != null || meanHrAll != null;
    }
  }

  private RunEngineStats() {}

  public static Snapshot compute(ActivityRow current, List<ActivityRow> similar) {
    return compute(current, similar, null);
  }

  public static Snapshot compute(
      ActivityRow current,
      List<ActivityRow> similar,
      Map<Long, RunConditions> conditions) {
    if (current == null || current.avgHr <= 0 || similar == null) {
      return empty();
    }
    int year = yearFromEpochSeconds(current.startTime);
    double curAdjPace = adjustedPace(current, conditions);
    List<Integer> allHr = new ArrayList<>();
    List<Integer> yearHr = new ArrayList<>();
    for (ActivityRow row : similar) {
      if (row == null || row.avgHr <= 0 || row.activityId == current.activityId) {
        continue;
      }
      int eqHr = equivalentHrAtCurrentEffort(row, curAdjPace, conditions);
      allHr.add(eqHr);
      if (yearFromEpochSeconds(row.startTime) == year) {
        yearHr.add(eqHr);
      }
    }
    Integer meanAll = mean(allHr);
    Integer meanYear = mean(yearHr);
    return new Snapshot(
        allHr.size(),
        yearHr.size(),
        percentileLowerIsBetter(current.avgHr, allHr),
        percentileLowerIsBetter(current.avgHr, yearHr),
        meanAll,
        meanYear,
        meanAll == null ? null : current.avgHr - meanAll,
        meanYear == null ? null : current.avgHr - meanYear);
  }

  /**
   * Projects a peer run's average HR to the current run's conditions-adjusted speed (`curAdjPace`)
   * via cardiac cost (`HR * adjPace`), clamped to ±15% so extreme pace gaps don't distort BPM.
   * When conditions are not provided, returns {@code peer.avgHr} unchanged.
   */
  private static int equivalentHrAtCurrentEffort(
      ActivityRow peer, double curAdjPace, Map<Long, RunConditions> conditions) {
    if (conditions == null || curAdjPace <= 0) {
      return peer.avgHr;
    }
    double peerAdjPace = adjustedPace(peer, conditions);
    if (peerAdjPace <= 0) {
      return peer.avgHr;
    }
    double speedRatio = Math.max(0.85, Math.min(1.15, peerAdjPace / curAdjPace));
    return (int) Math.round(peer.avgHr * speedRatio);
  }

  private static double adjustedPace(ActivityRow row, Map<Long, RunConditions> conditions) {
    if (conditions != null && conditions.containsKey(row.activityId)) {
      return conditions.get(row.activityId).adjustedPaceSecPerKm(row.totalDistance, row.totalTime);
    }
    return SimilarRunMatcher.paceSecPerKm(row.totalDistance, row.totalTime);
  }

  /** Share of peers with a higher (worse) HR, 0–100. Null when the sample is too small. */
  public static Integer percentileLowerIsBetter(int thisValue, List<Integer> peers) {
    if (peers == null || peers.size() < MIN_SIMILAR_FOR_PERCENTILE) {
      return null;
    }
    int betterThan = 0;
    for (int peer : peers) {
      if (peer > thisValue) {
        betterThan++;
      }
    }
    return Math.round(100f * betterThan / peers.size());
  }

  public static Integer mean(List<Integer> values) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    long sum = 0;
    for (int v : values) {
      sum += v;
    }
    return (int) Math.round(sum / (double) values.size());
  }

  public static int yearFromEpochSeconds(long startTimeSec) {
    Calendar cal = Calendar.getInstance(Locale.US);
    cal.setTimeInMillis(startTimeSec * 1000L);
    return cal.get(Calendar.YEAR);
  }

  private static Snapshot empty() {
    return new Snapshot(0, 0, null, null, null, null, null, null);
  }
}
