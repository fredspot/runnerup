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

  @Test
  public void warmHumidDayAddsExtraSlowdownOverDryHeat() {
    double dryWarm = RunConditionsAdjuster.adjustedPace(300, 0, 5000, 22.0, 40.0);
    double humidWarm = RunConditionsAdjuster.adjustedPace(300, 0, 5000, 22.0, 85.0);
    assertTrue(humidWarm < dryWarm);
  }

  @Test
  public void dailyWeather24h_matchesHoursAndEncodesRoundTrip() {
    double[] temps = new double[24];
    double[] hums = new double[24];
    for (int i = 0; i < 24; i++) {
      temps[i] = 10.0 + i;
      hums[i] = 50.0 + i;
    }
    DailyWeather24h day = new DailyWeather24h("2026-06-15", 455, -736, temps, hums);
    String tCsv = DailyWeather24h.encode24(day.hourlyTempC);
    double[] decoded = DailyWeather24h.decode24(tCsv);
    assertEquals(24.0, decoded[14], 0.01);
    // 14:10 UTC for 40 min stays in hour 14
    long startSec = 14L * 3600L + 600L;
    DailyWeather24h.HourMatch match = day.matchWindow(startSec, 2400);
    assertEquals(24.0, match.temperatureC, 0.01);
    assertEquals(64.0, match.humidityPct, 0.01);
  }
}
