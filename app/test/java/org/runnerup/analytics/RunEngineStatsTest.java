package org.runnerup.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.runnerup.data.RunningActivityReader.ActivityRow;

public class RunEngineStatsTest {

  @Test
  public void percentile_countsShareWithWorseHr() {
    List<Integer> peers = Arrays.asList(150, 148, 160, 142, 155);
    assertEquals(Integer.valueOf(80), RunEngineStats.percentileLowerIsBetter(145, peers));
  }

  @Test
  public void percentile_nullWhenFewerThanThreePeers() {
    assertNull(RunEngineStats.percentileLowerIsBetter(145, Arrays.asList(150, 148)));
  }

  @Test
  public void compute_splitsThisYearFromAllYears() {
    long thisYear = epoch(2026, Calendar.JUNE, 1);
    long lastYear = epoch(2025, Calendar.JUNE, 1);
    ActivityRow current = row(1, thisYear, 145);
    List<ActivityRow> similar =
        Arrays.asList(
            row(2, thisYear, 150),
            row(3, thisYear, 155),
            row(4, thisYear, 140),
            row(5, lastYear, 160),
            row(6, lastYear, 158),
            row(7, lastYear, 152));
    RunEngineStats.Snapshot snap = RunEngineStats.compute(current, similar);
    assertEquals(6, snap.similarWithHrAll);
    assertEquals(3, snap.similarWithHrYear);
    assertEquals(Integer.valueOf(83), snap.percentileAll);
    assertEquals(Integer.valueOf(67), snap.percentileYear);
    assertEquals(Integer.valueOf(153), snap.meanHrAll);
    assertEquals(Integer.valueOf(148), snap.meanHrYear);
    assertEquals(Integer.valueOf(-8), snap.deltaVsAll);
    assertEquals(Integer.valueOf(-3), snap.deltaVsYear);
  }

  @Test
  public void compute_emptyWithoutHr() {
    ActivityRow current = row(1, epoch(2026, Calendar.JANUARY, 1), 0);
    RunEngineStats.Snapshot snap = RunEngineStats.compute(current, Arrays.asList(row(2, 1, 150)));
    assertEquals(false, snap.hasHeadline());
  }

  @Test
  public void computeFromSegments_ranks330kmAt128bpmAnd143kmAt125bpmConsistently() {
    long sameDay = epoch(2026, Calendar.OCTOBER, 21);
    long earlierThisYear = epoch(2026, Calendar.AUGUST, 10);
    int[] zones = new int[] {100, 125, 125, 145, 145, 165, 165, 185};

    // Historical pool of 1 km segments around ~5:55/km (355 s/km), flat (+2 m), 120 m altitude (<500m), 16°C, 60% RH
    int[] histHrs = new int[] {122, 124, 126, 127, 129, 130, 132, 134, 136, 138};
    java.util.List<ComparableSegment> historical = new java.util.ArrayList<>();
    for (int i = 0; i < histHrs.length; i++) {
      historical.add(
          new ComparableSegment(
              100L + i,
              i % 5,
              356.0,
              356.0,
              histHrs[i],
              2.0,
              120.0,
              16.0,
              60.0,
              2,
              2,
              earlierThisYear,
              1000.0));
    }

    // Run A: 3.30 km at 5:55/km (355 s/km), avg HR 128 bpm
    ActivityRow run330 = new ActivityRow(1L, sameDay, 3300.0, 1171L, 128, 136);
    java.util.List<ComparableSegmentExtractor.Sample> samples330 =
        Arrays.asList(
            new ComparableSegmentExtractor.Sample(0.0, 0L, 128, 120.0, 16.0),
            new ComparableSegmentExtractor.Sample(1000.0, 355_000L, 128, 122.0, 16.0),
            new ComparableSegmentExtractor.Sample(2000.0, 710_000L, 128, 121.0, 16.0),
            new ComparableSegmentExtractor.Sample(3000.0, 1_065_000L, 128, 123.0, 16.0),
            new ComparableSegmentExtractor.Sample(3300.0, 1_171_500L, 128, 123.0, 16.0));
    List<ComparableSegment> segs330 =
        ComparableSegmentExtractor.extract(1L, samples330, zones, sameDay, null);
    assertEquals(3, segs330.size());

    // Run B: 1.43 km at 5:57/km (357 s/km), avg HR 125 bpm (includes 1.0 km + 0.43 km trailing segment)
    ActivityRow run143 = new ActivityRow(2L, sameDay, 1430.0, 510L, 125, 132);
    java.util.List<ComparableSegmentExtractor.Sample> samples143 =
        Arrays.asList(
            new ComparableSegmentExtractor.Sample(0.0, 0L, 125, 120.0, 16.0),
            new ComparableSegmentExtractor.Sample(1000.0, 357_000L, 125, 122.0, 16.0),
            new ComparableSegmentExtractor.Sample(1430.0, 510_510L, 125, 123.0, 16.0));
    List<ComparableSegment> segs143 =
        ComparableSegmentExtractor.extract(2L, samples143, zones, sameDay, null);
    assertEquals(2, segs143.size());
    assertEquals(430.0, segs143.get(1).distanceM, 0.1);

    RunEngineStats.Snapshot snap330 =
        RunEngineStats.computeFromSegments(run330, segs330, historical, null, null);
    RunEngineStats.Snapshot snap143 =
        RunEngineStats.computeFromSegments(run143, segs143, historical, null, null);

    // Against the same historical segment pool:
    // 128 bpm beats 6 of 10 historical segments (60%)
    // 125 bpm beats 8 of 10 historical segments (80%) — never 38%!
    assertEquals(Integer.valueOf(60), snap330.percentileAll);
    assertEquals(Integer.valueOf(80), snap143.percentileAll);
    assertEquals(snap330.meanHrAll, snap143.meanHrAll);
    org.junit.Assert.assertTrue(snap143.percentileAll >= snap330.percentileAll);
  }

  private static ActivityRow row(long id, long startTime, int hr) {
    return new ActivityRow(id, startTime, 10000, 3000, hr, hr + 10);
  }

  private static long epoch(int year, int month, int day) {
    Calendar cal = Calendar.getInstance(Locale.US);
    cal.clear();
    cal.set(year, month, day, 12, 0, 0);
    return cal.getTimeInMillis() / 1000L;
  }
}
