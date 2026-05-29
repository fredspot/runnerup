/*
 * Copyright (C) 2024 RunnerUp
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

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import org.runnerup.core.workout.Dimension
import org.runnerup.core.workout.Workout
import org.runnerup.core.workout.WorkoutBuilder

/** Builds the workout to start from the Run tab mode (Basic / Interval / Advanced). */
object StartWorkoutPrep {

  const val TAB_BASIC = "basic"
  const val TAB_INTERVAL = "interval"
  const val TAB_ADVANCED = "advanced"

  @JvmStatic
  fun tabTagForPageIndex(pageIndex: Int): String =
      when (pageIndex) {
        1 -> TAB_INTERVAL
        2 -> TAB_ADVANCED
        else -> TAB_BASIC
      }

  @JvmStatic
  fun prepareWorkout(
      context: Context,
      resources: Resources,
      pref: SharedPreferences,
      tabTag: String,
      basicTargetTypeValue: Int,
      advancedWorkout: Workout?,
      advancedWorkoutName: String?,
      prefKeyBasicAudio: String,
      prefKeyIntervalAudio: String,
      prefKeyAdvancedAudio: String,
  ): Workout? {
    val ctx = context.applicationContext
    var workout: Workout? = null
    var audioPref: SharedPreferences? = null

    when (tabTag) {
      TAB_BASIC -> {
        audioPref = WorkoutBuilder.getAudioCuePreferences(ctx, pref, prefKeyBasicAudio)
        val target = Dimension.valueOf(basicTargetTypeValue)
        workout = WorkoutBuilder.createDefaultWorkout(resources, pref, target)
      }
      TAB_INTERVAL -> {
        audioPref = WorkoutBuilder.getAudioCuePreferences(ctx, pref, prefKeyIntervalAudio)
        workout = WorkoutBuilder.createDefaultIntervalWorkout(resources, pref)
      }
      TAB_ADVANCED -> {
        audioPref = WorkoutBuilder.getAudioCuePreferences(ctx, pref, prefKeyAdvancedAudio)
        workout = advancedWorkout
      }
    }

    if (workout != null && audioPref != null) {
      WorkoutBuilder.prepareWorkout(resources, pref, workout)
      WorkoutBuilder.addAudioCuesToWorkout(ctx, resources, workout, audioPref, pref)
    }
    return workout
  }
}
