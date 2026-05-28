package org.runnerup.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BestTimesDistancesTest {

  @Test
  public void getLabel_knownDistances() {
    assertEquals("5 km", BestTimesDistances.getLabel(5000));
    assertEquals("Marathon", BestTimesDistances.getLabel(42195));
  }

  @Test
  public void indexOf_returnsIndexForTargetDistance() {
    assertEquals(1, BestTimesDistances.indexOf(5000));
    assertEquals(-1, BestTimesDistances.indexOf(999));
  }

  @Test
  public void targetDistances_hasNineEntries() {
    assertEquals(9, BestTimesDistances.TARGET_DISTANCES.length);
    assertTrue(BestTimesDistances.TARGET_DISTANCES[0] < BestTimesDistances.TARGET_DISTANCES[8]);
  }
}
