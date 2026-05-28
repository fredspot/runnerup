/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.core.util;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Monday–Sunday week boundaries in the device default timezone. */
public final class WeekCalendarUtil {

  private WeekCalendarUtil() {}

  /**
   * Local calendar date of {@code cal} at 00:00:00.000, without inheriting wall-clock fields that
   * confuse DST (see {@link Calendar#clear} / field-based {@code set}).
   */
  private static void pinToLocalMidnight(Calendar cal) {
    int y = cal.get(Calendar.YEAR);
    int mo = cal.get(Calendar.MONTH);
    int dom = cal.get(Calendar.DAY_OF_MONTH);
    cal.clear();
    cal.set(Calendar.YEAR, y);
    cal.set(Calendar.MONTH, mo);
    cal.set(Calendar.DAY_OF_MONTH, dom);
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
  }

  /**
   * Start of local calendar day for {@code c}, rolled back to the Monday starting the week
   * (Monday–Sunday weeks).
   */
  public static long mondayWeekStartMillis(Calendar c) {
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(c.getTimeInMillis());
    pinToLocalMidnight(cal);
    int dow = cal.get(Calendar.DAY_OF_WEEK);
    int diff = (dow - Calendar.MONDAY + 7) % 7;
    cal.add(Calendar.DAY_OF_MONTH, -diff);
    pinToLocalMidnight(cal);
    return cal.getTimeInMillis();
  }

  /**
   * Ordered Monday week starts from the week containing {@code rangeStartSec} through the week
   * containing the last instant before {@code rangeEndSecExclusive}, using Unix seconds for
   * range bounds.
   */
  public static List<Long> buildMondayWeekStartsInRange(
      long rangeStartSec, long rangeEndSecExclusive) {
    List<Long> weeks = new ArrayList<>();
    Calendar start = Calendar.getInstance();
    start.setTimeInMillis(rangeStartSec * 1000L);
    long first = mondayWeekStartMillis(start);

    Calendar lastInstant = Calendar.getInstance();
    lastInstant.setTimeInMillis(rangeEndSecExclusive * 1000L);
    lastInstant.add(Calendar.MILLISECOND, -1);
    long lastWeekStart = mondayWeekStartMillis(lastInstant);

    long t = first;
    while (true) {
      weeks.add(t);
      if (t >= lastWeekStart) {
        break;
      }
      Calendar next = Calendar.getInstance();
      next.setTimeInMillis(t);
      next.add(Calendar.DAY_OF_MONTH, 7);
      t = mondayWeekStartMillis(next);
    }
    return weeks;
  }
}
