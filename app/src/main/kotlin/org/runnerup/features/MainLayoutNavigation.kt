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
    BgTasks.runDb(
        {
          var bulkRecomputeJustRan = false
          var db: SQLiteDatabase? = null
          try {
            db = DBHelper.getWritableDatabase(activity)
            val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
            val requiredStamp =
                BuildConfig.VERSION_CODE.toLong() * 1000L + BULK_RECOMPUTE_DATA_REVISION
            val appliedStamp = prefs.getLong(PREF_ACTIVITY_BULK_STAMP, 0L)
            if (appliedStamp < requiredStamp) {
              Log.i(
                  activity.javaClass.simpleName,
                  "Bulk activity recompute (apk ${BuildConfig.VERSION_CODE}, revision $BULK_RECOMPUTE_DATA_REVISION)",
              )
              val cleaner = ActivityCleaner()
              db.query(
                      DB.ACTIVITY.TABLE,
                      arrayOf(DB.PRIMARY_KEY),
                      DB.ACTIVITY.DELETED + " = ?",
                      arrayOf("0"),
                      null,
                      null,
                      DB.PRIMARY_KEY + " ASC",
                  )
                  .use { cursor ->
                    while (cursor.moveToNext()) {
                      val id = cursor.getLong(0)
                      try {
                        cleaner.recompute(db, id)
                      } catch (e: Exception) {
                        Log.w(
                            activity.javaClass.simpleName,
                            "Recompute failed for activity $id",
                            e,
                        )
                      }
                    }
                  }
              ComputationTracker.deleteTracking(
                  db,
                  ComputationTracker.TYPE_BEST_TIMES,
                  ComputationTracker.TYPE_STATISTICS,
              )
              prefs.edit().putLong(PREF_ACTIVITY_BULK_STAMP, requiredStamp).apply()
              bulkRecomputeJustRan = true
            }
            AutoComputeRunner.runAll(db, monthlyComparisonZoneBounds(activity))
            bulkRecomputeJustRan
          } catch (e: Exception) {
            Log.e(
                activity.javaClass.simpleName,
                "Error during auto-computation: ${e.message}",
                e,
            )
            false
          } finally {
            db?.let { DBHelper.closeDB(it) }
          }
        },
        { bulkRecomputeJustRan ->
          if (bulkRecomputeJustRan) {
            Toast.makeText(
                    activity,
                    R.string.activity_bulk_recompute_done,
                    Toast.LENGTH_LONG,
                )
                .show()
          }
        },
    )
  }

  @JvmStatic
  fun monthlyComparisonZoneBounds(context: android.content.Context): IntArray =
      MonthlyComparisonCalculator.resolveZoneBounds(HRZones(context))

  private const val PREF_ACTIVITY_BULK_STAMP = "activity_bulk_recompute_stamp"
  private const val BULK_RECOMPUTE_DATA_REVISION = 2
}
