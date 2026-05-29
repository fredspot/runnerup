/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import org.runnerup.core.workout.Workout

/** Workout tab selection and [Workout] preparation for [StartFragment]. */
internal class StartWorkoutPickerController(private val fragment: StartFragment) {

  fun prepareWorkout(): Workout? = fragment.startLaunchController.prepareWorkout()

  fun startWorkout() {
    fragment.startLaunchController.startWorkout()
  }
}
