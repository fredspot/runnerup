package org.runnerup.analytics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Matches 1 km windows that share elevation and preceding heart-rate zones. */
public final class SegmentComparer {

  public static final double ELEVATION_TOLERANCE_M = 10;
  public static final int MIN_MATCHES = 3;

  private SegmentComparer() {}

  public static final class Comparison {
    public final ComparableSegment segment;
    public final int matchCount;
    public final double medianAdjustedPaceSecPerKm;
    public final int medianHr;

    public Comparison(
        ComparableSegment segment,
        int matchCount,
        double medianAdjustedPaceSecPerKm,
        int medianHr) {
      this.segment = segment;
      this.matchCount = matchCount;
      this.medianAdjustedPaceSecPerKm = medianAdjustedPaceSecPerKm;
      this.medianHr = medianHr;
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
      for (ComparableSegment other : historical) {
        if (!sameContext(segment, other)) {
          continue;
        }
        paces.add(other.adjustedPaceSecPerKm);
        if (other.avgHr > 0) {
          hrs.add(other.avgHr);
        }
      }
      if (paces.size() < MIN_MATCHES) {
        continue;
      }
      out.add(new Comparison(segment, paces.size(), median(paces), medianHr(hrs)));
    }
    return out;
  }

  /**
   * Smart comparison that prefers exact two-zone + ±10m elevation matches (≥3), then falls back per
   * kilometre to:
   *
   * <ol>
   *   <li>Immediate preceding zone (`prevZoneNear`) + ±15m elevation (≥2 matches)
   *   <li>Any preceding zone + ±20m elevation (≥1 match)
   * </ol>
   *
   * so the user can always evaluate how each kilometre of their run stacked up against past runs.
   */
  public static List<Comparison> compareSmart(
      List<ComparableSegment> current, List<ComparableSegment> historical) {
    if (current == null || current.isEmpty() || historical == null || historical.isEmpty()) {
      return Collections.emptyList();
    }
    List<Comparison> out = new ArrayList<>();
    for (ComparableSegment segment : current) {
      Comparison strict = matchAtTier(segment, historical, 0);
      if (strict != null) {
        out.add(strict);
        continue;
      }
      Comparison nearZone = matchAtTier(segment, historical, 1);
      if (nearZone != null) {
        out.add(nearZone);
        continue;
      }
      Comparison gradeOnly = matchAtTier(segment, historical, 2);
      if (gradeOnly != null) {
        out.add(gradeOnly);
      }
    }
    return out;
  }

  private static Comparison matchAtTier(
      ComparableSegment segment, List<ComparableSegment> historical, int tier) {
    List<Double> paces = new ArrayList<>();
    List<Integer> hrs = new ArrayList<>();
    for (ComparableSegment other : historical) {
      if (other == null || other.activityId == segment.activityId) continue;
      boolean match;
      if (tier == 0) {
        match = sameContext(segment, other);
      } else if (tier == 1) {
        match =
            Math.abs(segment.netElevationM - other.netElevationM) <= 15.0
                && segment.prevZoneNear == other.prevZoneNear;
      } else {
        match = Math.abs(segment.netElevationM - other.netElevationM) <= 20.0;
      }
      if (!match) continue;
      paces.add(other.adjustedPaceSecPerKm);
      if (other.avgHr > 0) {
        hrs.add(other.avgHr);
      }
    }
    int minReq = tier == 0 ? MIN_MATCHES : (tier == 1 ? 2 : 1);
    if (paces.size() < minReq) {
      return null;
    }
    return new Comparison(segment, paces.size(), median(paces), medianHr(hrs));
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
