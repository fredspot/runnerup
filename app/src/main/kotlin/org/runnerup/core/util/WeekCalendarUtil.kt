/*
 * Copyright (C) 2026 RunnerUp
 */

package org.runnerup.core.util

import java.util.Calendar

/** Monday–Sunday week boundaries in the device default timezone. */
object WeekCalendarUtil {
  private fun pinToLocalMidnight(cal: Calendar) {
    val y = cal.get(Calendar.YEAR)
    val mo = cal.get(Calendar.MONTH)
    val dom = cal.get(Calendar.DAY_OF_MONTH)
    cal.clear()
    cal.set(Calendar.YEAR, y)
    cal.set(Calendar.MONTH, mo)
    cal.set(Calendar.DAY_OF_MONTH, dom)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
  }

  /** Start of local calendar day rolled back to Monday (Monday–Sunday weeks). */
  @JvmStatic
  fun mondayWeekStartMillis(c: Calendar): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = c.timeInMillis
    pinToLocalMidnight(cal)
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    val diff = (dow - Calendar.MONDAY + 7) % 7
    cal.add(Calendar.DAY_OF_MONTH, -diff)
    pinToLocalMidnight(cal)
    return cal.timeInMillis
  }

  @JvmStatic
  fun buildMondayWeekStartsInRange(
      rangeStartSec: Long,
      rangeEndSecExclusive: Long,
  ): List<Long> {
    val weeks = mutableListOf<Long>()
    val start = Calendar.getInstance()
    start.timeInMillis = rangeStartSec * 1000L
    val first = mondayWeekStartMillis(start)

    val lastInstant = Calendar.getInstance()
    lastInstant.timeInMillis = rangeEndSecExclusive * 1000L
    lastInstant.add(Calendar.MILLISECOND, -1)
    val lastWeekStart = mondayWeekStartMillis(lastInstant)

    var t = first
    while (true) {
      weeks.add(t)
      if (t >= lastWeekStart) {
        break
      }
      val next = Calendar.getInstance()
      next.timeInMillis = t
      next.add(Calendar.DAY_OF_MONTH, 7)
      t = mondayWeekStartMillis(next)
    }
    return weeks
  }
}
