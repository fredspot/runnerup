/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import org.runnerup.R
import org.runnerup.core.util.StartGpsAccuracyFormatter

/** Status row expand/collapse and GPS accuracy text for [StartFragment]. */
internal class StartStatusController(
    private val fragment: StartFragment,
) {

  fun toggleStatusDetails(
      expandIcon: ImageView?,
      startButton: Button?,
  ) {
    fragment.statusDetailsShown = !fragment.statusDetailsShown
    val bottomMargin =
        if (fragment.statusDetailsShown) {
          expandIcon?.setImageResource(R.drawable.ic_expand_down_white_24dp)
          fragment.resources.getDimension(R.dimen.fab_margin_68row)
        } else {
          expandIcon?.setImageResource(R.drawable.ic_expand_up_white_24dp)
          fragment.resources.getDimension(R.dimen.fab_margin_44row)
        }

    if (startButton != null) {
      val params = startButton.layoutParams as ViewGroup.MarginLayoutParams
      params.setMargins(params.leftMargin, params.topMargin, params.rightMargin, bottomMargin.toInt())
      startButton.layoutParams = params
    }
    fragment.updateView()
  }

  fun formatGpsAccuracy(accuracy: Float): String =
      StartGpsAccuracyFormatter.format(
          fragment.requireContext(),
          fragment.formatter,
          fragment.mTracker,
          accuracy,
      )

  fun buildHrDetailString(): String {
    val str = StringBuilder()
    val prefs =
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(fragment.requireContext())
    val btDeviceName = prefs.getString(fragment.getString(R.string.pref_bt_name), null)

    if (btDeviceName != null) {
      str.append(btDeviceName)
    } else if (org.runnerup.hr.MockHRProvider.NAME ==
        prefs.getString(fragment.getString(R.string.pref_bt_provider), "")) {
      str.append("mock: ").append(prefs.getString(fragment.getString(R.string.pref_bt_address), "???"))
    }

    val tracker = fragment.mTracker ?: return str.toString()
    if (tracker.isComponentConnected(org.runnerup.tracking.component.TrackerHRM.NAME)) {
      val hrVal = tracker.currentHRValue
      if (hrVal != null) {
        str.append(" ").append(hrVal)
        val batteryLevel = tracker.currentBatteryLevel
        if (batteryLevel != org.runnerup.hr.HRProvider.BATTERY_LEVEL_UNAVAILABLE) {
          str.append(" ").append(batteryLevel).append("%")
        }
      }
    }
    return str.toString()
  }
}
