package org.runnerup.tracking.elevation;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ElevationGainTest {

  @Test
  public void compute_ignoresNoiseBelowThreshold() {
    double[] elev = {100, 101, 102, 101, 100};
    ElevationGain.Result result = ElevationGain.compute(elev, 3.0);
    assertEquals(0, result.gainM, 0.01);
    assertEquals(0, result.lossM, 0.01);
  }

  @Test
  public void compute_countsAscentAndDescent() {
    double[] elev = {100, 110, 120, 105, 90};
    ElevationGain.Result result = ElevationGain.compute(elev, 3.0);
    assertEquals(20, result.gainM, 0.01);
    assertEquals(30, result.lossM, 0.01);
  }

  @Test
  public void compute_skipsNaN() {
    double[] elev = {100, Double.NaN, 110};
    ElevationGain.Result result = ElevationGain.compute(elev, 3.0);
    assertEquals(10, result.gainM, 0.01);
  }
}
