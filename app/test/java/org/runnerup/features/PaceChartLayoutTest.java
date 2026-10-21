package org.runnerup.features;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class PaceChartLayoutTest {

  @Test
  public void avgHrUsesGroupedBars() {
    assertTrue(PaceChart.isGrouped(PaceChart.Metric.AVG_HR));
    assertFalse(PaceChart.isGrouped(PaceChart.Metric.LAPS));
  }

  @Test
  public void maxNormalizedValue_lapsUsesStackedSum() {
    Map<Integer, PaceActivity.BinStats[]> yearToBins = twoYears(10, 20, 15, 5);
    float max =
        PaceChart.maxNormalizedValue(
            PaceChart.Metric.LAPS, Arrays.asList(2024, 2025), yearToBins, 2);
    assertEquals(25f, max, 0.01f);
  }

  @Test
  public void valueRange_lapsStartsAtZero() {
    Map<Integer, PaceActivity.BinStats[]> yearToBins = twoYears(10, 20, 15, 5);
    float[] range =
        PaceChart.valueRange(PaceChart.Metric.LAPS, Arrays.asList(2024, 2025), yearToBins, 2);
    assertEquals(0f, range[0], 0.01f);
    assertEquals(25f, range[1], 0.01f);
  }

  @Test
  public void valueRange_avgHrZoomsAroundObservedBpm() {
    Map<Integer, PaceActivity.BinStats[]> yearToBins = twoYearsHr(140, 150, 145, 160);
    float[] range =
        PaceChart.valueRange(PaceChart.Metric.AVG_HR, Arrays.asList(2024, 2025), yearToBins, 2);
    assertEquals(132f, range[0], 0.01f);
    assertEquals(163.2f, range[1], 0.01f);
  }

  @Test
  public void heightFraction_usesZoomedWindow() {
    assertEquals(0.5f, PaceChart.heightFraction(150, 140, 160), 0.01f);
    assertEquals(0f, PaceChart.heightFraction(120, 140, 160), 0.01f);
  }

  @Test
  public void groupWidth_groupedMultipliesByYearCount() {
    float grouped = PaceChart.groupWidthPx(3, 12f, 2f, true);
    assertEquals(40f, grouped, 0.01f);
    float stacked = PaceChart.groupWidthPx(3, 18f, 2f, false);
    assertEquals(18f, stacked, 0.01f);
  }

  @Test
  public void contentWidth_includesGapsBetweenBins() {
    assertEquals(0f, PaceChart.contentWidthPx(0, 18f, 18f), 0.01f);
    assertEquals(54f, PaceChart.contentWidthPx(2, 18f, 18f), 0.01f);
  }

  @Test
  public void sortedYears_ordersAscending() {
    Map<Integer, PaceActivity.BinStats[]> yearToBins = new HashMap<>();
    yearToBins.put(2025, new PaceActivity.BinStats[] {});
    yearToBins.put(2023, new PaceActivity.BinStats[] {});
    List<Integer> years = PaceChart.sortedYears(yearToBins);
    assertEquals(Arrays.asList(2023, 2025), years);
    assertEquals(Collections.emptyList(), PaceChart.sortedYears(null));
  }

  private static Map<Integer, PaceActivity.BinStats[]> twoYears(
      int y1b0, int y1b1, int y2b0, int y2b1) {
    Map<Integer, PaceActivity.BinStats[]> map = new HashMap<>();
    map.put(2024, new PaceActivity.BinStats[] {laps(y1b0), laps(y1b1)});
    map.put(2025, new PaceActivity.BinStats[] {laps(y2b0), laps(y2b1)});
    return map;
  }

  private static Map<Integer, PaceActivity.BinStats[]> twoYearsHr(
      int y1b0, int y1b1, int y2b0, int y2b1) {
    Map<Integer, PaceActivity.BinStats[]> map = new HashMap<>();
    map.put(2024, new PaceActivity.BinStats[] {hr(y1b0), hr(y1b1)});
    map.put(2025, new PaceActivity.BinStats[] {hr(y2b0), hr(y2b1)});
    return map;
  }

  private static PaceActivity.BinStats laps(int count) {
    PaceActivity.BinStats s = new PaceActivity.BinStats();
    s.laps = count;
    return s;
  }

  private static PaceActivity.BinStats hr(int bpm) {
    PaceActivity.BinStats s = new PaceActivity.BinStats();
    s.hrSum = bpm;
    s.hrCount = 1;
    return s;
  }
}
