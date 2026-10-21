package org.runnerup.tracking.elevation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ElevationProfileTest {

  @Test
  public void prepare_shiftsYSoProfileStartsAtZero() {
    double[] x = {0, 1000, 2000};
    double[] y = {51, 56, 53};
    ElevationProfile.Series series = ElevationProfile.prepare(x, y, 100);
    assertEquals(51, series.yOffset, 0.01);
    assertEquals(0, series.plotY[0], 0.01);
    assertEquals(5, series.plotY[1], 0.01);
    assertEquals(2, series.plotY[2], 0.01);
  }

  @Test
  public void prepare_downsamplesLongTracks() {
    double[] x = new double[20];
    double[] y = new double[20];
    for (int i = 0; i < 20; i++) {
      x[i] = i * 10;
      y[i] = 50 + i;
    }
    ElevationProfile.Series series = ElevationProfile.prepare(x, y, 5);
    assertEquals(5, series.x.length);
    assertEquals(0, series.x[0], 0.01);
    assertEquals(190, series.x[4], 0.01);
  }
}
