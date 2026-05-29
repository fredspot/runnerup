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
      prefKeyBasicAudio: String,
      prefKeyIntervalAudio: String,
      prefKeyAdvancedAudio: String,
  ): Workout? {
    val ctx = context.applicationContext
    val (workout, audioPref) =
        when (tabTag) {
          TAB_BASIC -> {
            val audio =
                WorkoutBuilder.getAudioCuePreferences(ctx, pref, prefKeyBasicAudio)
            val target = Dimension.valueOf(basicTargetTypeValue)
            Pair(WorkoutBuilder.createDefaultWorkout(resources, pref, target), audio)
          }
          TAB_INTERVAL -> {
            val audio =
                WorkoutBuilder.getAudioCuePreferences(ctx, pref, prefKeyIntervalAudio)
            Pair(WorkoutBuilder.createDefaultIntervalWorkout(resources, pref), audio)
          }
          TAB_ADVANCED -> {
            val audio =
                WorkoutBuilder.getAudioCuePreferences(ctx, pref, prefKeyAdvancedAudio)
            Pair(advancedWorkout, audio)
          }
          else -> Pair(null, null)
        }
    if (workout != null && audioPref != null) {
      WorkoutBuilder.prepareWorkout(resources, pref, workout)
      WorkoutBuilder.addAudioCuesToWorkout(resources, workout, audioPref, pref)
    }
    return workout
  }
}
