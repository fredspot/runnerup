package org.runnerup.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class StepCountFromCadenceTest {

  @Test
  public void compute_ninetyOneRpmForOneMinuteIs182Steps() {
    long[] elapsed = new long[61];
    double[] cad = new double[61];
    for (int i = 0; i < 61; i++) {
      elapsed[i] = i * 1000L;
      cad[i] = 91;
    }
    assertEquals(Integer.valueOf(182), StepCountFromCadence.compute(elapsed, cad));
  }

  @Test
  public void compute_skipsLongGaps() {
    long[] elapsed = {0, 1000, 120_000, 121_000};
    double[] cad = {90, 90, 90, 90};
    // 1s at 90 rpm = 3 steps, then a 119s gap ignored, then 1s = 3 more
    assertEquals(Integer.valueOf(6), StepCountFromCadence.compute(elapsed, cad));
  }

  @Test
  public void compute_nullWhenNoCadence() {
    long[] elapsed = {0, 1000};
    double[] cad = {Double.NaN, Double.NaN};
    assertNull(StepCountFromCadence.compute(elapsed, cad));
  }
}
