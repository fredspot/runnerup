/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import org.runnerup.BuildConfig
import org.runnerup.R
import org.runnerup.analytics.AutoComputeRunner
import org.runnerup.analytics.DailyWeatherStore
import org.runnerup.analytics.OpenMeteoWeatherClient
import org.runnerup.tracking.elevation.TopographyElevationService
import org.runnerup.analytics.MonthlyComparisonCalculator
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.BgTasks
import org.runnerup.core.util.HRZones
import org.runnerup.data.ActivityCleaner
import org.runnerup.data.ComputationTracker
import org.runnerup.data.DBHelper

/** History-tab navigation and background auto-compute for [MainLayout]. */
object MainLayoutNavigation {

  fun interface FragmentProvider {
    fun getCurrentFragment(): Fragment?
  }

  @JvmStatic
  fun handleHistoryNavigationIntent(
      activity: AppCompatActivity,
      intent: Intent?,
      pager: ViewPager2?,
      fragmentProvider: FragmentProvider,
  ) {
    if (intent == null || pager == null) return

    if (intent.hasExtra("navigate_to_tab")) {
      val tabIndex = intent.getIntExtra("navigate_to_tab", -1)
      if (tabIndex >= 0 && pager.adapter != null && tabIndex < pager.adapter!!.itemCount) {
        pager.post { pager.setCurrentItem(tabIndex, false) }
      }
      return
    }

    if (intent.getBooleanExtra("HISTORY_TAB", false)) {
      val filterYear = intent.getIntExtra("FILTER_YEAR", -1)
      val filterMonth = intent.getIntExtra("FILTER_MONTH", -1)
      if (filterYear != -1 && filterMonth != -1) {
        pager.post {
          pager.setCurrentItem(1, false)
          pager.postDelayed(
              {
                val fragment = fragmentProvider.getCurrentFragment()
                if (fragment is HistoryFragment) {
                  fragment.applyFilter(filterYear, filterMonth)
                }
              },
              100,
          )
        }
      }
    }
  }

  @JvmStatic
  fun runAutoComputeInBackground(activity: MainLayout) {
    BgTasks.runNetwork(
        {
          var db: SQLiteDatabase? = null
          try {
            db = DBHelper.getWritableDatabase(activity)
            AutoComputeRunner.runAll(db, monthlyComparisonZoneBounds(activity))
          } catch (e: Exception) {
            Log.e(
                activity.javaClass.simpleName,
                "Error during auto-computation: ${e.message}",
                e,
            )
          } finally {
            db?.let { DBHelper.closeDB(it) }
          }
        },
        { _ -> },
    )
  }

  @JvmStatic
  fun monthlyComparisonZoneBounds(context: android.content.Context): IntArray =
      MonthlyComparisonCalculator.resolveZoneBounds(HRZones(context))
}
