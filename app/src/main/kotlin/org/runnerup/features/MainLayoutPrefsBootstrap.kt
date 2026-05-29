/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import org.runnerup.R

/** Default preference bootstrap for [MainLayout] cold start. */
object MainLayoutPrefsBootstrap {

  @JvmStatic
  fun applyDefaultValues(activity: AppCompatActivity) {
    val pref = PreferenceManager.getDefaultSharedPreferences(activity)
    pref.edit().remove(activity.getString(R.string.pref_basic_target_type)).apply()

    PreferenceManager.setDefaultValues(activity, R.xml.settings, false)
    PreferenceManager.setDefaultValues(activity, R.xml.audio_cue_settings, true)
    PreferenceManager.setDefaultValues(activity, R.xml.settings_runtime_defaults, true)
    PreferenceManager.setDefaultValues(activity, R.xml.settings_maintenance, true)
    PreferenceManager.setDefaultValues(activity, R.xml.settings_sensors, true)
    PreferenceManager.setDefaultValues(activity, R.xml.settings_units, true)
    PreferenceManager.setDefaultValues(activity, R.xml.settings_workout, true)

    migrateMutePreference(activity, pref)
  }

  private fun migrateMutePreference(activity: AppCompatActivity, pref: SharedPreferences) {
    val res = activity.resources
    try {
      if (pref.contains(res.getString(R.string.pref_mute))) {
        val v = pref.getString(res.getString(R.string.pref_mute), "no")
        val editor = pref.edit()
        editor.putBoolean(res.getString(R.string.pref_mute_bool), v.equals("yes", ignoreCase = true))
        editor.remove(res.getString(R.string.pref_mute))
        editor.apply()
      }
    } catch (_: Exception) {
    }
  }
}
