/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.features

import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Calendar
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.core.util.WeekCalendarUtil
import org.runnerup.data.DBHelper

class WeeklyKmActivity : AppCompatActivity() {

  private var mDB: SQLiteDatabase? = null
  private lateinit var chart: WeeklyKmChart
  private lateinit var scroll: HorizontalScrollView
  private lateinit var emptyHint: TextView

  private var rangeStartSec: Long = 0
  private var rangeEndSecExclusive: Long = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.weekly_km)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setTitle(R.string.weekly_km_title)
    }

    mDB = DBHelper.getReadableDatabase(this)
    chart = findViewById(R.id.weekly_km_chart)
    scroll = findViewById(R.id.weekly_km_scroll)
    emptyHint = findViewById(R.id.weekly_km_empty_hint)

    val rootView = findViewById<View>(R.id.weekly_km_root)
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
      val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
      val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
      mlp.topMargin = insets.top
      WindowInsetsCompat.CONSUMED
    }

    applyDefaultRange()
    refreshChart()
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(mDB)
  }

  override fun onSupportNavigateUp(): Boolean {
    onBackPressed()
    return true
  }

  /**
   * Default range: ~52 Monday-Sunday weeks ending with the current (in-progress) week.
   *
   * End is the Monday strictly after today (exclusive), start is 52 weeks before that Monday.
   * That guarantees the current week is always the rightmost dot.
   */
  private fun applyDefaultRange() {
    val today = Calendar.getInstance()
    val currentWeekStart = WeekCalendarUtil.mondayWeekStartMillis(today)
    val end = Calendar.getInstance()
    end.timeInMillis = currentWeekStart
    end.add(Calendar.WEEK_OF_YEAR, 1) // exclusive upper bound: Monday after current week
    val start = Calendar.getInstance()
    start.timeInMillis = currentWeekStart
    start.add(Calendar.WEEK_OF_YEAR, -52)

    rangeStartSec = start.timeInMillis / 1000L
    rangeEndSecExclusive = end.timeInMillis / 1000L
  }

  private fun refreshChart() {
    val weekStarts =
        WeekCalendarUtil.buildMondayWeekStartsInRange(rangeStartSec, rangeEndSecExclusive)
    val kmByWeek = HashMap<Long, Double>()
    for (ws in weekStarts) {
      kmByWeek[ws] = 0.0
    }

    val sql =
        "SELECT " +
            Constants.DB.ACTIVITY.START_TIME +
            ", " +
            Constants.DB.ACTIVITY.DISTANCE +
            " FROM " +
            Constants.DB.ACTIVITY.TABLE +
            " WHERE " +
            Constants.DB.ACTIVITY.SPORT +
            " = ? AND " +
            Constants.DB.ACTIVITY.DELETED +
            " = 0 AND " +
            Constants.DB.ACTIVITY.START_TIME +
            " >= ? AND " +
            Constants.DB.ACTIVITY.START_TIME +
            " < ?"

    val args =
        arrayOf(
            Constants.DB.ACTIVITY.SPORT_RUNNING.toString(),
            rangeStartSec.toString(),
            rangeEndSecExclusive.toString(),
        )

    var totalKm = 0.0
    mDB!!.rawQuery(sql, args).use { cursor ->
      while (cursor.moveToNext()) {
        val startTimeSec = cursor.getLong(0)
        val meters = cursor.getDouble(1)
        val act = Calendar.getInstance()
        act.timeInMillis = startTimeSec * 1000L
        val weekStart = WeekCalendarUtil.mondayWeekStartMillis(act)
        val prev = kmByWeek[weekStart]
        if (prev != null) {
          val km = meters / 1000.0
          kmByWeek[weekStart] = prev + km
          totalKm += km
        }
      }
    }

    emptyHint.visibility = if (totalKm <= 0) View.VISIBLE else View.GONE
    scroll.visibility = if (totalKm <= 0) View.GONE else View.VISIBLE

    val n = weekStarts.size
    val points = arrayOfNulls<WeeklyKmChart.WeekPoint>(n)
    var maxKm = 0.0
    for (i in 0 until n) {
      val ws = weekStarts[i]
      val km = kmByWeek[ws]!!
      val isCurrent = i == n - 1
      points[i] = WeeklyKmChart.WeekPoint(ws, km, isCurrent)
      if (km > maxKm) {
        maxKm = km
      }
    }
    if (maxKm <= 0) {
      maxKm = 1.0
    }

    chart.setData(points, maxKm)
    // Scroll to the rightmost (latest) week once layout settles.
    scroll.post { scroll.fullScroll(View.FOCUS_RIGHT) }
  }
}
