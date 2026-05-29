/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import org.runnerup.R
import org.runnerup.common.tracker.TrackerState
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.SafeParse
import org.runnerup.tracking.component.TrackerHRM
import org.runnerup.tracking.component.TrackerWear
import org.runnerup.ui.common.widget.ClassicSpinner

/** Start/GPS button visibility, wear/HR rows, and tab pager wiring for [StartFragment]. */
internal class StartRunUiController(private val fragment: StartFragment) {

  fun setupStartTabs(root: View) {
    fragment.startPager = root.findViewById(R.id.start_pager)
    val layouts =
        intArrayOf(
            R.layout.start_basic,
            R.layout.start_interval,
            R.layout.start_advanced,
        )
    fragment.startPager?.apply {
      adapter = StartTabAdapter(layouts)
      offscreenPageLimit = layouts.size
      isUserInputEnabled = false
      registerOnPageChangeCallback(
          object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
              if (position == 2) {
                fragment.advancedController.loadAdvanced(null)
              }
              fragment.view?.findViewById<ClassicSpinner>(R.id.workout_mode_spinner)
                  ?.setViewSelection(position)
              fragment.updateView()
            }
          },
      )
    }
  }

  fun setupModeSpinner(root: View) {
    val context = fragment.requireContext()
    val modeSpinner = root.findViewById<ClassicSpinner>(R.id.workout_mode_spinner)
    val modeArray =
        arrayOf(
            fragment.getString(org.runnerup.common.R.string.Basic),
            fragment.getString(org.runnerup.common.R.string.Interval),
            fragment.getString(org.runnerup.common.R.string.Advanced),
        )
    val modeAdapter = ArrayAdapter(context, R.layout.actionbar_spinner, modeArray)
    modeAdapter.setDropDownViewResource(R.layout.actionbar_dropdown_spinner)
    modeSpinner.setAdapter(modeAdapter)
    modeSpinner.setViewSelection(0)
    modeSpinner.setViewOnItemSelectedListener(
        object : AdapterView.OnItemSelectedListener {
          override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            fragment.startPager?.setCurrentItem(position, false)
          }

          override fun onNothingSelected(parent: AdapterView<*>?) {}
        },
    )
  }

  fun wireClickListeners() {
    fragment.startButton?.setOnClickListener {
      val tracker = fragment.mTracker ?: return@setOnClickListener
      if (tracker.state == TrackerState.CONNECTED) {
        fragment.workoutController.startWorkout()
      } else {
        if (fragment.permissionsController.checkPermissions(true)) {
          return@setOnClickListener
        }
        fragment.gpsController.startGps()
      }
      fragment.updateView()
    }
    fragment.gpsEnable?.setOnClickListener {
      if (fragment.permissionsController.checkPermissions(true)) {
        return@setOnClickListener
      }
      val tracker = fragment.mTracker
      if (tracker == null || tracker.state != TrackerState.CONNECTED) {
        fragment.gpsController.startGps()
      }
      fragment.updateView()
    }
  }

  fun updateNewStartButton() {
    val startButton = fragment.startButton ?: return
    val gpsStatus = fragment.mGpsStatus
    val presentation =
        StartButtonPresentation.resolve(
            gpsStarted = gpsStatus?.isStarted == true,
            gpsFixed = gpsStatus?.isFixed == true,
            trackerConnected = fragment.mTracker?.state == TrackerState.CONNECTED,
        )
    startButton.text = presentation.label
    startButton.setBackgroundResource(presentation.backgroundResId)
    startButton.isEnabled = presentation.enabled
    startButton.visibility = View.VISIBLE
  }

  fun updateHrView(): Boolean {
    val tracker = fragment.mTracker
    if (tracker == null || !tracker.isComponentConfigured(TrackerHRM.NAME)) {
      fragment.hrIndicator?.visibility = View.GONE
      fragment.hrMessage?.visibility = View.GONE
      return false
    }
    if (tracker.isComponentConnected(TrackerHRM.NAME)) {
      if (!fragment.batteryLevelMessageShown) {
        fragment.batteryLevelMessageShown = true
        notificationBatteryLevel(tracker.currentBatteryLevel)
      }
    }
    fragment.hrMessage?.text = fragment.statusController.buildHrDetailString()
    fragment.hrIndicator?.visibility = View.VISIBLE
    fragment.hrMessage?.visibility =
        if (fragment.statusDetailsShown) View.VISIBLE else View.GONE
    return true
  }

  fun updateWearOsView(): Boolean {
    val tracker = fragment.mTracker
    if (tracker == null || !tracker.isComponentConfigured(TrackerWear.NAME)) {
      fragment.wearOsIndicator?.visibility = View.GONE
      fragment.wearOsMessage?.visibility = View.GONE
      return false
    }
    fragment.wearOsIndicator?.visibility = View.VISIBLE
    if (!tracker.isComponentConnected(TrackerWear.NAME)) {
      fragment.wearOsMessage?.visibility = View.VISIBLE
      fragment.wearOsMessage?.text = "?"
    } else {
      fragment.wearOsMessage?.visibility = View.GONE
    }
    return true
  }

  fun updateNoDevicesConnected(show: Boolean) {
    fragment.noDevicesConnected?.visibility = if (show) View.VISIBLE else View.GONE
  }

  fun updateHrIndicator() {
    val hrIndicator = fragment.view?.findViewById<ImageView>(R.id.new_hr_indicator) ?: return
    val tracker = fragment.mTracker
    if (tracker != null && tracker.isComponentConnected(TrackerHRM.NAME)) {
      hrIndicator.setColorFilter(fragment.resources.getColor(android.R.color.white, null))
      hrIndicator.alpha = 1.0f
    } else {
      hrIndicator.setColorFilter(0xFF808080.toInt())
      hrIndicator.alpha = 0.2f
    }
  }

  fun notificationBatteryLevel(batteryLevel: Int) {
    if (batteryLevel !in 0..100) {
      return
    }
    val context = fragment.requireContext()
    val prefKey = fragment.getString(R.string.pref_battery_level_low_notification_discard)
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val highThreshold =
        SafeParse.parseInt(
            prefs.getString(fragment.getString(R.string.pref_battery_level_high_threshold), "75"),
            75,
        )
    if (batteryLevel > highThreshold && prefs.contains(prefKey)) {
      prefs.edit().remove(prefKey).apply()
      return
    }
    val lowThreshold =
        SafeParse.parseInt(
            prefs.getString(fragment.getString(R.string.pref_battery_level_low_threshold), "15"),
            15,
        )
    if (batteryLevel > lowThreshold || prefs.getBoolean(prefKey, false)) {
      return
    }
    val dontShowAgain = CheckBox(context)
    dontShowAgain.setText(org.runnerup.common.R.string.Do_not_show_again)
    AlertDialog.Builder(context)
        .setView(dontShowAgain)
        .setCancelable(false)
        .setTitle(org.runnerup.common.R.string.Warning)
        .setMessage(
            fragment.resources.getText(org.runnerup.common.R.string.Low_HRM_battery_level)
                .toString() +
                "\n" +
                fragment.resources.getText(org.runnerup.common.R.string.Battery_level) +
                ": $batteryLevel%",
        )
        .setPositiveButton(org.runnerup.common.R.string.OK) { _, _ ->
          if (dontShowAgain.isChecked) {
            prefs.edit().putBoolean(prefKey, true).apply()
          }
        }
        .show()
  }
}
