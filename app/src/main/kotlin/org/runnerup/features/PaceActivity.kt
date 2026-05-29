/*
 * Copyright (C) 2024 RunnerUp
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.runnerup.features

import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Calendar
import java.util.Locale
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.data.DBHelper
import org.runnerup.ui.common.widget.TitleSpinner

class PaceActivity : AppCompatActivity() {

  private var mDB: SQLiteDatabase? = null
  private lateinit var metricSpinner: TitleSpinner
  private lateinit var chart: PaceChart

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.pace)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setTitle(R.string.statistics_pace)
    }

    val rootView = findViewById<View>(R.id.pace_root)
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
      val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
      val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
      mlp.topMargin = insets.top
      WindowInsetsCompat.CONSUMED
    }

    mDB = DBHelper.getReadableDatabase(this)

    metricSpinner = findViewById(R.id.metric_spinner)
    chart = findViewById(R.id.pace_chart)

    val labels =
        arrayOf(getString(R.string.pace_metric_laps), getString(R.string.pace_metric_avg_hr))
    metricSpinner.setArrayEntries(labels)
    metricSpinner.setViewSelection(0)
    metricSpinner.setViewValue(0)

    metricSpinner.setViewOnItemSelectedListener(
        object : AdapterView.OnItemSelectedListener {
          override fun onItemSelected(
              parent: AdapterView<*>?,
              view: View?,
              position: Int,
              id: Long,
          ) {
            metricSpinner.setViewSelection(position)
            metricSpinner.setViewValue(position)
            loadData(if (position == 0) METRIC_LAPS else METRIC_AVG_HR)
          }

          override fun onNothingSelected(parent: AdapterView<*>?) {}
        })

    loadData(METRIC_LAPS)
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(mDB)
  }

  override fun onSupportNavigateUp(): Boolean {
    onBackPressed()
    return true
  }

  private fun loadData(metric: Int) {
    val startSec = 3 * 60 + 30 // 210
    val endSec = 7 * 60 + 30 // 450
    val step = 10
    val centers = mutableListOf<Int>()
    var s = startSec
    while (s <= endSec) {
      centers.add(s)
      s += step
    }

    val yearToBins = HashMap<Int, Array<BinStats>>()

    val sql =
        "SELECT a." +
            Constants.DB.PRIMARY_KEY +
            ", a." +
            Constants.DB.ACTIVITY.START_TIME +
            ", l." +
            Constants.DB.LAP.TIME +
            ", l." +
            Constants.DB.LAP.DISTANCE +
            ", a." +
            Constants.DB.ACTIVITY.AVG_HR +
            " FROM " +
            Constants.DB.LAP.TABLE +
            " l JOIN " +
            Constants.DB.ACTIVITY.TABLE +
            " a ON l." +
            Constants.DB.LAP.ACTIVITY +
            " = a." +
            Constants.DB.PRIMARY_KEY +
            " WHERE a." +
            Constants.DB.ACTIVITY.SPORT +
            " = " +
            Constants.DB.ACTIVITY.SPORT_RUNNING +
            " AND a." +
            Constants.DB.ACTIVITY.DELETED +
            " = 0 AND l." +
            Constants.DB.LAP.DISTANCE +
            " > 0 AND l." +
            Constants.DB.LAP.TIME +
            " > 0"

    mDB!!.rawQuery(sql, null).use { c ->
      while (c.moveToNext()) {
        val startEpoch = c.getLong(1)
        val timeSec = c.getLong(2)
        val distM = c.getDouble(3)
        val hr = if (c.isNull(4)) 0 else c.getInt(4)
        val paceSecPerKm = timeSec / (distM / 1000.0)
        if (paceSecPerKm <= 0) continue

        val paceSec = Math.round(paceSecPerKm).toInt()
        if (paceSec < startSec - 5 || paceSec > endSec + 5) continue

        var binIndex = Math.round((paceSec - startSec) / step.toDouble()).toInt()
        if (binIndex < 0) binIndex = 0
        if (binIndex >= centers.size) binIndex = centers.size - 1

        val cal = Calendar.getInstance(Locale.US)
        cal.timeInMillis = startEpoch * 1000L
        val year = cal.get(Calendar.YEAR)

        var bins = yearToBins[year]
        if (bins == null) {
          bins = Array(centers.size) { BinStats() }
          yearToBins[year] = bins
        }
        bins[binIndex].laps += 1
        if (hr > 0) {
          bins[binIndex].hrSum += hr
          bins[binIndex].hrCount += 1
        }
      }
    }

    chart.setBins(centers)
    chart.setData(yearToBins)
    chart.setMetric(if (metric == METRIC_LAPS) PaceChart.Metric.LAPS else PaceChart.Metric.AVG_HR)
  }

  class BinStats {
    @JvmField var laps: Int = 0
    var hrSum: Int = 0
    var hrCount: Int = 0

    fun avgHr(): Float = if (hrCount > 0) hrSum / hrCount.toFloat() else 0f
  }

  companion object {
    private const val METRIC_LAPS = 0
    private const val METRIC_AVG_HR = 1
  }
}
