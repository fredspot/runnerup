package org.runnerup.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GraphAxisTest {

  @Test
  public void zoomedBounds_smallElevationRangeFillsTheChart() {
    double[] bounds = GraphAxis.zoomedBounds(51, 56, 6, false);
    assertTrue(bounds[0] < 51);
    assertTrue(bounds[1] > 56);
    assertTrue(bounds[1] - bounds[0] <= 12);
    assertTrue((56 - 51) / (bounds[1] - bounds[0]) > 0.4);
  }

  @Test
  public void zoomedBounds_doesNotStartAtZeroWhenValuesAreHigh() {
    double[] bounds = GraphAxis.zoomedBounds(180, 220, 20, false);
    assertTrue(bounds[0] > 100);
    assertTrue(bounds[1] > 220);
    assertTrue(bounds[1] - bounds[0] >= 20);
  }

  @Test
  public void zoomedBounds_expandsTinyRange() {
    double[] bounds = GraphAxis.zoomedBounds(100, 102, 20, false);
    assertEquals(20, bounds[1] - bounds[0], 0.01);
  }

  @Test
  public void zoomedBounds_canClampHeartRateToZero() {
    double[] bounds = GraphAxis.zoomedBounds(5, 10, 30, true);
    assertEquals(0, bounds[0], 0.01);
    assertTrue(bounds[1] >= 30);
  }

  @Test
  public void omitWarmup_onlyForLongDistanceGraphs() {
    assertTrue(GraphAxis.omitWarmup(5000, true));
    assertTrue(!GraphAxis.omitWarmup(3000, true));
    assertTrue(!GraphAxis.omitWarmup(10000, false));
  }

  @Test
  public void sharedXRange_trimsWarmupAndUsesWidestSpan() {
    double[] range =
        GraphAxis.sharedXRange(
            true,
            new double[] {0, 8000},
            new double[] {100, 7500},
            new double[] {0, 200});
    assertEquals(GraphAxis.WARMUP_OMIT_METERS, range[0], 0.01);
    assertEquals(8000, range[1], 0.01);
  }

  @Test
  public void sharedXRange_withoutWarmupKeepsTheStart() {
    double[] range =
        GraphAxis.sharedXRange(false, new double[] {0, 1200}, new double[] {40, 900});
    assertEquals(0, range[0], 0.01);
    assertEquals(1200, range[1], 0.01);
  }
}
