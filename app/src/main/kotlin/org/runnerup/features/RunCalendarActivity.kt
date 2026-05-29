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

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.HashMap
import java.util.Locale
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.core.util.Formatter
import org.runnerup.data.DBHelper

class RunCalendarActivity : AppCompatActivity(), Constants {

  private lateinit var mDB: android.database.sqlite.SQLiteDatabase
  private lateinit var formatter: Formatter
  private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
  private val monthTitleFormat = SimpleDateFormat("LLLL yyyy", Locale.getDefault())

  private lateinit var monthTitleView: TextView
  private lateinit var emptyHint: TextView
  private val dayCells = Array(6) { arrayOfNulls<View>(7) }

  private var viewYear = 0
  private var viewMonth = 0 // 0–11

  private var statsByDay = HashMap<String, DayStats>()

  private class DayStats {
    var runCount = 0
    var totalDistanceM = 0.0
    var totalTimeSec = 0L
    var sumHr = 0L
    var hrRunsCount = 0
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_run_calendar)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setTitle(R.string.run_calendar_title)
    }

    mDB = DBHelper.getReadableDatabase(this)
    formatter = Formatter(this)

    monthTitleView = findViewById(R.id.run_calendar_month_title)
    emptyHint = findViewById(R.id.run_calendar_empty_hint)

    val prev = findViewById<ImageButton>(R.id.run_calendar_prev)
    val next = findViewById<ImageButton>(R.id.run_calendar_next)
    prev.setOnClickListener { shiftMonth(-1) }
    next.setOnClickListener { shiftMonth(1) }

    buildCalendarGrid()
    populateWeekdayRow()

    val now = Calendar.getInstance()
    viewYear = now.get(Calendar.YEAR)
    viewMonth = now.get(Calendar.MONTH)

    val rootView = findViewById<View>(R.id.run_calendar_root)
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
      val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
      val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
      mlp.topMargin = insets.top
      WindowInsetsCompat.CONSUMED
    }

    refreshMonth()
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(mDB)
  }

  override fun onSupportNavigateUp(): Boolean {
    onBackPressed()
    return true
  }

  private fun buildCalendarGrid() {
    val table = findViewById<TableLayout>(R.id.run_calendar_table)
    for (r in 0..5) {
      val row = TableRow(this)
      for (c in 0..6) {
        val cell = layoutInflater.inflate(R.layout.item_run_calendar_day, row, false)
        val lp = TableRow.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        cell.layoutParams = lp
        row.addView(cell)
        dayCells[r][c] = cell
      }
      table.addView(row)
    }
  }

  private fun populateWeekdayRow() {
    val row = findViewById<LinearLayout>(R.id.run_calendar_weekday_row)
    row.removeAllViews()
    val firstDow = Calendar.MONDAY
    val dfs = DateFormatSymbols(Locale.getDefault())
    val shortWeekdays = dfs.shortWeekdays
    var dow = firstDow
    for (i in 0..6) {
      val tv = TextView(this)
      tv.text = shortWeekdays[dow]
      tv.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary))
      tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      tv.gravity = Gravity.CENTER
      val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
      row.addView(tv, lp)
      dow++
      if (dow > Calendar.SATURDAY) {
        dow = Calendar.SUNDAY
      }
    }
  }

  private fun shiftMonth(delta: Int) {
    val c = Calendar.getInstance()
    c.set(viewYear, viewMonth, 1)
    c.add(Calendar.MONTH, delta)
    viewYear = c.get(Calendar.YEAR)
    viewMonth = c.get(Calendar.MONTH)
    refreshMonth()
  }

  private fun refreshMonth() {
    loadStatsForVisibleMonth()
    monthTitleView.text = monthTitleFormat.format(monthStartCal().time)
    emptyHint.visibility = if (statsByDay.isEmpty()) View.VISIBLE else View.GONE

    val monthCal = monthStartCal()
    val firstDayDow = monthCal.get(Calendar.DAY_OF_WEEK)
    val firstDayOfWeek = Calendar.MONDAY
    val startOffset = (firstDayDow - firstDayOfWeek + 7) % 7
    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)

    for (i in 0..41) {
      val row = i / 7
      val col = i % 7
      val cell = dayCells[row][col]!!
      val num = cell.findViewById<TextView>(R.id.run_calendar_day_number)
      val dist = cell.findViewById<TextView>(R.id.run_calendar_day_distance)

      val day = i - startOffset + 1
      if (i < startOffset || day > daysInMonth) {
        num.text = ""
        dist.visibility = View.GONE
        dist.text = ""
        cell.background = null
        cell.setOnClickListener(null)
        cell.isClickable = false
        cell.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        continue
      }

      val dayCal = monthCal.clone() as Calendar
      dayCal.set(viewYear, viewMonth, day)
      val key = dateKeyFormat.format(dayCal.time)

      num.text = String.format(Locale.getDefault(), "%d", day)
      val stats = statsByDay[key]
      if (stats != null && stats.runCount > 0) {
        num.setTextColor(ContextCompat.getColor(this, R.color.colorText))
        num.setTypeface(null, Typeface.BOLD)
        dist.visibility = View.VISIBLE
        dist.text = formatCellDistance(stats.totalDistanceM)
        cell.setBackgroundResource(R.drawable.run_calendar_day_has_run)
        cell.isClickable = true
        cell.setOnClickListener { showDaySummary(dayCal.time, stats) }
        cell.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
      } else {
        num.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary))
        num.setTypeface(null, Typeface.NORMAL)
        dist.visibility = View.GONE
        dist.text = ""
        cell.background = null
        cell.setOnClickListener(null)
        cell.isClickable = false
        cell.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
      }
    }
  }

  private fun monthStartCal(): Calendar {
    val c = Calendar.getInstance()
    c.firstDayOfWeek = Calendar.MONDAY
    c.set(viewYear, viewMonth, 1, 0, 0, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c
  }

  private fun loadStatsForVisibleMonth() {
    statsByDay = HashMap()
    val start = monthStartCal()
    val startSec = start.timeInMillis / 1000L
    val end = start.clone() as Calendar
    end.add(Calendar.MONTH, 1)
    val endSec = end.timeInMillis / 1000L

    val sql =
        "SELECT a.${Constants.DB.ACTIVITY.START_TIME}, a.${Constants.DB.ACTIVITY.DISTANCE}, " +
            "a.${Constants.DB.ACTIVITY.TIME}, a.${Constants.DB.ACTIVITY.AVG_HR} " +
            "FROM ${Constants.DB.ACTIVITY.TABLE} a WHERE a.${Constants.DB.ACTIVITY.SPORT} = ? " +
            "AND a.${Constants.DB.ACTIVITY.DELETED} = 0 AND a.${Constants.DB.ACTIVITY.START_TIME} >= ? " +
            "AND a.${Constants.DB.ACTIVITY.START_TIME} < ?"

    mDB
        .rawQuery(
            sql,
            arrayOf(
                Constants.DB.ACTIVITY.SPORT_RUNNING.toString(),
                startSec.toString(),
                endSec.toString(),
            ),
        )
        .use { cursor ->
          while (cursor.moveToNext()) {
            val startTimeSec = cursor.getLong(0)
            val distanceM = cursor.getDouble(1)
            val timeSec = cursor.getLong(2)
            var hr = 0
            if (!cursor.isNull(3)) {
              hr = cursor.getInt(3)
            }

            val key = dateKeyFormat.format(Date(startTimeSec * 1000L))
            var agg = statsByDay[key]
            if (agg == null) {
              agg = DayStats()
              statsByDay[key] = agg
            }
            agg.runCount++
            agg.totalDistanceM += distanceM
            agg.totalTimeSec += timeSec
            if (hr > 0) {
              agg.sumHr += hr
              agg.hrRunsCount++
            }
          }
        }
  }

  private fun formatCellDistance(meters: Double): String {
    val metric =
        Formatter.getUseMetric(resources, PreferenceManager.getDefaultSharedPreferences(this), null)
    if (metric) {
      if (meters >= 1000.0) {
        val km = Math.round(meters / Formatter.km_meters).toInt()
        return km.toString() + " " + getString(org.runnerup.common.R.string.metrics_distance_km)
      }
      return Math.round(meters).toString() + " " + getString(org.runnerup.common.R.string.metrics_distance_m)
    }
    if (meters >= Formatter.mi_meters * 0.99) {
      val mi = Math.round(meters / Formatter.mi_meters).toInt()
      return mi.toString() + " " + getString(org.runnerup.common.R.string.metrics_distance_mi)
    }
    return Math.round(meters).toString() + " " + getString(org.runnerup.common.R.string.metrics_distance_m)
  }

  private fun showDaySummary(day: Date, stats: DayStats) {
    val title = android.text.format.DateFormat.getMediumDateFormat(this).format(day)
    val dist = formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(stats.totalDistanceM))
    val time = formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, stats.totalTimeSec)

    val msg = StringBuilder()
    msg.append(getString(R.string.run_calendar_summary_runs, stats.runCount)).append('\n')
    msg.append(getString(R.string.run_calendar_summary_distance, dist)).append('\n')
    msg.append(getString(R.string.run_calendar_summary_time, time)).append('\n')
    if (stats.hrRunsCount > 0) {
      val avgHr = Math.round(stats.sumHr.toDouble() / stats.hrRunsCount).toInt()
      msg.append(getString(R.string.run_calendar_summary_avg_hr, avgHr))
    } else {
      msg.append(getString(R.string.run_calendar_summary_avg_hr_na))
    }

    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(msg.toString())
        .setPositiveButton(android.R.string.ok, null)
        .show()
  }
}
