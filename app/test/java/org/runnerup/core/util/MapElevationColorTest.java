package org.runnerup.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MapElevationColorTest {

  @Test
  public void color_usesFallbackWhenRangeIsTiny() {
    assertEquals(MapElevationColor.FALLBACK, MapElevationColor.color(51, 51, 51.2));
  }

  @Test
  public void color_highIsRedderThanLow() {
    int low = MapElevationColor.color(51, 51, 56);
    int high = MapElevationColor.color(56, 51, 56);
    assertNotEquals(low, high);
    int lowRed = (low >> 16) & 0xFF;
    int highRed = (high >> 16) & 0xFF;
    int lowBlue = low & 0xFF;
    int highBlue = high & 0xFF;
    assertTrue(highRed > lowRed);
    assertTrue(lowBlue > highBlue);
  }
}
