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

  @Test
  public void compute_slowPaceWithDenseNoisyPointsMatchesTrueHillGain() {
    // 1000 m route with one 20 m hill (100 m -> 120 m -> 100 m), sampled every 5 m (slow pace)
    // with ±4 m alternating GPS/DEM jitter that would otherwise inflate gain by ~300 m.
    int n = 201;
    double[] dist = new double[n];
    double[] noisy = new double[n];
    for (int i = 0; i < n; i++) {
      double d = i * 5.0;
      dist[i] = d;
      double trueElev = d <= 500 ? 100.0 + 20.0 * (d / 500.0) : 120.0 - 20.0 * ((d - 500.0) / 500.0);
      double jitter = (i % 2 == 0 ? 4.0 : -4.0);
      noisy[i] = trueElev + jitter;
    }
    ElevationGain.Result result =
        ElevationGain.compute(dist, noisy, ElevationGain.DEFAULT_THRESHOLD_M);
    assertTrue("Expected gain near 20 m, got " + result.gainM, result.gainM >= 14.0 && result.gainM <= 24.0);
    assertTrue("Expected loss near 20 m, got " + result.lossM, result.lossM >= 14.0 && result.lossM <= 24.0);
  }
}
