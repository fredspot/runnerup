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

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.GridLabelRenderer
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.runnerup.R
import org.runnerup.analytics.YearlyCumulativeCalculator
import org.runnerup.common.util.Constants
import org.runnerup.data.DBHelper

class YearlyCumulativeActivity : AppCompatActivity() {

  private var mDB: android.database.sqlite.SQLiteDatabase? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.yearly_cumulative)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setTitle(R.string.yearly_progress_title)
    }

    mDB = DBHelper.getReadableDatabase(this)

    val rootView = findViewById<View>(R.id.yearly_cumulative_root)
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
      val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
      val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
      mlp.topMargin = insets.top
      WindowInsetsCompat.CONSUMED
    }

    loadCumulativeData()
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(mDB)
  }

  override fun onSupportNavigateUp(): Boolean {
    onBackPressed()
    return true
  }

  private fun loadCumulativeData() {
    Log.d(TAG, "Forcing yearly cumulative recomputation...")
    YearlyCumulativeCalculator.computeCumulative(mDB)

    val cal = Calendar.getInstance()
    val currentYear = cal.get(Calendar.YEAR)
    val lastYear = currentYear - 1

    val labelCurrentYear = findViewById<TextView>(R.id.label_current_year)
    val labelLastYear = findViewById<TextView>(R.id.label_last_year)
    labelCurrentYear.text = currentYear.toString()
    labelLastYear.text = lastYear.toString()

    var currentYearData = loadYearData(currentYear)
    val lastYearData = loadYearData(lastYear)

    val calToday = Calendar.getInstance()
    val currentDayOfYear = calToday.get(Calendar.DAY_OF_YEAR)
    currentYearData = filterDataUpToDay(currentYearData, currentDayOfYear)
    Log.d(
        TAG,
        "Filtered current year data to ${currentYearData.size} points (up to day $currentDayOfYear)")

    val graph = findViewById<GraphView>(R.id.graph)

    val currentYearTotalKm =
        if (currentYearData.isEmpty()) 0.0
        else currentYearData[currentYearData.size - 1].y / 1000.0
    val lastYearTotalKm =
        if (lastYearData.isEmpty()) 0.0 else lastYearData[lastYearData.size - 1].y / 1000.0

    val currentYearColor = ContextCompat.getColor(this, R.color.colorAccent)
    val currentYearSeries =
        LineGraphSeries(currentYearData.toTypedArray()).apply {
          color = currentYearColor
          title = currentYear.toString()
          isDrawDataPoints = false
          thickness = 4
          isDrawBackground = true
          backgroundColor =
              Color.argb(
                  15,
                  Color.red(currentYearColor),
                  Color.green(currentYearColor),
                  Color.blue(currentYearColor))
        }

    val lastYearColor = ContextCompat.getColor(this, R.color.colorTextSecondary)
    val lastYearSeries =
        LineGraphSeries(lastYearData.toTypedArray()).apply {
          color = lastYearColor
          title = lastYear.toString()
          isDrawDataPoints = false
          thickness = 3
          isDrawBackground = true
          backgroundColor =
              Color.argb(
                  10, Color.red(lastYearColor), Color.green(lastYearColor), Color.blue(lastYearColor))
        }

    graph.addSeries(currentYearSeries)
    graph.addSeries(lastYearSeries)

    graph.viewport.isXAxisBoundsManual = true
    graph.viewport.isYAxisBoundsManual = true

    var minX = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    val minY = 0.0
    var maxY = -Double.MAX_VALUE
    var currentYearLastY = 0.0
    var lastYearLastY = 0.0
    var viewportMaxY = 0.0

    if (currentYearData.isNotEmpty() || lastYearData.isNotEmpty()) {
      for (point in currentYearData) {
        minX = minOf(minX, point.x)
        maxX = maxOf(maxX, point.x)
        maxY = maxOf(maxY, point.y)
      }

      if (currentYearData.isNotEmpty()) {
        currentYearLastY = currentYearData[currentYearData.size - 1].y
      }

      for (point in lastYearData) {
        minX = minOf(minX, point.x)
        maxX = maxOf(maxX, point.x)
        maxY = maxOf(maxY, point.y)
      }

      if (lastYearData.isNotEmpty()) {
        lastYearLastY = lastYearData[lastYearData.size - 1].y
      }

      graph.viewport.setMinX(minX)
      graph.viewport.setMaxX(maxX)
      graph.viewport.setMinY(minY)
      viewportMaxY = maxY * 1.15
      graph.viewport.setMaxY(viewportMaxY)
    }

    val renderer = graph.gridLabelRenderer
    renderer.isHorizontalLabelsVisible = false
    renderer.isVerticalLabelsVisible = false
    renderer.gridStyle = GridLabelRenderer.GridStyle.NONE
    renderer.numHorizontalLabels = 0
    renderer.numVerticalLabels = 0
    renderer.horizontalAxisTitle = ""
    renderer.verticalAxisTitle = ""

    graph.setBackgroundColor(Color.TRANSPARENT)

    val currentYearTotalView = findViewById<TextView>(R.id.current_year_total)
    val lastYearTotalView = findViewById<TextView>(R.id.last_year_total)
    val finalViewportMaxY = viewportMaxY
    val finalCurrentYearLastY = currentYearLastY
    val finalLastYearLastY = lastYearLastY

    if (currentYearTotalView != null) {
      currentYearTotalView.text = String.format(Locale.getDefault(), "%.1f km", currentYearTotalKm)
      currentYearTotalView.setTextColor(currentYearColor)
      currentYearTotalView.visibility = View.VISIBLE

      if (finalCurrentYearLastY > 0 && finalViewportMaxY > 0) {
        currentYearTotalView.post {
          val params = currentYearTotalView.layoutParams as ConstraintLayout.LayoutParams
          val bias = (1.0 - (finalCurrentYearLastY / finalViewportMaxY)).toFloat()
          params.verticalBias = maxOf(0.05f, minOf(0.95f, bias))
          currentYearTotalView.layoutParams = params
        }
      }
    }

    if (lastYearTotalView != null && lastYearTotalKm > 0) {
      lastYearTotalView.text = String.format(Locale.getDefault(), "%.1f km", lastYearTotalKm)
      lastYearTotalView.setTextColor(lastYearColor)
      lastYearTotalView.visibility = View.VISIBLE

      if (finalLastYearLastY > 0 && finalViewportMaxY > 0) {
        lastYearTotalView.post {
          val params = lastYearTotalView.layoutParams as ConstraintLayout.LayoutParams
          val normalizedY = (finalLastYearLastY / finalViewportMaxY).toFloat()
          val bias = 1.0f - normalizedY - 0.03f
          params.verticalBias = maxOf(0.05f, minOf(0.95f, bias))
          lastYearTotalView.layoutParams = params
        }
      }
    }
  }

  private fun loadYearData(year: Int): MutableList<DataPoint> {
    val dataPoints = ArrayList<DataPoint>()

    val sql =
        "SELECT ${Constants.DB.YEARLY_CUMULATIVE.DATE}, " +
            "${Constants.DB.YEARLY_CUMULATIVE.CUMULATIVE_KM} " +
            "FROM ${Constants.DB.YEARLY_CUMULATIVE.TABLE} " +
            "WHERE ${Constants.DB.YEARLY_CUMULATIVE.YEAR} = ? " +
            "ORDER BY ${Constants.DB.YEARLY_CUMULATIVE.DATE}"

    mDB!!.rawQuery(sql, arrayOf(year.toString())).use { cursor ->
      val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

      while (cursor.moveToNext()) {
        val dateStr = cursor.getString(0)
        val cumulativeKm = cursor.getDouble(1)

        try {
          val date = dateFormat.parse(dateStr)
          if (date != null) {
            val cal = Calendar.getInstance()
            cal.time = date
            val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
            dataPoints.add(DataPoint(dayOfYear.toDouble(), cumulativeKm))
          }
        } catch (e: Exception) {
          Log.e(TAG, "Error parsing date: $dateStr", e)
        }
      }
    }

    Log.d(TAG, "Loaded ${dataPoints.size} data points for year $year")
    return dataPoints
  }

  private fun filterDataUpToDay(dataPoints: List<DataPoint>, maxDay: Int): MutableList<DataPoint> {
    val filtered = ArrayList<DataPoint>()
    for (point in dataPoints) {
      if (point.x <= maxDay) {
        filtered.add(point)
      }
    }
    return filtered
  }

  companion object {
    private const val TAG = "YearlyCumulativeAct"
  }
}
