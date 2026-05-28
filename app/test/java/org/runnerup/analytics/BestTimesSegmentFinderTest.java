package org.runnerup.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.runnerup.analytics.BestTimesCalculator.BestTimeResult;
import org.runnerup.data.RunningActivityReader;

public class BestTimesSegmentFinderTest {

  @Test
  public void findFastestSegment_singleLapCoversTarget() {
    RunningActivityReader.LapRow lap = new RunningActivityReader.LapRow(1, 300, 1000, 150);
    RunningActivityReader.ActivityRow info =
        new RunningActivityReader.ActivityRow(1, 1000, 1000, 300, 150, 170);
    BestTimeResult result =
        BestTimesSegmentFinder.findFastestSegment(
            Collections.singletonList(lap), 1000, info);
    assertNotNull(result);
    assertEquals(300_000L, result.timeMs.longValue());
  }

  @Test
  public void findFastestSegment_rejectsImpossiblyFastPace() {
    RunningActivityReader.LapRow lap = new RunningActivityReader.LapRow(1, 30, 1000, 0);
    BestTimeResult result =
        BestTimesSegmentFinder.findFastestSegment(
            Collections.singletonList(lap),
            1000,
            new RunningActivityReader.ActivityRow(1, 0, 1000, 30, 0, 0));
    assertNull(result);
  }

  @Test
  public void findFastestSegment_zeroTimeLapSkippedButLaterLapCounts() {
    RunningActivityReader.LapRow marker = new RunningActivityReader.LapRow(0, 0, 0, 0);
    RunningActivityReader.LapRow work = new RunningActivityReader.LapRow(1, 360, 1000, 140);
    BestTimeResult result =
        BestTimesSegmentFinder.findFastestSegment(
            Arrays.asList(marker, work),
            1000,
            new RunningActivityReader.ActivityRow(2, 0, 1000, 360, 140, 160));
    assertNotNull(result);
    assertEquals(360_000L, result.timeMs.longValue());
  }
}
