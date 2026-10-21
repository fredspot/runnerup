package org.runnerup.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.runnerup.analytics.ComparableSegmentExtractor.Sample;
import org.runnerup.analytics.SegmentComparer.Comparison;

public class SegmentComparerTest {

  private static final int[] ZONES = new int[] {100, 130, 130, 150, 150, 170, 170, 190};

  @Test
  public void zoneForHr_usesBoundsAndZoneFiveAboveTheTop() {
    assertEquals(2, ComparableSegmentExtractor.zoneForHr(140, ZONES));
    assertEquals(5, ComparableSegmentExtractor.zoneForHr(190, ZONES));
    assertEquals(0, ComparableSegmentExtractor.zoneForHr(0, ZONES));
  }

  @Test
  public void flatKilometreAfterTwoZoneTwoKilometresMatchesTheSamePattern() {
    List<ComparableSegment> current = extract(1, 260_000);
    List<ComparableSegment> historical = new ArrayList<>();
    historical.addAll(extract(2, 300_000));
    historical.addAll(extract(3, 310_000));
    historical.addAll(extract(4, 290_000));
    historical.addAll(hillyThirdKilometre(5));

    List<Comparison> comparisons = SegmentComparer.compare(current, historical);
    Comparison third = null;
    for (Comparison comparison : comparisons) {
      if (comparison.segment.windowIndex == 2) {
        third = comparison;
      }
    }
    assertTrue(third != null);
    assertEquals(3, third.matchCount);
    assertEquals(300, third.medianAdjustedPaceSecPerKm, 0.01);
    assertTrue(third.segment.adjustedPaceSecPerKm < third.medianAdjustedPaceSecPerKm);
    assertEquals(2, third.segment.prevZoneNear);
    assertEquals(2, third.segment.prevZoneFar);
    assertFalse(SegmentComparer.sameContext(current.get(2), hillyThirdKilometre(5).get(0)));
  }

  @Test
  public void fewerThanThreeMatchesAreHidden() {
    List<Comparison> comparisons =
        SegmentComparer.compare(extract(1, 260_000), extract(2, 300_000));
    assertTrue(comparisons.isEmpty());
  }

  @Test
  public void compareSmart_fallsBackWhenFewerThanThreeStrictMatches() {
    List<Comparison> smart =
        SegmentComparer.compareSmart(extract(1, 260_000), extract(2, 300_000));
    assertFalse(smart.isEmpty());
    assertEquals(3, smart.size());
  }

  @Test
  public void priorityOrdering_paceThenElevChangeThenAltitudeUnder500ThenTempThenHumidity() {
    assertTrue(SegmentComparer.WEIGHT_PACE > SegmentComparer.WEIGHT_ELEVATION_CHANGE);
    assertTrue(SegmentComparer.WEIGHT_ELEVATION_CHANGE > SegmentComparer.WEIGHT_ALTITUDE);
    assertTrue(SegmentComparer.WEIGHT_ALTITUDE > SegmentComparer.WEIGHT_TEMPERATURE);
    assertTrue(SegmentComparer.WEIGHT_TEMPERATURE > SegmentComparer.WEIGHT_HUMIDITY);

    // Everything under 500 m altitude is considered the exact same (0 diff)
    assertEquals(0.0, SegmentComparer.effectiveAltitudeM(40.0), 0.001);
    assertEquals(0.0, SegmentComparer.effectiveAltitudeM(495.0), 0.001);
    assertTrue(SegmentComparer.effectiveAltitudeM(2240.0) > 1700.0);

    // Elevation change under 10 m over 1 km is flat (0 diff); >= 10 m is drastic
    assertEquals(0.0, SegmentComparer.effectiveElevationChangeDiffM(-5.0, 7.0), 0.001);
    assertTrue(SegmentComparer.effectiveElevationChangeDiffM(0.0, 15.0) >= 15.0);

    ComparableSegment cur =
        new ComparableSegment(1L, 0, 300.0, 300.0, 150, 3.0, 100.0, 18.0, 55.0, 0, 0);
    ComparableSegment sameUnder500Alt =
        new ComparableSegment(2L, 4, 300.0, 300.0, 154, -4.0, 480.0, 18.0, 55.0, 3, 3);
    assertEquals(0.0, SegmentComparer.similarityDistance(cur, sameUnder500Alt), 0.0001);
    assertTrue(SegmentComparer.isPrimaryPriorityMatch(cur, sameUnder500Alt));

    List<Comparison> comps =
        SegmentComparer.compareSmart(
            Arrays.asList(cur), Arrays.asList(sameUnder500Alt));
    SegmentComparer.RunSegmentSummary summary = SegmentComparer.summarizeRun(comps);
    assertTrue(summary != null);
    assertEquals(1, summary.matchedSegmentCount);
    assertTrue(summary.hrDeltaBpm < 0);
  }

  private static List<ComparableSegment> extract(long activityId, long thirdKmMs) {
    return ComparableSegmentExtractor.extract(activityId, track(thirdKmMs, 0), ZONES);
  }

  private static List<ComparableSegment> hillyThirdKilometre(long activityId) {
    List<ComparableSegment> segments =
        ComparableSegmentExtractor.extract(activityId, track(300_000, 40), ZONES);
    List<ComparableSegment> third = new ArrayList<>();
    for (ComparableSegment segment : segments) {
      if (segment.windowIndex == 2) {
        third.add(segment);
      }
    }
    return third;
  }

  /** Three flat zone-2 kilometres, optional extra climb on the third. */
  private static List<Sample> track(long thirdKmMs, double thirdClimbM) {
    return Arrays.asList(
        sample(0, 0, 140, 100),
        sample(500, 150_000, 140, 100),
        sample(1000, 300_000, 140, 100),
        sample(1500, 450_000, 140, 100),
        sample(2000, 600_000, 140, 100),
        sample(2500, 600_000 + thirdKmMs / 2, 155, 100 + thirdClimbM / 2),
        sample(3000, 600_000 + thirdKmMs, 155, 100 + thirdClimbM));
  }

  private static Sample sample(double distance, long elapsed, int hr, double elev) {
    return new Sample(distance, elapsed, hr, elev, 15.0);
  }
}
