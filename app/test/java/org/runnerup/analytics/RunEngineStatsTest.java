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
