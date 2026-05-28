package org.runnerup.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ComputationTrackerTest {

  @Test
  public void isStaleOlderThanOneHour_trueWhenOld() {
    long twoHoursAgo = System.currentTimeMillis() - (2 * 60 * 60 * 1000);
    assertTrue(ComputationTracker.isStaleOlderThanOneHour(twoHoursAgo));
  }

  @Test
  public void isStaleOlderThanOneHour_falseWhenRecent() {
    long now = System.currentTimeMillis();
    assertFalse(ComputationTracker.isStaleOlderThanOneHour(now));
  }

  @Test
  public void isStaleByCalendarMonth_trueWhenLastComputedBeforeCurrentMonth() {
    java.util.Calendar cal = java.util.Calendar.getInstance();
    cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
    cal.add(java.util.Calendar.MONTH, -1);
    assertTrue(ComputationTracker.isStaleByCalendarMonth(cal.getTimeInMillis()));
  }

  @Test
  public void isStaleByCalendarMonth_falseWhenComputedThisMonth() {
    assertFalse(ComputationTracker.isStaleByCalendarMonth(System.currentTimeMillis()));
  }
}
