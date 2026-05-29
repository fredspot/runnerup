/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import org.runnerup.R
import org.runnerup.core.workout.Workout

/** Prepares and launches a workout from [StartFragment] (not tracker lifecycle). */
internal class StartLaunchController(private val fragment: StartFragment) {

  fun currentWorkoutTabTag(): String {
    val index = fragment.startPager?.currentItem ?: 0
    return StartWorkoutPrep.tabTagForPageIndex(index)
  }

  fun prepareWorkout(): Workout? {
    val ctx = fragment.requireActivity().applicationContext
    val pref = PreferenceManager.getDefaultSharedPreferences(ctx)
    val targetType = fragment.simpleTargetType ?: return null
    return StartWorkoutPrep.prepareWorkout(
        ctx,
        fragment.resources,
        pref,
        currentWorkoutTabTag(),
        targetType.valueInt,
        fragment.advancedController.advancedWorkout,
        fragment.advancedController.getSelectedWorkoutName(),
        fragment.getString(R.string.pref_basic_audio),
        fragment.getString(R.string.pref_interval_audio),
        fragment.getString(R.string.pref_advanced_audio),
    )
  }

  fun startWorkout() {
    val gpsStatus = fragment.mGpsStatus ?: return
    val tracker = fragment.mTracker ?: return
    gpsStatus.stop(fragment)
    fragment.trackerLifecycle.unregisterStartEventListener()
    tracker.setWorkout(prepareWorkout())
    tracker.start()
    fragment.runActivityPending = true
    val intent = Intent(fragment.requireContext(), RunActivity::class.java)
    fragment.runActivityLauncher.launch(intent)
    fragment.notificationStateManager.cancelNotification()
  }
}
