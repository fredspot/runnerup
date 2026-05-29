/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.runnerup.R
import org.runnerup.common.tracker.TrackerState
import org.runnerup.common.util.Constants
import org.runnerup.common.util.ValueModel
import org.runnerup.tracking.Tracker

/** Tracker service bind, broadcast start intents, and GPS-required mode for [StartFragment]. */
internal class StartTrackerLifecycle(private val fragment: StartFragment) {

  private var trackerBinding: StartTrackerBinding? = null

  private val trackerStateListener =
      ValueModel.ChangeListener<TrackerState> { _, _, _ -> fragment.onTick() }

  private val startEventBroadcastReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          fragment.requireActivity().runOnUiThread {
            val tracker = fragment.mTracker ?: return@runOnUiThread
            val startButton = fragment.startButton ?: return@runOnUiThread
            if (startButton.visibility != View.VISIBLE) {
              return@runOnUiThread
            }
            when (tracker.state) {
              TrackerState.INIT,
              TrackerState.INITIALIZED,
              TrackerState.CONNECTED -> startButton.performClick()
              else -> {}
            }
          }
        }
      }

  fun ensureTrackerBound() {
    val binding = trackerBinding
    if (binding != null && binding.isBound() && fragment.mTracker != null) {
      onGpsTrackerBound()
      return
    }
    if (trackerBinding == null) {
      trackerBinding =
          StartTrackerBinding(
              fragment.requireContext(),
              object : StartTrackerBinding.Callback {
                override fun onTrackerBound(tracker: Tracker) {
                  fragment.mTracker = tracker
                  onGpsTrackerBound()
                  tracker.registerTrackerStateListener(trackerStateListener)
                }

                override fun onTrackerUnbound() {
                  fragment.mTracker?.unregisterTrackerStateListener(trackerStateListener)
                  fragment.mTracker = null
                }
              },
          )
    }
    trackerBinding?.bind()
  }

  fun unbindTracker() {
    trackerBinding?.unbind()
    trackerBinding = null
  }

  fun isTrackerBound(): Boolean = trackerBinding?.isBound() == true

  fun registerStartEventListener() {
    val filter =
        IntentFilter().apply {
          addAction(Constants.Intents.START_ACTIVITY)
          addAction(Constants.Intents.START_WORKOUT)
        }
    ContextCompat.registerReceiver(
        fragment.requireActivity(),
        startEventBroadcastReceiver,
        filter,
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
  }

  fun unregisterStartEventListener() {
    try {
      fragment.requireActivity().unregisterReceiver(startEventBroadcastReceiver)
    } catch (_: Exception) {
    }
  }

  fun setGpsNotRequired(withoutGps: Boolean) {
    if (fragment.sportWithoutGps == withoutGps) {
      return
    }
    fragment.sportWithoutGps = withoutGps
    val tracker = fragment.mTracker ?: return
    if (withoutGps) {
      tracker.setWithoutGps(true)
    } else {
      Log.e(javaClass.name, "mTracker.reset()")
      tracker.setWithoutGps(false)
      tracker.reset()
      fragment.mGpsStatus?.let { gps ->
        if (gps.isStarted) {
          tracker.setup()
          fragment.gpsController.startGps()
        }
      }
    }
  }

  fun getAutoStartGps(): Boolean {
    val ctx = fragment.requireActivity().applicationContext
    val pref = PreferenceManager.getDefaultSharedPreferences(ctx)
    return pref.getBoolean(fragment.getString(R.string.pref_startgps), false)
  }

  fun stopGps() {
    fragment.gpsController.stopGps()
  }

  fun isGpsLogging(): Boolean = fragment.mGpsStatus?.isLogging == true

  fun onPause() {
    if (getAutoStartGps()) {
      stopGps()
    } else {
      val tracker = fragment.mTracker
      if (tracker != null &&
          (tracker.state == TrackerState.INITIALIZED ||
              tracker.state == TrackerState.INITIALIZING)) {
        Log.e(javaClass.name, "mTracker.reset()")
        tracker.reset()
      }
    }
  }

  private fun onGpsTrackerBound() {
    val tracker = fragment.mTracker ?: return
    val missingPermission = fragment.permissionsController.checkPermissions(false)
    tracker.setWithoutGps(fragment.sportWithoutGps)
    if (!missingPermission && getAutoStartGps()) {
      fragment.gpsController.startGps()
    } else {
      Log.e(javaClass.name, "onGpsTrackerBound state: ${tracker.state}")
      when (tracker.state) {
        TrackerState.INIT,
        TrackerState.CLEANUP -> tracker.setup()
        else -> {}
      }
    }
    fragment.updateView()
  }
}
