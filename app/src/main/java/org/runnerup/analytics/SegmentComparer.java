package org.runnerup.analytics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Matches and ranks 1 km segments across all historical runs using the strict priority hierarchy:
 *
 * <ol>
 *   <li><b>Pace</b> (1 km segment pace — highest weight)
 *   <li><b>Change in elevation</b> (`netElevationM` over 1 km, where changes within ±10 m over 1 km
 *       are considered flat / same terrain bucket, and ≥10 m is a drastic climb/descent)
 *   <li><b>Elevation / Altitude</b> (`avgElevationM` above sea level, where everything under 500 m
 *       is considered the exact same altitude)
 *   <li><b>Temperature</b> (`temperatureC`)
 *   <li><b>Humidity</b> (`humidityPct` — lowest weight)
 * </ol>
 */
public final class SegmentComparer {

  public static final double ELEVATION_TOLERANCE_M = 10.0;
  public static final double DRASTIC_ELEVATION_CHANGE_M = 10.0;
  public static final double ALTITUDE_SAME_BELOW_M = 500.0;
  public static final int MIN_MATCHES = 3;
  public static final int TARGET_RICH_MATCHES = 15;

  // Priority weights strictly ordered from most important to least important:
  // 1. Pace > 2. Change in elevation > 3. Altitude (>500m) > 4. Temperature > 5. Humidity
  public static final double WEIGHT_PACE = 1.00;
  public static final double WEIGHT_ELEVATION_CHANGE = 0.70;
  public static final double WEIGHT_ALTITUDE = 0.45;
  public static final double WEIGHT_TEMPERATURE = 0.25;
  public static final double WEIGHT_HUMIDITY = 0.12;

  private SegmentComparer() {}

  public static final class Comparison {
    public final ComparableSegment segment;
    public final int matchCount;
    public final int distinctRunCount;
    public final double medianAdjustedPaceSecPerKm;
    public final double medianRawPaceSecPerKm;
    public final int medianHr;
    public final double efficiencyDeltaPct;

    public Comparison(
        ComparableSegment segment,
        int matchCount,
        double medianAdjustedPaceSecPerKm,
        int medianHr) {
      this(
          segment,
          matchCount,
          matchCount,
          medianAdjustedPaceSecPerKm,
          medianAdjustedPaceSecPerKm,
          medianHr,
          computeEfficiencyDeltaPct(segment, medianAdjustedPaceSecPerKm, medianHr));
    }

    public Comparison(
        ComparableSegment segment,
        int matchCount,
        int distinctRunCount,
        double medianAdjustedPaceSecPerKm,
        double medianRawPaceSecPerKm,
        int medianHr,
        double efficiencyDeltaPct) {
      this.segment = segment;
      this.matchCount = matchCount;
      this.distinctRunCount = distinctRunCount;
      this.medianAdjustedPaceSecPerKm = medianAdjustedPaceSecPerKm;
      this.medianRawPaceSecPerKm = medianRawPaceSecPerKm;
      this.medianHr = medianHr;
      this.efficiencyDeltaPct = efficiencyDeltaPct;
    }
  }

  /**
   * Whole-run performance rollup computed across ALL matched 1 km segments of the activity. Gives a
   * high-confidence assessment of the run even when few whole-run distance peers exist.
   */
  public static final class RunSegmentSummary {
    public final int totalSegmentsInRun;
    public final int matchedSegmentCount;
    public final int totalHistoricalSegmentMatches;
    public final int distinctHistoricalRuns;
    public final double avgRawPaceSecPerKm;
    public final double avgAdjustedPaceSecPerKm;
    public final double avgBaselineAdjustedPaceSecPerKm;
    /** Positive means this run's segments were faster than matched historical segments. */
    public final double paceSavedSecPerKm;
    public final int avgHr;
    public final int avgBaselineHr;
    /** Negative means lower HR than usual at matched pace/terrain/weather (more efficient). */
    public final int hrDeltaBpm;
    /** Positive means fewer heartbeats per km than matched historical segments. */
    public final double efficiencyGainPct;
    public final int segmentsBetterCount;

    public RunSegmentSummary(
        int totalSegmentsInRun,
        int matchedSegmentCount,
        int totalHistoricalSegmentMatches,
        int distinctHistoricalRuns,
        double avgRawPaceSecPerKm,
        double avgAdjustedPaceSecPerKm,
        double avgBaselineAdjustedPaceSecPerKm,
        double paceSavedSecPerKm,
        int avgHr,
        int avgBaselineHr,
        int hrDeltaBpm,
        double efficiencyGainPct,
        int segmentsBetterCount) {
      this.totalSegmentsInRun = totalSegmentsInRun;
      this.matchedSegmentCount = matchedSegmentCount;
      this.totalHistoricalSegmentMatches = totalHistoricalSegmentMatches;
      this.distinctHistoricalRuns = distinctHistoricalRuns;
      this.avgRawPaceSecPerKm = avgRawPaceSecPerKm;
      this.avgAdjustedPaceSecPerKm = avgAdjustedPaceSecPerKm;
      this.avgBaselineAdjustedPaceSecPerKm = avgBaselineAdjustedPaceSecPerKm;
      this.paceSavedSecPerKm = paceSavedSecPerKm;
      this.avgHr = avgHr;
      this.avgBaselineHr = avgBaselineHr;
      this.hrDeltaBpm = hrDeltaBpm;
      this.efficiencyGainPct = efficiencyGainPct;
      this.segmentsBetterCount = segmentsBetterCount;
    }
  }

  public static boolean sameContext(ComparableSegment current, ComparableSegment other) {
    if (current == null || other == null || current.activityId == other.activityId) {
      return false;
    }
    if (Math.abs(current.netElevationM - other.netElevationM) > ELEVATION_TOLERANCE_M) {
      return false;
    }
    return current.prevZoneNear == other.prevZoneNear && current.prevZoneFar == other.prevZoneFar;
  }

  /**
   * Effective altitude above 500 m ASL. Everything under 500 m returns {@code 0.0} and is
   * considered the exact same altitude.
   */
  public static double effectiveAltitudeM(Double avgElevationM) {
    if (avgElevationM == null || avgElevationM.isNaN() || avgElevationM <= ALTITUDE_SAME_BELOW_M) {
      return 0.0;
    }
    return avgElevationM - ALTITUDE_SAME_BELOW_M;
  }

  /**
   * Effective difference in 1 km elevation change (`netElevationM`). If both segments are within
   * the non-drastic flat band (`|netElevationM| < 10 m` over 1 km), they are treated as the same
   * flat terrain (`0.0` bucket difference).
   */
  public static double effectiveElevationChangeDiffM(double net1M, double net2M) {
    if (Math.abs(net1M) < DRASTIC_ELEVATION_CHANGE_M
        && Math.abs(net2M) < DRASTIC_ELEVATION_CHANGE_M) {
      return 0.0;
    }
    return Math.abs(net1M - net2M);
  }

  /** Relative pace difference (using the closer of raw pace and conditions-adjusted pace). */
  public static double relativePaceDiff(ComparableSegment a, ComparableSegment b) {
    if (a == null || b == null || a.paceSecPerKm <= 0 || b.paceSecPerKm <= 0) {
      return Double.POSITIVE_INFINITY;
    }
    double rawDiff = Math.abs(a.paceSecPerKm - b.paceSecPerKm) / a.paceSecPerKm;
    double adjDiff =
        (a.adjustedPaceSecPerKm > 0 && b.adjustedPaceSecPerKm > 0)
            ? Math.abs(a.adjustedPaceSecPerKm - b.adjustedPaceSecPerKm) / a.adjustedPaceSecPerKm
            : rawDiff;
    return Math.min(rawDiff, adjDiff);
  }

  /**
   * Composite similarity distance strictly ordered by the 5 priority factors:
   *
   * <ol>
   *   <li>Pace ({@link #WEIGHT_PACE} = 1.00)
   *   <li>Change in elevation ({@link #WEIGHT_ELEVATION_CHANGE} = 0.70, ±10 m flat deadband)
   *   <li>Elevation / Altitude ({@link #WEIGHT_ALTITUDE} = 0.45, &lt;500 m all same)
   *   <li>Temperature ({@link #WEIGHT_TEMPERATURE} = 0.25)
   *   <li>Humidity ({@link #WEIGHT_HUMIDITY} = 0.12)
   * </ol>
   *
   * Lower score means a closer match.
   */
  public static double similarityDistance(ComparableSegment current, ComparableSegment other) {
    if (current == null || other == null || current.activityId == other.activityId) {
      return Double.POSITIVE_INFINITY;
    }
    double paceRel = relativePaceDiff(current, other);
    if (Double.isInfinite(paceRel)) {
      return Double.POSITIVE_INFINITY;
    }
    // 1. Pace (normalized by 10% pace difference)
    double paceTerm = WEIGHT_PACE * (paceRel / 0.10);

    // 2. Change in elevation over 1 km (0 when both within ±10 m flat band; normalized by 10 m)
    double elevChangeDiff =
        effectiveElevationChangeDiffM(current.netElevationM, other.netElevationM);
    double elevChangeTerm = WEIGHT_ELEVATION_CHANGE * (elevChangeDiff / 10.0);

    // 3. Elevation / Altitude above sea level (0 when both <= 500 m; normalized by 500 m)
    double altDiff =
        Math.abs(
            effectiveAltitudeM(current.avgElevationM) - effectiveAltitudeM(other.avgElevationM));
    double altitudeTerm = WEIGHT_ALTITUDE * (altDiff / 500.0);

    // 4. Temperature (normalized by 10°C)
    double tempTerm = 0.0;
    if (current.avgTempC != null
        && !current.avgTempC.isNaN()
        && other.avgTempC != null
        && !other.avgTempC.isNaN()) {
      tempTerm = WEIGHT_TEMPERATURE * (Math.abs(current.avgTempC - other.avgTempC) / 10.0);
    }

    // 5. Humidity (normalized by 25% RH)
    double humidityTerm = 0.0;
    if (current.avgHumidityPct != null
        && !current.avgHumidityPct.isNaN()
        && other.avgHumidityPct != null
        && !other.avgHumidityPct.isNaN()) {
      humidityTerm =
          WEIGHT_HUMIDITY * (Math.abs(current.avgHumidityPct - other.avgHumidityPct) / 25.0);
    }

    return paceTerm + elevChangeTerm + altitudeTerm + tempTerm + humidityTerm;
  }

  /** Strict comparison used when at least {@link #MIN_MATCHES} exact two-zone matches exist. */
  public static List<Comparison> compare(
      List<ComparableSegment> current, List<ComparableSegment> historical) {
    if (current == null || current.isEmpty() || historical == null || historical.isEmpty()) {
      return Collections.emptyList();
    }
    List<Comparison> out = new ArrayList<>();
    for (ComparableSegment segment : current) {
      List<Double> paces = new ArrayList<>();
      List<Integer> hrs = new ArrayList<>();
      Set<Long> runIds = new HashSet<>();
      for (ComparableSegment other : historical) {
        if (!sameContext(segment, other)) {
          continue;
        }
        paces.add(other.adjustedPaceSecPerKm);
        runIds.add(other.activityId);
        if (other.avgHr > 0) {
          hrs.add(other.avgHr);
        }
      }
      if (paces.size() < MIN_MATCHES) {
        continue;
      }
      double medAdj = median(paces);
      int medHr = medianHr(hrs);
      out.add(
          new Comparison(
              segment,
              paces.size(),
              runIds.size(),
              medAdj,
              medAdj,
              medHr,
              computeEfficiencyDeltaPct(segment, medAdj, medHr)));
    }
    return out;
  }

  /**
   * Smart high-coverage 1 km segment comparison across all historical runs and km windows, ordered
   * by:
   *
   * <ol>
   *   <li>Pace
   *   <li>Change in elevation (±10 m over 1 km flat bucket)
   *   <li>Altitude (&lt;500 m treated as identical)
   *   <li>Temperature
   *   <li>Humidity
   * </ol>
   */
  public static List<Comparison> compareSmart(
      List<ComparableSegment> current, List<ComparableSegment> historical) {
    if (current == null || current.isEmpty() || historical == null || historical.isEmpty()) {
      return Collections.emptyList();
    }
    List<Comparison> out = new ArrayList<>(current.size());
    for (ComparableSegment segment : current) {
      if (segment == null) continue;
      Comparison c = matchSegmentPriority(segment, historical);
      if (c != null) {
        out.add(c);
      }
    }
    return out;
  }

  private static Comparison matchSegmentPriority(
      final ComparableSegment segment, List<ComparableSegment> historical) {
    List<ComparableSegment> tier1 = new ArrayList<>();
    List<ComparableSegment> tier2 = new ArrayList<>();
    List<ComparableSegment> allOther = new ArrayList<>();

    for (ComparableSegment other : historical) {
      if (other == null || other.activityId == segment.activityId || other.paceSecPerKm <= 0) {
        continue;
      }
      allOther.add(other);
      if (isPrimaryPriorityMatch(segment, other)) {
        tier1.add(other);
      } else if (isBroadPriorityMatch(segment, other)) {
        tier2.add(other);
      }
    }

    if (allOther.isEmpty()) {
      return null;
    }

    Comparator<ComparableSegment> bySimilarity =
        new Comparator<ComparableSegment>() {
          @Override
          public int compare(ComparableSegment a, ComparableSegment b) {
            return Double.compare(similarityDistance(segment, a), similarityDistance(segment, b));
          }
        };

    Collections.sort(tier1, bySimilarity);
    List<ComparableSegment> matched = new ArrayList<>(tier1);

    // If primary tier has fewer than TARGET_RICH_MATCHES, supplement with closest broadened matches
    if (matched.size() < TARGET_RICH_MATCHES && !tier2.isEmpty()) {
      Collections.sort(tier2, bySimilarity);
      for (ComparableSegment candidate : tier2) {
        matched.add(candidate);
        if (matched.size() >= TARGET_RICH_MATCHES) {
          break;
        }
      }
    }

    // Fallback: ensure every segment gets compared if historical segments exist
    if (matched.isEmpty()) {
      Collections.sort(allOther, bySimilarity);
      int limit = Math.min(TARGET_RICH_MATCHES, allOther.size());
      for (int i = 0; i < limit; i++) {
        matched.add(allOther.get(i));
      }
    }

    Set<Long> distinctRuns = new HashSet<>();
    double weightedAdjPaceSum = 0.0;
    double weightedRawPaceSum = 0.0;
    double paceWeightSum = 0.0;
    double weightedHrSum = 0.0;
    double hrWeightSum = 0.0;

    for (int i = 0; i < matched.size(); i++) {
      ComparableSegment other = matched.get(i);
      distinctRuns.add(other.activityId);
      double dist = similarityDistance(segment, other);
      double w = 1.0 / (1.0 + dist * dist);
      weightedAdjPaceSum += w * other.adjustedPaceSecPerKm;
      weightedRawPaceSum += w * other.paceSecPerKm;
      paceWeightSum += w;

      if (other.avgHr > 0) {
        // Normalize HR slightly for any residual adjusted-pace difference within the matched band
        // so HR comparison reflects the exact same running speed & terrain effort.
        double paceRatio =
            (segment.adjustedPaceSecPerKm > 0 && other.adjustedPaceSecPerKm > 0)
                ? (other.adjustedPaceSecPerKm / segment.adjustedPaceSecPerKm)
                : 1.0;
        paceRatio = Math.max(0.90, Math.min(1.10, paceRatio));
        double normalizedHr = other.avgHr * paceRatio;
        weightedHrSum += w * normalizedHr;
        hrWeightSum += w;
      }
    }

    double refAdjPace =
        paceWeightSum > 0 ? (weightedAdjPaceSum / paceWeightSum) : segment.adjustedPaceSecPerKm;
    double refRawPace =
        paceWeightSum > 0 ? (weightedRawPaceSum / paceWeightSum) : segment.paceSecPerKm;
    int refHr = hrWeightSum > 0 ? (int) Math.round(weightedHrSum / hrWeightSum) : 0;
    double effDeltaPct = computeEfficiencyDeltaPct(segment, refAdjPace, refHr);

    return new Comparison(
        segment,
        matched.size(),
        distinctRuns.size(),
        refAdjPace,
        refRawPace,
        refHr,
        effDeltaPct);
  }

  /**
   * Primary match gate following the priority hierarchy:
   *
   * <ol>
   *   <li>Pace within ±14% (raw or conditions-adjusted)
   *   <li>Change in elevation: both within ±10 m flat band, OR same slope direction within ±14 m
   *   <li>Altitude: within ±350 m effective altitude (where everything ≤500 m is 0 m)
   *   <li>Temperature: within ±15°C when both known
   *   <li>Humidity: within ±40% RH when both known
   * </ol>
   */
  public static boolean isPrimaryPriorityMatch(
      ComparableSegment current, ComparableSegment other) {
    if (current == null || other == null || current.activityId == other.activityId) {
      return false;
    }
    // 1. Pace
    if (relativePaceDiff(current, other) > 0.14) {
      return false;
    }
    // 2. Change in elevation (±10 m over 1 km flat threshold)
    boolean curFlat = Math.abs(current.netElevationM) < DRASTIC_ELEVATION_CHANGE_M;
    boolean othFlat = Math.abs(other.netElevationM) < DRASTIC_ELEVATION_CHANGE_M;
    if (curFlat && othFlat) {
      // Both in the non-drastic flat band (< 10 m over 1 km) -> match!
    } else if (curFlat != othFlat) {
      if (Math.abs(current.netElevationM - other.netElevationM) > 10.0) {
        return false;
      }
    } else {
      // Both have drastic elevation change (>= 10 m): require same sign and within 14 m
      if (current.netElevationM * other.netElevationM < 0) {
        return false;
      }
      if (Math.abs(current.netElevationM - other.netElevationM) > 14.0) {
        return false;
      }
    }
    // 3. Altitude (everything under 500 m is 0.0 -> 0 diff)
    double altDiff =
        Math.abs(
            effectiveAltitudeM(current.avgElevationM) - effectiveAltitudeM(other.avgElevationM));
    if (altDiff > 350.0) {
      return false;
    }
    // 4. Temperature
    if (current.avgTempC != null
        && !current.avgTempC.isNaN()
        && other.avgTempC != null
        && !other.avgTempC.isNaN()) {
      if (Math.abs(current.avgTempC - other.avgTempC) > 15.0) {
        return false;
      }
    }
    // 5. Humidity
    if (current.avgHumidityPct != null
        && !current.avgHumidityPct.isNaN()
        && other.avgHumidityPct != null
        && !other.avgHumidityPct.isNaN()) {
      if (Math.abs(current.avgHumidityPct - other.avgHumidityPct) > 40.0) {
        return false;
      }
    }
    return true;
  }

  /** Broadened priority match when primary pool has fewer than {@link #TARGET_RICH_MATCHES}. */
  public static boolean isBroadPriorityMatch(ComparableSegment current, ComparableSegment other) {
    if (current == null || other == null || current.activityId == other.activityId) {
      return false;
    }
    if (relativePaceDiff(current, other) > 0.22) {
      return false;
    }
    return effectiveElevationChangeDiffM(current.netElevationM, other.netElevationM) <= 18.0;
  }

  /**
   * Summarizes overall run performance across ALL matched 1 km segments of the run.
   */
  public static RunSegmentSummary summarizeRun(List<Comparison> comparisons) {
    if (comparisons == null || comparisons.isEmpty()) {
      return null;
    }
    int matchedSegs = 0;
    int totalMatches = 0;
    int maxDistinctRuns = 0;
    double rawPaceSum = 0.0;
    double adjPaceSum = 0.0;
    double baseAdjPaceSum = 0.0;
    int hrSum = 0;
    int baseHrSum = 0;
    int hrSegCount = 0;
    double effSum = 0.0;
    int effSegCount = 0;
    int betterCount = 0;

    for (Comparison c : comparisons) {
      if (c == null || c.segment == null || c.matchCount <= 0) {
        continue;
      }
      matchedSegs++;
      totalMatches += c.matchCount;
      if (c.distinctRunCount > maxDistinctRuns) {
        maxDistinctRuns = c.distinctRunCount;
      }
      rawPaceSum += c.segment.paceSecPerKm;
      adjPaceSum += c.segment.adjustedPaceSecPerKm;
      baseAdjPaceSum += c.medianAdjustedPaceSecPerKm;

      if (c.segment.avgHr > 0 && c.medianHr > 0) {
        hrSum += c.segment.avgHr;
        baseHrSum += c.medianHr;
        hrSegCount++;
      }
      if (!Double.isNaN(c.efficiencyDeltaPct)) {
        effSum += c.efficiencyDeltaPct;
        effSegCount++;
      }

      boolean better =
          (c.segment.avgHr > 0 && c.medianHr > 0)
              ? (c.efficiencyDeltaPct > 0.0
                  || (c.segment.avgHr <= c.medianHr
                      && c.segment.adjustedPaceSecPerKm <= c.medianAdjustedPaceSecPerKm))
              : (c.segment.adjustedPaceSecPerKm <= c.medianAdjustedPaceSecPerKm);
      if (better) {
        betterCount++;
      }
    }

    if (matchedSegs == 0) {
      return null;
    }

    double avgRaw = rawPaceSum / matchedSegs;
    double avgAdj = adjPaceSum / matchedSegs;
    double avgBaseAdj = baseAdjPaceSum / matchedSegs;
    double paceSaved = avgBaseAdj - avgAdj;
    int avgHr = hrSegCount > 0 ? (int) Math.round(hrSum / (double) hrSegCount) : 0;
    int avgBaseHr = hrSegCount > 0 ? (int) Math.round(baseHrSum / (double) hrSegCount) : 0;
    int hrDelta = (avgHr > 0 && avgBaseHr > 0) ? (avgHr - avgBaseHr) : 0;
    double avgEffGain = effSegCount > 0 ? (effSum / effSegCount) : 0.0;

    return new RunSegmentSummary(
        comparisons.size(),
        matchedSegs,
        totalMatches,
        maxDistinctRuns,
        avgRaw,
        avgAdj,
        avgBaseAdj,
        paceSaved,
        avgHr,
        avgBaseHr,
        hrDelta,
        avgEffGain,
        betterCount);
  }

  private static double computeEfficiencyDeltaPct(
      ComparableSegment segment, double refAdjPaceSecPerKm, int refHr) {
    if (segment == null
        || segment.avgHr <= 0
        || refHr <= 0
        || segment.adjustedPaceSecPerKm <= 0
        || refAdjPaceSecPerKm <= 0) {
      return 0.0;
    }
    double curBeatsPerKm = segment.avgHr * (segment.adjustedPaceSecPerKm / 60.0);
    double refBeatsPerKm = refHr * (refAdjPaceSecPerKm / 60.0);
    if (refBeatsPerKm <= 0) {
      return 0.0;
    }
    return ((refBeatsPerKm - curBeatsPerKm) / refBeatsPerKm) * 100.0;
  }

  static double median(List<Double> values) {
    if (values.isEmpty()) {
      return 0;
    }
    List<Double> sorted = new ArrayList<>(values);
    Collections.sort(sorted);
    int mid = sorted.size() / 2;
    if (sorted.size() % 2 == 1) {
      return sorted.get(mid);
    }
    return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
  }

  static int medianHr(List<Integer> values) {
    if (values.isEmpty()) {
      return 0;
    }
    List<Integer> sorted = new ArrayList<>(values);
    Collections.sort(sorted);
    int mid = sorted.size() / 2;
    if (sorted.size() % 2 == 1) {
      return sorted.get(mid);
    }
    return (int) Math.round((sorted.get(mid - 1) + sorted.get(mid)) / 2.0);
  }
}
