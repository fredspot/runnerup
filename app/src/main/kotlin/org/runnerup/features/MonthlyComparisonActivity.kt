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

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.runnerup.R
import org.runnerup.analytics.MonthlyComparisonCalculator
import org.runnerup.common.util.Constants
import org.runnerup.core.util.BgTasks
import org.runnerup.core.util.Formatter
import org.runnerup.core.util.HRZones
import org.runnerup.data.DBHelper

class MonthlyComparisonActivity : AppCompatActivity() {

  private var db: SQLiteDatabase? = null
  private lateinit var formatter: Formatter
  private lateinit var rootView: View
  private lateinit var contentScroll: ScrollView
  private lateinit var emptyHint: TextView
  private lateinit var loadingIndicator: ProgressBar
  private var isLoading = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.monthly_comparison)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setTitle(R.string.monthly_comparison_title)
    }

    rootView = findViewById(R.id.monthly_comparison_root)
    contentScroll = findViewById(R.id.monthly_comparison_content)
    emptyHint = findViewById(R.id.monthly_comparison_empty_hint)
    loadingIndicator = findViewById(R.id.monthly_comparison_loading)

    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
      val insets: Insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
      val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
      mlp.topMargin = insets.top
      WindowInsetsCompat.CONSUMED
    }

    db = DBHelper.getReadableDatabase(this)
    formatter = Formatter(this)

    MonthlyComparisonBinder.bindZoneMetricLabels(
        this,
        rootView,
        MonthlyComparisonCalculator.resolveZoneBounds(HRZones(this)),
    )
    ensureComparisonDataLoaded()
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(db)
  }

  override fun onSupportNavigateUp(): Boolean {
    onBackPressedDispatcher.onBackPressed()
    return true
  }

  private fun ensureComparisonDataLoaded() {
    if (needsRecompute()) {
      recomputeComparisonAsync()
    } else {
      loadComparisonData()
    }
  }

  private fun needsRecompute(): Boolean {
    val database = db ?: return true
    if (MonthlyComparisonCalculator.isDataStale(database)) {
      return true
    }
    val sql = "SELECT * FROM ${Constants.DB.MONTHLY_COMPARISON.TABLE} LIMIT 1"
    return database.rawQuery(sql, null).use { cursor ->
      MonthlyComparisonBinder.needsZonePaceRecompute(cursor)
    }
  }

  private fun recomputeComparisonAsync() {
    setLoading(true)
    BgTasks.run(
        {
          val writableDb = DBHelper.getWritableDatabase(this@MonthlyComparisonActivity)
          try {
            MonthlyComparisonCalculator.computeComparison(
                writableDb,
                MonthlyComparisonCalculator.resolveZoneBounds(HRZones(this@MonthlyComparisonActivity)),
            )
          } finally {
            DBHelper.closeDB(writableDb)
          }
        },
        {
          DBHelper.closeDB(db)
          db = DBHelper.getReadableDatabase(this@MonthlyComparisonActivity)
          loadComparisonData()
        },
    )
  }

  private fun loadComparisonData() {
    val database = db ?: return
    val sql = "SELECT * FROM ${Constants.DB.MONTHLY_COMPARISON.TABLE} LIMIT 1"

    try {
      database.rawQuery(sql, null).use { cursor ->
        if (cursor.count == 0) {
          recomputeComparisonAsync()
          return
        }

        if (cursor.moveToFirst()) {
          try {
            val data = MonthlyComparisonBinder.readFromCursor(cursor)
            MonthlyComparisonBinder.bind(this, rootView, formatter, data)
            wireMonthClicks(data)
            applyContentState(loading = false, hasData = MonthlyComparisonBinder.hasMeaningfulData(data))
          } catch (e: Exception) {
            Log.e(TAG, "Error reading monthly comparison data: ${e.message}", e)
            applyContentState(loading = false, hasData = false)
          }
        } else {
          applyContentState(loading = false, hasData = false)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error loading comparison data: ${e.message}", e)
      applyContentState(loading = false, hasData = false)
    }
  }

  private fun setLoading(loading: Boolean) {
    if (loading) {
      applyContentState(loading = true, hasData = false)
    }
  }

  private fun wireMonthClicks(data: MonthlyComparisonBinder.Data) {
    val current = MonthlyComparisonBinder.parseHistoryMonth(data.currentMonthYear)
    if (current != null) {
      val thisMonthIds =
          intArrayOf(
              R.id.this_month_avg_pace,
              R.id.this_month_total_km,
              R.id.this_month_avg_distance_per_run,
              R.id.this_month_avg_bpm,
              R.id.this_month_pb_count,
              R.id.this_month_top25_count,
              R.id.this_month_avg_pace_zone_3,
              R.id.this_month_easy_pace,
          )
      for (id in thisMonthIds) {
        findViewById<View>(id)?.setOnClickListener { openHistory(current[0], current[1]) }
      }
    }
    wireBest(R.id.best_month_avg_pace, R.id.best_month_avg_pace_label, data.bestAvgPaceMonth)
    wireBest(R.id.best_month_total_km, R.id.best_month_total_km_label, data.bestTotalKmMonth)
    wireBest(
        R.id.best_month_avg_distance_per_run,
        R.id.best_month_avg_distance_per_run_label,
        data.bestAvgDistancePerRunMonth,
    )
    wireBest(R.id.best_month_avg_bpm, R.id.best_month_avg_bpm_label, data.bestAvgBpmMonth)
    wireBest(R.id.best_month_pb_count, R.id.best_month_pb_count_label, data.bestPbCountMonth)
    wireBest(R.id.best_month_top25_count, R.id.best_month_top25_count_label, data.bestTop25CountMonth)
    wireBest(
        R.id.best_month_avg_pace_zone_3,
        R.id.best_month_avg_pace_zone_3_label,
        data.bestZoneMonth[3],
    )
    wireBest(
        R.id.best_month_easy_pace,
        R.id.best_month_easy_pace_label,
        MonthlyComparisonBinder.bestEasyMonth(data),
    )
  }

  private fun wireBest(valueId: Int, labelId: Int, monthLabel: String?) {
    val month = MonthlyComparisonBinder.parseHistoryMonth(monthLabel) ?: return
    val click = View.OnClickListener { openHistory(month[0], month[1]) }
    findViewById<View>(valueId)?.setOnClickListener(click)
    findViewById<View>(labelId)?.setOnClickListener(click)
  }

  private fun openHistory(year: Int, monthZeroBased: Int) {
    val intent = Intent(this, MainLayout::class.java)
    intent.putExtra("HISTORY_TAB", true)
    intent.putExtra("FILTER_YEAR", year)
    intent.putExtra("FILTER_MONTH", monthZeroBased)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    startActivity(intent)
    finish()
  }

  private fun applyContentState(loading: Boolean, hasData: Boolean) {
    isLoading = loading
    val state = MonthlyComparisonBinder.resolveContentState(loading, hasData)
    loadingIndicator.visibility = if (state.showLoading) View.VISIBLE else View.GONE
    contentScroll.visibility = if (state.showTable) View.VISIBLE else View.GONE
    emptyHint.visibility = if (state.showEmpty) View.VISIBLE else View.GONE
  }

  companion object {
    private const val TAG = "MonthlyComparisonAct"
  }
}
