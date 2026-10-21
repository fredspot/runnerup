package org.runnerup.analytics;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.runnerup.data.RunningActivityReader.ActivityRow;

/**
 * Interpretable heart-performance numbers versus comparable 1 km segments (or whole runs when
 * segments are unavailable).
 *
 * <p>Controls for pace, elevation change (±10 m/km flat bucket), altitude (&lt;500 m all same),
 * temperature, and humidity at the 1 km segment level so runs of any length (e.g. 1.43 km vs
 * 3.30 km) at the same pace and conditions are evaluated against the exact same historical pool of
 * 1 km segments.
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

  /**
   * Computes the BPM engine snapshot ("Better than X%", usual BPM, delta BPM, and match counts for
   * This Year and All Years) directly from controlled 1 km segments.
   *
   * <p>Each segment of the current run is matched against historical 1 km segments using {@link
   * SegmentComparer#findControlledMatches} (1. Pace, 2. ±10m Elevation Change, 3. &lt;500m
   * Altitude, 4. Temperature, 5. Humidity) and weighted by segment distance (`distanceM`).
   */
  public static Snapshot computeFromSegments(
      ActivityRow current,
      List<ComparableSegment> currentSegments,
      List<ComparableSegment> historicalSegments,
      List<ActivityRow> fallbackSimilarRuns,
      Map<Long, RunConditions> conditions) {
    if (current == null || current.avgHr <= 0) {
      return empty();
    }
    if (currentSegments == null
        || currentSegments.isEmpty()
        || historicalSegments == null
        || historicalSegments.isEmpty()) {
      return compute(current, fallbackSimilarRuns, conditions);
    }

    int targetYear = yearFromEpochSeconds(current.startTime);

    double totalWeightAll = 0.0;
    double weightedBetterAll = 0.0;
    double weightedMeanAllSum = 0.0;
    double weightedThisHrAllSum = 0.0;
    int totalMatchedPairsAll = 0;

    double totalWeightYear = 0.0;
    double weightedBetterYear = 0.0;
    double weightedMeanYearSum = 0.0;
    double weightedThisHrYearSum = 0.0;
    int totalMatchedPairsYear = 0;

    for (ComparableSegment seg : currentSegments) {
      if (seg == null) continue;
      int segHr = seg.avgHr > 0 ? seg.avgHr : current.avgHr;
      if (segHr <= 0) continue;

      List<ComparableSegment> matches =
          SegmentComparer.findControlledMatches(seg, historicalSegments);
      if (matches.isEmpty()) continue;

      List<Integer> peerHrAll = new ArrayList<>();
      List<Integer> peerHrYear = new ArrayList<>();
      for (ComparableSegment peer : matches) {
        if (peer == null || peer.avgHr <= 0) continue;
        peerHrAll.add(peer.avgHr);
        if (peer.startTimeSec > 0 && yearFromEpochSeconds(peer.startTimeSec) == targetYear) {
          peerHrYear.add(peer.avgHr);
        }
      }

      double segWeight = Math.max(400.0, seg.distanceM);

      if (!peerHrAll.isEmpty()) {
        totalMatchedPairsAll += peerHrAll.size();
        int betterCount = 0;
        long sumHr = 0;
        for (int hr : peerHrAll) {
          if (hr > segHr) betterCount++;
          sumHr += hr;
        }
        double segBetterRatio = betterCount / (double) peerHrAll.size();
        double segMeanHr = sumHr / (double) peerHrAll.size();
        totalWeightAll += segWeight;
        weightedBetterAll += segWeight * segBetterRatio;
        weightedMeanAllSum += segWeight * segMeanHr;
        weightedThisHrAllSum += segWeight * segHr;
      }

      if (!peerHrYear.isEmpty()) {
        totalMatchedPairsYear += peerHrYear.size();
        int betterCount = 0;
        long sumHr = 0;
        for (int hr : peerHrYear) {
          if (hr > segHr) betterCount++;
          sumHr += hr;
        }
        double segBetterRatio = betterCount / (double) peerHrYear.size();
        double segMeanHr = sumHr / (double) peerHrYear.size();
        totalWeightYear += segWeight;
        weightedBetterYear += segWeight * segBetterRatio;
        weightedMeanYearSum += segWeight * segMeanHr;
        weightedThisHrYearSum += segWeight * segHr;
      }
    }

    if (totalMatchedPairsAll == 0) {
      return compute(current, fallbackSimilarRuns, conditions);
    }

    Integer percentileAll =
        totalMatchedPairsAll >= MIN_SIMILAR_FOR_PERCENTILE
            ? (int) Math.round(100.0 * (weightedBetterAll / totalWeightAll))
            : null;
    Integer percentileYear =
        totalMatchedPairsYear >= MIN_SIMILAR_FOR_PERCENTILE && totalWeightYear > 0
            ? (int) Math.round(100.0 * (weightedBetterYear / totalWeightYear))
            : null;

    Integer meanAll =
        totalWeightAll > 0 ? (int) Math.round(weightedMeanAllSum / totalWeightAll) : null;
    Integer meanYear =
        totalWeightYear > 0 ? (int) Math.round(weightedMeanYearSum / totalWeightYear) : null;

    int effThisHrAll =
        totalWeightAll > 0
            ? (int) Math.round(weightedThisHrAllSum / totalWeightAll)
            : current.avgHr;
    int effThisHrYear =
        totalWeightYear > 0
            ? (int) Math.round(weightedThisHrYearSum / totalWeightYear)
            : current.avgHr;

    Integer deltaAll = meanAll == null ? null : (effThisHrAll - meanAll);
    Integer deltaYear = meanYear == null ? null : (effThisHrYear - meanYear);

    return new Snapshot(
        totalMatchedPairsAll,
        totalMatchedPairsYear,
        percentileAll,
        percentileYear,
        meanAll,
        meanYear,
        deltaAll,
        deltaYear);
  }

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

  private static int equivalentHrAtCurrentEffort(
      ActivityRow peer, double curAdjPace, Map<Long, RunConditions> conditions) {
    if (conditions == null || curAdjPace <= 0) {
      return peer.avgHr;
    }
    double peerAdjPace = adjustedPace(peer, conditions);
    if (peerAdjPace <= 0) {
      return peer.avgHr;
    }
    double speedRatio = Math.max(0.92, Math.min(1.08, peerAdjPace / curAdjPace));
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
