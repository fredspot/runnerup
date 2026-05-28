package org.runnerup.data;

import java.util.Calendar;

/** Inclusive calendar month as Unix epoch seconds (activity start_time). */
public final class MonthRange {

  public final long startSeconds;
  public final long endSecondsExclusive;

  public MonthRange(long startSeconds, long endSecondsExclusive) {
    this.startSeconds = startSeconds;
    this.endSecondsExclusive = endSecondsExclusive;
  }

  /** Month range for {@code year} and {@code month} (1-based). */
  public static MonthRange forYearMonth(int year, int month) {
    Calendar cal = Calendar.getInstance();
    cal.set(year, month - 1, 1, 0, 0, 0);
    cal.set(Calendar.MILLISECOND, 0);
    long startSeconds = cal.getTimeInMillis() / 1000;
    cal.add(Calendar.MONTH, 1);
    long endExclusive = cal.getTimeInMillis() / 1000;
    return new MonthRange(startSeconds, endExclusive);
  }

  /** Current calendar month. */
  public static MonthRange currentMonth() {
    Calendar cal = Calendar.getInstance();
    return forYearMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
  }
}
