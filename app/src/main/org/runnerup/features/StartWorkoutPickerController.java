package org.runnerup.features;

import org.runnerup.core.workout.Workout;

/** Workout tab selection and {@link Workout} preparation for {@link StartFragment}. */
final class StartWorkoutPickerController {

  private final StartFragment fragment;

  StartWorkoutPickerController(StartFragment fragment) {
    this.fragment = fragment;
  }

  Workout prepareWorkout() {
    return fragment.performPrepareWorkout();
  }

  void startWorkout() {
    fragment.performStartWorkout();
  }
}
