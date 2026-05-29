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
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.runnerup.R
import org.runnerup.core.util.Formatter
import org.runnerup.data.BestTimesDistances
import org.runnerup.data.DBHelper
import org.runnerup.data.RunningActivityReader
import org.runnerup.ui.common.widget.TitleSpinner

class DistributionActivity : AppCompatActivity() {

  private var mDB: SQLiteDatabase? = null
  private lateinit var formatter: Formatter

  private lateinit var distanceSpinner: TitleSpinner
  private lateinit var chart: DistributionChart

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.distribution)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setTitle(R.string.statistics_distribution)
    }

    mDB = DBHelper.getReadableDatabase(this)
    formatter = Formatter(this)

    val rootView = findViewById<View>(R.id.distribution_root)
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
      val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
      val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
      mlp.topMargin = insets.top
      WindowInsetsCompat.CONSUMED
    }

    distanceSpinner = findViewById(R.id.distance_spinner)
    chart = findViewById(R.id.distribution_chart)

    // Setup distance spinner - default to 1km (index 0)
    distanceSpinner.setViewSelection(0)
    distanceSpinner.setViewValue(0)

    distanceSpinner.setViewOnItemSelectedListener(
        object : AdapterView.OnItemSelectedListener {
          override fun onItemSelected(
              parent: AdapterView<*>?,
              view: View?,
              position: Int,
              id: Long,
          ) {
            val selectedDistance = TARGET_DISTANCES[position]
            distanceSpinner.setViewSelection(position)
            distanceSpinner.setViewValue(position)
            loadDistribution(selectedDistance)
          }

          override fun onNothingSelected(parent: AdapterView<*>?) {
            // Do nothing
          }
        })

    loadDistribution(1000)
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(mDB)
  }

  override fun onSupportNavigateUp(): Boolean {
    onBackPressed()
    return true
  }

  private fun loadDistribution(targetDistance: Int) {
    Log.d(TAG, "Loading distribution for distance: ${targetDistance}m")

    val activityIds = RunningActivityReader.getRunningActivityIds(mDB!!)
    val lapTimes = mutableListOf<Long>()

    for (activityId in activityIds) {
      val laps = RunningActivityReader.getLapsWithDistance(mDB!!, activityId)

      if (targetDistance == 1000) {
        for (lap in laps) {
          val distanceRatio = lap.distanceM / targetDistance
          if (distanceRatio >= 0.95 && distanceRatio <= 1.05) {
            val pacePerKm = lap.timeSeconds / (lap.distanceM / 1000.0)
            if (pacePerKm >= 120.0 && pacePerKm <= 720.0) {
              lapTimes.add(lap.timeSeconds)
            }
          }
        }
      } else {
        val expectedLapCount = targetDistance / 1000
        for (startIdx in 0..laps.size - expectedLapCount) {
          var totalTime = 0L
          var totalDistance = 0.0

          for (i in 0 until expectedLapCount) {
            if (startIdx + i >= laps.size) break
            val lap = laps[startIdx + i]
            totalTime += lap.timeSeconds
            totalDistance += lap.distanceM
          }

          val distanceRatio = totalDistance / targetDistance
          if (distanceRatio >= 0.95 && distanceRatio <= 1.05) {
            val pacePerKm = totalTime / (totalDistance / 1000.0)
            if (pacePerKm >= 120.0 && pacePerKm <= 720.0) {
              lapTimes.add(totalTime)
            }
          }
        }
      }
    }

    Log.d(TAG, "Found ${lapTimes.size} matching lap times")

    chart.setLapTimes(lapTimes)
  }

  companion object {
    private const val TAG = "DistributionActivity"
    private val TARGET_DISTANCES = BestTimesDistances.TARGET_DISTANCES
  }
}
