/*
 * Copyright (C) 2024 RunnerUp
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
import android.util.Log
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.runnerup.R
import org.runnerup.analytics.HRZoneStatsCalculator
import org.runnerup.common.util.Constants
import org.runnerup.core.util.Formatter
import org.runnerup.data.DBHelper

class HRZoneActivity : AppCompatActivity() {

  private var mDB: SQLiteDatabase? = null
  private lateinit var formatter: Formatter
  private val zoneData = ArrayList<HRZoneData>()
  private var showTime = true // true = time, false = pace

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.hr_zone)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setTitle(R.string.hr_zones_title)
    }

    mDB = DBHelper.getReadableDatabase(this)
    formatter = Formatter(this)

    val rootView = findViewById<android.view.View>(R.id.hr_zone_root)
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
      val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
      val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
      mlp.topMargin = insets.top
      WindowInsetsCompat.CONSUMED
    }

    val toggle = findViewById<Switch>(R.id.toggle_display)
    val toggleLabelLeft = findViewById<TextView>(R.id.toggle_label_left)
    val toggleLabelRight = findViewById<TextView>(R.id.toggle_label_right)

    toggle.isChecked = true // Start with Time mode (right side)
    toggle.setOnCheckedChangeListener { _, isChecked ->
      showTime = isChecked
      if (showTime) {
        toggleLabelLeft.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary))
        toggleLabelRight.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
        toggleLabelRight.paint.isFakeBoldText = true
        toggleLabelLeft.paint.isFakeBoldText = false
      } else {
        toggleLabelLeft.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
        toggleLabelRight.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary))
        toggleLabelLeft.paint.isFakeBoldText = true
        toggleLabelRight.paint.isFakeBoldText = false
      }
      updateDisplay()
    }

    loadHRZoneData()
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(mDB)
  }

  override fun onSupportNavigateUp(): Boolean {
    onBackPressed()
    return true
  }

  private fun loadHRZoneData() {
    Log.d(TAG, "Forcing HR zone recomputation...")
    HRZoneStatsCalculator.computeHRZones(mDB)

    zoneData.clear()
    val sql =
        "SELECT ${Constants.DB.HR_ZONE_STATS.ZONE_NUMBER}, " +
            "${Constants.DB.HR_ZONE_STATS.TIME_IN_ZONE}, " +
            "${Constants.DB.HR_ZONE_STATS.AVG_PACE_IN_ZONE} " +
            "FROM ${Constants.DB.HR_ZONE_STATS.TABLE} " +
            "ORDER BY ${Constants.DB.HR_ZONE_STATS.ZONE_NUMBER}"

    mDB!!.rawQuery(sql, null).use { cursor ->
      while (cursor.moveToNext()) {
        val zone = cursor.getInt(0)
        val timeInZone = cursor.getLong(1)
        val avgPace = cursor.getDouble(2)
        zoneData.add(HRZoneData(zone, timeInZone, avgPace))
        Log.d(TAG, "Loaded zone $zone: time=${timeInZone}ms, pace=$avgPace")
      }
    }

    if (zoneData.isEmpty()) {
      Log.w(TAG, "No HR zone data found")
    }

    normalizeAndDisplay()
  }

  private fun normalizeAndDisplay() {
    if (zoneData.isEmpty()) {
      return
    }

    if (showTime) {
      var maxValue = 0.0
      for (data in zoneData) {
        if (data.timeInZone > maxValue) {
          maxValue = data.timeInZone.toDouble()
        }
      }

      for (data in zoneData) {
        updateZoneDisplay(data, maxValue, 0.0)
      }
    } else {
      val minPace = 200.0 // 3:20 per km in seconds
      val maxPace = 600.0 // 10:00 per km in seconds

      for (data in zoneData) {
        updateZoneDisplay(data, maxPace, minPace)
      }
    }
  }

  private fun updateZoneDisplay(data: HRZoneData, maxValue: Double, minValue: Double) {
    val zoneNumber = data.zoneNumber

    val bar = getZoneProgressBar(zoneNumber)
    val valueView = getZoneValueView(zoneNumber)

    if (bar == null || valueView == null) {
      return
    }

    val value = if (showTime) data.timeInZone.toDouble() else data.avgPace
    val normalizedProgress: Int

    if (showTime) {
      normalizedProgress = if (maxValue > 0) ((value / maxValue) * 100).toInt() else 0
    } else {
      var clampedValue = value
      if (value < minValue) {
        clampedValue = minValue
      } else if (value > maxValue) {
        clampedValue = maxValue
      }

      normalizedProgress =
          if (value > 0) {
            (((maxValue - clampedValue) / (maxValue - minValue)) * 100).toInt()
          } else {
            0
          }
    }

    bar.progress = normalizedProgress

    if (showTime) {
      val totalSeconds = (value / 1000).toLong()
      val hours = totalSeconds / 3600
      val minutes = (totalSeconds % 3600) / 60

      if (hours > 0) {
        valueView.text = String.format("%dh %02dm", hours, minutes)
      } else {
        valueView.text = String.format("%dm", minutes)
      }
    } else {
      Log.d(TAG, "Displaying pace for zone ${data.zoneNumber}: avgPace=${data.avgPace}")
      if (data.avgPace > 0) {
        val paceStr = formatter.formatPaceFromSecPerKm(Formatter.Format.TXT_SHORT, data.avgPace)
        Log.d(TAG, "Formatted pace: $paceStr")
        valueView.text = paceStr
      } else {
        Log.d(TAG, "Pace is 0 for zone ${data.zoneNumber}")
        valueView.text = "--"
      }
    }
  }

  private fun updateDisplay() {
    normalizeAndDisplay()
  }

  private fun getZoneProgressBar(zone: Int): ProgressBar? {
    return when (zone) {
      0 -> findViewById(R.id.zone_0_bar)
      1 -> findViewById(R.id.zone_1_bar)
      2 -> findViewById(R.id.zone_2_bar)
      3 -> findViewById(R.id.zone_3_bar)
      4 -> findViewById(R.id.zone_4_bar)
      5 -> findViewById(R.id.zone_5_bar)
      else -> null
    }
  }

  private fun getZoneValueView(zone: Int): TextView? {
    return when (zone) {
      0 -> findViewById(R.id.zone_0_value)
      1 -> findViewById(R.id.zone_1_value)
      2 -> findViewById(R.id.zone_2_value)
      3 -> findViewById(R.id.zone_3_value)
      4 -> findViewById(R.id.zone_4_value)
      5 -> findViewById(R.id.zone_5_value)
      else -> null
    }
  }

  private class HRZoneData(
      val zoneNumber: Int,
      val timeInZone: Long,
      val avgPace: Double,
  )

  companion object {
    private const val TAG = "HRZoneActivity"
  }
}
