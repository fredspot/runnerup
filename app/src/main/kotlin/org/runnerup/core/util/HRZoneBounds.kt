/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 */

package org.runnerup.core.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Pair
import androidx.preference.PreferenceManager
import org.runnerup.R

class HRZoneBounds {
  constructor(ctx: Context) : this(ctx.resources, PreferenceManager.getDefaultSharedPreferences(ctx))

  constructor(res: Resources, prefs: SharedPreferences) {
    val pct = res.getString(R.string.pref_hrz_thresholds)
    if (prefs.contains(pct)) {
      val limits = SafeParse.parseIntList(prefs.getString(pct, ""))
      if (limits != null) {
        zoneLimitsPct = limits
      }
    }
  }

  private var zoneLimitsPct: IntArray =
      intArrayOf(
          63,
          71,
          78,
          85,
          92,
      )

  val zoneCount: Int
    get() = zoneLimitsPct.size

  fun getZoneLimits(zone: Int): Pair<Int, Int>? {
    val z = zone - 1
    if (z < 0 || z >= zoneLimitsPct.size) {
      return null
    }
    return if (z + 1 < zoneLimitsPct.size) {
      Pair(zoneLimitsPct[z], zoneLimitsPct[z + 1])
    } else {
      Pair(zoneLimitsPct[z], 100)
    }
  }

  fun computeHRZone(zone: Int, maxHR: Int): Pair<Int, Int>? {
    val limits = getZoneLimits(zone) ?: return null
    return Pair(
        kotlin.math.round(limits.first * maxHR / 100.0).toInt(),
        kotlin.math.round(limits.second * maxHR / 100.0).toInt(),
    )
  }

  companion object {
    @JvmStatic
    fun computeMaxHR(age: Int, male: Boolean): Int =
        if (male) {
          kotlin.math.round(214 - age * 0.8f).toInt()
        } else {
          kotlin.math.round(209 - age * 0.7f).toInt()
        }
  }
}
