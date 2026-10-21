package org.runnerup.tracking.elevation;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TopographyInterpolatorTest {

  @Test
  public void interpolate_midpointBetweenSamples() {
    double[] sampleD = {0, 100};
    double[] sampleE = {10, 30};
    double[] track = {0, 50, 100};
    double[] out = TopographyInterpolator.interpolate(sampleD, sampleE, track);
    assertEquals(10, out[0], 0.01);
    assertEquals(20, out[1], 0.01);
    assertEquals(30, out[2], 0.01);
  }

  @Test
  public void interpolate_clampsOutsideRange() {
    double[] sampleD = {10, 20};
    double[] sampleE = {5, 15};
    double[] track = {0, 30};
    double[] out = TopographyInterpolator.interpolate(sampleD, sampleE, track);
    assertEquals(5, out[0], 0.01);
    assertEquals(15, out[1], 0.01);
  }

  @Test
  public void downsampleIndices_includesEndsAndCapsCount() {
    int[] indices = TopographyInterpolator.downsampleIndices(11, 5);
    assertEquals(5, indices.length);
    assertEquals(0, indices[0]);
    assertEquals(10, indices[4]);
  }

  @Test
  public void downsampleIndices_returnsAllWhenFewerThanMax() {
    int[] indices = TopographyInterpolator.downsampleIndices(3, 10);
    assertArrayEquals(new int[] {0, 1, 2}, indices);
  }
}
