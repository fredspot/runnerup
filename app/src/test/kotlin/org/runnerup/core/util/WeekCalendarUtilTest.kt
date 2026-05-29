package org.runnerup.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WeekCalendarUtilTest {

  @Test
  fun mondayWeekStartMillis_rollsBackToMonday() {
    val cal = Calendar.getInstance()
    cal.set(2026, Calendar.MAY, 28, 15, 30, 0) // Wednesday
    cal.set(Calendar.MILLISECOND, 0)
    val monday = WeekCalendarUtil.mondayWeekStartMillis(cal)
    val check = Calendar.getInstance()
    check.timeInMillis = monday
    assertEquals(Calendar.MONDAY, check.get(Calendar.DAY_OF_WEEK))
    assertEquals(0, check.get(Calendar.HOUR_OF_DAY))
  }

  @Test
  fun buildMondayWeekStartsInRange_singleWeek() {
    val cal = Calendar.getInstance()
    cal.set(2026, Calendar.MAY, 28, 12, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startSec = cal.timeInMillis / 1000
    val endSec = startSec + 3600
    val weeks = WeekCalendarUtil.buildMondayWeekStartsInRange(startSec, endSec)
    assertEquals(1, weeks.size)
    assertTrue(weeks[0] <= startSec * 1000)
  }
}
