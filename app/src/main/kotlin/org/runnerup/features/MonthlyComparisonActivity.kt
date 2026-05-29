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
    if (needsZonePaceRecompute()) {
      recomputeComparisonAsync()
    } else {
      loadComparisonData()
    }
  }

  private fun needsZonePaceRecompute(): Boolean {
    val database = db ?: return true
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
            showContent(MonthlyComparisonBinder.hasMeaningfulData(data))
          } catch (e: Exception) {
            Log.e(TAG, "Error reading monthly comparison data: ${e.message}", e)
            showContent(false)
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error loading comparison data: ${e.message}", e)
      showContent(false)
    } finally {
      setLoading(false)
    }
  }

  private fun setLoading(loading: Boolean) {
    isLoading = loading
    loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
    if (loading) {
      contentScroll.visibility = View.GONE
      emptyHint.visibility = View.GONE
    }
  }

  private fun showContent(hasData: Boolean) {
    if (isLoading) {
      return
    }
    emptyHint.visibility = if (hasData) View.GONE else View.VISIBLE
    contentScroll.visibility = if (hasData) View.VISIBLE else View.GONE
  }

  companion object {
    private const val TAG = "MonthlyComparisonAct"
  }
}
