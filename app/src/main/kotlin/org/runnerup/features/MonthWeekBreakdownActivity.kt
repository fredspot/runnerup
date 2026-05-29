/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.LinkedHashMap
import java.util.Locale
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.core.util.CardPressHelper
import org.runnerup.core.util.Formatter
import org.runnerup.core.util.WeekCalendarUtil
import org.runnerup.data.DBHelper

/**
 * Lists Monday–Sunday weeks overlapping a calendar month with running aggregates; opens History
 * for the full month from any row or the toolbar action.
 */
class MonthWeekBreakdownActivity : AppCompatActivity() {

  private var year = 0
  /** 1–12 */
  private var month = 0
  /** 0–11 for History filter */
  private var monthZeroBased = 0

  private lateinit var mDB: android.database.sqlite.SQLiteDatabase
  private lateinit var formatter: Formatter
  private val weekRows = ArrayList<WeekRow>()
  private lateinit var adapter: WeekListAdapter
  private var emptyView: TextView? = null

  private class WeekRow(
      val weekStartMillis: Long,
      val title: String,
      val runCount: Int,
      val totalDistanceM: Double,
      val avgPaceSecPerKm: Double,
      val avgRunLengthM: Double,
  )

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.month_week_breakdown)

    year = intent.getIntExtra(EXTRA_YEAR, Calendar.getInstance().get(Calendar.YEAR))
    month = intent.getIntExtra(EXTRA_MONTH, 1)
    if (month < 1) {
      month = 1
    }
    if (month > 12) {
      month = 12
    }
    monthZeroBased = month - 1

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      title = formatToolbarTitle()
    }

    mDB = DBHelper.getReadableDatabase(this)
    formatter = Formatter(this)

    val recyclerView = findViewById<RecyclerView>(R.id.month_week_list)
    emptyView = findViewById(R.id.month_week_empty)
    recyclerView.layoutManager = LinearLayoutManager(this)
    val spacingPx = (16 * resources.displayMetrics.density).toInt()
    recyclerView.addItemDecoration(CardSpacingDecoration(spacingPx))
    adapter = WeekListAdapter()
    recyclerView.adapter = adapter
    adapter.setOnItemClickListener { openMonthHistory() }

    val rootView = findViewById<View>(R.id.month_week_breakdown_layout)
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
      val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
      val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
      mlp.topMargin = insets.top
      WindowInsetsCompat.CONSUMED
    }

    loadWeekRows()
  }

  private fun formatToolbarTitle(): String {
    val c = Calendar.getInstance()
    c.set(Calendar.YEAR, year)
    c.set(Calendar.MONTH, monthZeroBased)
    c.set(Calendar.DAY_OF_MONTH, 1)
    return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(c.time)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.month_week_breakdown, menu)
    return true
  }

  override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
    if (item.itemId == R.id.menu_view_month_history) {
      openMonthHistory()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(mDB)
  }

  override fun onSupportNavigateUp(): Boolean {
    onBackPressed()
    return true
  }

  private fun loadWeekRows() {
    weekRows.clear()

    val monthStart = Calendar.getInstance()
    monthStart.set(Calendar.YEAR, year)
    monthStart.set(Calendar.MONTH, monthZeroBased)
    monthStart.set(Calendar.DAY_OF_MONTH, 1)
    monthStart.set(Calendar.HOUR_OF_DAY, 0)
    monthStart.set(Calendar.MINUTE, 0)
    monthStart.set(Calendar.SECOND, 0)
    monthStart.set(Calendar.MILLISECOND, 0)
    val monthStartSec = monthStart.timeInMillis / 1000

    val monthEnd = monthStart.clone() as Calendar
    monthEnd.add(Calendar.MONTH, 1)
    val monthEndSecExclusive = monthEnd.timeInMillis / 1000

    val weekStarts =
        WeekCalendarUtil.buildMondayWeekStartsInRange(monthStartSec, monthEndSecExclusive)

    val byWeek = LinkedHashMap<Long, Agg>()
    for (ws in weekStarts) {
      byWeek[ws] = Agg()
    }

    var queryStartSec = 0L
    var queryEndSecExclusive = 0L
    if (weekStarts.isNotEmpty()) {
      queryStartSec = weekStarts[0] / 1000L
      queryEndSecExclusive = weekStarts[weekStarts.size - 1] / 1000L + 7L * 24 * 3600
    }

    val sql =
        "SELECT ${Constants.DB.ACTIVITY.START_TIME}, ${Constants.DB.ACTIVITY.DISTANCE}, " +
            "${Constants.DB.ACTIVITY.TIME} FROM ${Constants.DB.ACTIVITY.TABLE} WHERE " +
            "${Constants.DB.ACTIVITY.SPORT} = ? AND ${Constants.DB.ACTIVITY.DELETED} = 0 AND " +
            "${Constants.DB.ACTIVITY.START_TIME} >= ? AND ${Constants.DB.ACTIVITY.START_TIME} < ?"

    val args =
        arrayOf(
            Constants.DB.ACTIVITY.SPORT_RUNNING.toString(),
            queryStartSec.toString(),
            queryEndSecExclusive.toString(),
        )

    val locale = Locale.getDefault()
    val dayFmt = SimpleDateFormat("EEE MMM d", locale)

    mDB.rawQuery(sql, args).use { cursor ->
      while (cursor.moveToNext()) {
        val startTimeSec = cursor.getLong(0)
        val distanceM = cursor.getDouble(1)
        val timeSec = cursor.getLong(2)

        val act = Calendar.getInstance()
        act.timeInMillis = startTimeSec * 1000L
        val weekStart = WeekCalendarUtil.mondayWeekStartMillis(act)
        val agg = byWeek[weekStart]
        if (agg != null) {
          agg.addRun(distanceM, timeSec)
        }
      }
    }

    for (ws in weekStarts) {
      val agg = byWeek[ws]
      if (agg == null || agg.runCount == 0) {
        continue
      }
      val mon = Calendar.getInstance()
      mon.timeInMillis = ws
      val sun = mon.clone() as Calendar
      sun.add(Calendar.DAY_OF_MONTH, 6)
      val title = dayFmt.format(mon.time) + " \u2013 " + dayFmt.format(sun.time)

      var avgPaceSecPerKm = 0.0
      if (agg.totalDistanceM > 0) {
        avgPaceSecPerKm = agg.totalTimeSec / (agg.totalDistanceM / 1000.0)
      }
      val avgRunLengthM = if (agg.runCount > 0) agg.totalDistanceM / agg.runCount else 0.0

      weekRows.add(
          WeekRow(ws, title, agg.runCount, agg.totalDistanceM, avgPaceSecPerKm, avgRunLengthM))
    }

    adapter.notifyDataSetChanged()
    updateEmptyState()
  }

  private fun updateEmptyState() {
    emptyView?.visibility = if (weekRows.isEmpty()) View.VISIBLE else View.GONE
  }

  private fun openMonthHistory() {
    val intent = Intent(this, MainLayout::class.java)
    intent.putExtra("HISTORY_TAB", true)
    intent.putExtra("FILTER_YEAR", year)
    intent.putExtra("FILTER_MONTH", monthZeroBased)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    setResult(RESULT_OK)
    startActivity(intent)
    finish()
  }

  private class Agg {
    var runCount = 0
    var totalDistanceM = 0.0
    var totalTimeSec = 0L

    fun addRun(distanceM: Double, timeSec: Long) {
      runCount++
      totalDistanceM += distanceM
      totalTimeSec += timeSec
    }
  }

  private class CardSpacingDecoration(private val spacingPx: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
      val position = parent.getChildAdapterPosition(view)
      if (position == RecyclerView.NO_POSITION) return
      if (position < state.itemCount - 1) {
        outRect.bottom = spacingPx
      }
    }
  }

  private inner class WeekListAdapter : RecyclerView.Adapter<WeekViewHolder>() {
    private var onItemClickListener: ((WeekRow) -> Unit)? = null

    fun setOnItemClickListener(listener: ((WeekRow) -> Unit)?) {
      onItemClickListener = listener
    }

    override fun getItemCount(): Int = weekRows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
      val inflater = LayoutInflater.from(this@MonthWeekBreakdownActivity)
      val view = inflater.inflate(R.layout.statistics_detail_row, parent, false)
      return WeekViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
      CardPressHelper.clearPressState(holder.itemView)
      val row = weekRows[position]

      holder.monthText.text = row.title
      holder.runCountText.text = row.runCount.toString() + " runs"
      holder.totalDistanceText.text =
          formatter.formatDistance(Formatter.Format.TXT_SHORT, row.totalDistanceM.toLong())

      if (row.totalDistanceM > 0 && row.avgPaceSecPerKm > 0) {
        holder.avgPaceText.text =
            formatter.formatPaceFromSecPerKm(Formatter.Format.TXT_SHORT, row.avgPaceSecPerKm)
      } else {
        holder.avgPaceText.text = "-"
      }

      holder.avgRunLengthText.text =
          formatter.formatDistance(Formatter.Format.TXT_SHORT, row.avgRunLengthM.toLong())

      holder.itemView.setOnClickListener {
        onItemClickListener?.invoke(row)
      }
    }
  }

  private class WeekViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val monthText: TextView = itemView.findViewById(R.id.month_text)
    val runCountText: TextView = itemView.findViewById(R.id.run_count_text)
    val totalDistanceText: TextView = itemView.findViewById(R.id.total_distance_text)
    val avgPaceText: TextView = itemView.findViewById(R.id.avg_pace_text)
    val avgRunLengthText: TextView = itemView.findViewById(R.id.avg_run_length_text)
  }

  companion object {
    const val EXTRA_YEAR = "YEAR"
    const val EXTRA_MONTH = "MONTH"
  }
}
