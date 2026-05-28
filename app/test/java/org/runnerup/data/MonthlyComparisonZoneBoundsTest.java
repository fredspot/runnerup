package org.runnerup.data;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;
import org.runnerup.analytics.MonthlyComparisonCalculator;
import org.runnerup.common.util.Constants;

public class MonthlyComparisonZoneBoundsTest {

  @Test
  public void resolveZoneBounds_usesDefaultsWhenUnconfigured() {
    int[] bounds = MonthlyComparisonCalculator.resolveZoneBounds(null);
    assertArrayEquals(
        new int[] {
          Constants.DB.HR_ZONES.ZONE1_MIN,
          Constants.DB.HR_ZONES.ZONE1_MAX,
          Constants.DB.HR_ZONES.ZONE2_MIN,
          Constants.DB.HR_ZONES.ZONE2_MAX,
          Constants.DB.HR_ZONES.ZONE3_MIN,
          Constants.DB.HR_ZONES.ZONE3_MAX,
          Constants.DB.HR_ZONES.ZONE4_MIN,
          Constants.DB.HR_ZONES.ZONE4_MAX
        },
        bounds);
  }
}
