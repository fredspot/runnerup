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
    return StartWorkoutPrep.prepareWorkout(
        ctx,
        fragment.resources,
        pref,
        currentWorkoutTabTag(),
        fragment.simpleTargetType.valueInt,
        fragment.advancedController.advancedWorkout,
        fragment.advancedController.getSelectedWorkoutName(),
        fragment.getString(R.string.pref_basic_audio),
        fragment.getString(R.string.pref_interval_audio),
        fragment.getString(R.string.pref_advanced_audio),
    )
  }

  fun startWorkout() {
    fragment.mGpsStatus.stop(fragment)
    fragment.unregisterStartEventListener()
    fragment.mTracker.setWorkout(prepareWorkout())
    fragment.mTracker.start()
    fragment.runActivityPending = true
    val intent = Intent(fragment.requireContext(), RunActivity::class.java)
    fragment.runActivityLauncher.launch(intent)
    fragment.notificationStateManager.cancelNotification()
  }
}
