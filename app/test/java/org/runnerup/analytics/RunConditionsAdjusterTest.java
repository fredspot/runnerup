package org.runnerup.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RunConditionsAdjusterTest {

  @Test
  public void flatRunWithoutTemperatureKeepsRawPace() {
    double adjusted = RunConditionsAdjuster.adjustedPace(300, 0, 1000, null);
    assertEquals(300, adjusted, 0.01);
  }

  @Test
  public void uphillPaceIsFasterWhenExpressedAsFlat() {
    double raw = 360;
    double adjusted = RunConditionsAdjuster.gradeAdjustedPace(raw, 50, 1000);
    assertTrue(adjusted < raw);
    assertTrue(adjusted > raw * 0.5);
  }

  @Test
  public void heatRemovesTheExpectedSlowdown() {
    double mild = RunConditionsAdjuster.adjustedPace(300, 0, 5000, 10.0);
    double hot = RunConditionsAdjuster.adjustedPace(300, 0, 5000, 25.0);
    assertEquals(300, mild, 0.01);
    assertEquals(300 / 1.04, hot, 0.01);
  }

  @Test
  public void coldAddsASmallerFactor() {
    assertEquals(1.01, RunConditionsAdjuster.temperatureFactor(0.0), 0.0001);
  }

  @Test
  public void gradeIsClamped() {
    assertEquals(0.20, RunConditionsAdjuster.grade(500, 1000), 0.0001);
    assertEquals(-0.20, RunConditionsAdjuster.grade(-500, 1000), 0.0001);
  }
}
