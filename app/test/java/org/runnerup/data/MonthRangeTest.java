package org.runnerup.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import org.junit.Test;

public class MonthRangeTest {

  @Test
  public void forYearMonth_january2024() {
    MonthRange range = MonthRange.forYearMonth(2024, 1);
    Calendar start = Calendar.getInstance();
    start.set(2024, Calendar.JANUARY, 1, 0, 0, 0);
    start.set(Calendar.MILLISECOND, 0);
    Calendar end = Calendar.getInstance();
    end.set(2024, Calendar.FEBRUARY, 1, 0, 0, 0);
    end.set(Calendar.MILLISECOND, 0);
    assertEquals(start.getTimeInMillis() / 1000, range.startSeconds);
    assertEquals(end.getTimeInMillis() / 1000, range.endSecondsExclusive);
  }

  @Test
  public void forYearMonth_endExclusiveAfterStart() {
    MonthRange range = MonthRange.forYearMonth(2025, 6);
    assertTrue(range.endSecondsExclusive > range.startSeconds);
  }

  @Test
  public void currentMonth_containsNow() {
    MonthRange range = MonthRange.currentMonth();
    long now = System.currentTimeMillis() / 1000;
    assertTrue(now >= range.startSeconds);
    assertTrue(now < range.endSecondsExclusive);
  }
}
