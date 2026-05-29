/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 */

package org.runnerup.core.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import android.util.Pair
import androidx.preference.PreferenceManager
import org.runnerup.R
import java.util.Vector

class HRZones {
  private var zones: IntArray? = null
  private val key: String
  private val prefs: SharedPreferences

  constructor(ctx: Context) : this(ctx.resources, PreferenceManager.getDefaultSharedPreferences(ctx))

  constructor(res: Resources, p: SharedPreferences) {
    key = res.getString(R.string.pref_hrz_values)
    prefs = p
    reload()
  }

  fun reload() {
    val str = prefs.getString(key, null)
    zones =
        if (str != null) {
          SafeParse.parseIntList(str)
        } else {
          null
        }
    if (zones != null) {
      System.err.print("loaded: ($str)")
      for (zone in zones!!) {
        System.err.print(" $zone")
      }
      Log.e(javaClass.name, "")
    }
  }

  val isConfigured: Boolean
    get() = zones != null

  val count: Int
    get() = if (zones != null) zones!!.size - 1 else 0

  fun getZone(value: Double): Double {
    val z = zones ?: return 0.0
    var i = 0
    while (i < z.size) {
      if (z[i] >= value) {
        break
      }
      i++
    }
    if (i == z.size) {
      return (i - 1).toDouble()
    }
    val lo = if (i == 0) 0.0 else z[i - 1].toDouble()
    val hi = z[i].toDouble()
    val add = (value - lo) / (hi - lo)
    Log.e(javaClass.name, "value: $value, z: $i, lo: $lo, hi: $hi, add: $add")
    return i + add
  }

  fun getZoneInt(value: Double): Int {
    val z = zones ?: return 0
    var i = 0
    while (i < z.size) {
      if (z[i] >= value) {
        return i
      }
      i++
    }
    return i - 1
  }

  fun getHRValues(zone: Int): Pair<Int, Int>? {
    val z = zones ?: return null
    if (zone < z.size) {
      return if (zone == 0) {
        Pair(0, z[0])
      } else {
        Pair(z[zone - 1], z[zone])
      }
    }
    return null
  }

  fun save(vals: Vector<Int>) {
    zones = IntArray(vals.size) { i -> vals[i] }
    prefs.edit().putString(key, SafeParse.storeIntList(zones!!)).apply()
  }

  fun clear() {
    zones = null
    prefs.edit().remove(key).apply()
  }

  fun match(minValue: Double, maxValue: Double): Int =
      (getZone((minValue + maxValue) / 2) + 0.5).toInt()
}
