/*
 * Copyright (C) 2024 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import org.runnerup.core.workout.Range
import org.runnerup.core.workout.RepeatStep
import org.runnerup.core.workout.Step
import org.runnerup.core.workout.Workout

object WorkoutEditorHelper {

  @JvmStatic
  fun parentList(workout: Workout, step: Step): MutableList<Step>? {
    if (workout.steps.contains(step)) {
      return workout.steps
    }
    for (top in workout.steps) {
      if (top is RepeatStep && top.steps.contains(step)) {
        return top.steps
      }
    }
    return null
  }

  @JvmStatic
  fun moveInList(list: MutableList<Step>, fromIndex: Int, toIndex: Int): Boolean {
    if (fromIndex < 0 || toIndex < 0 || fromIndex >= list.size || toIndex >= list.size) {
      return false
    }
    if (fromIndex == toIndex) {
      return false
    }
    val step = list.removeAt(fromIndex)
    list.add(toIndex, step)
    return true
  }

  @JvmStatic
  fun deleteStep(workout: Workout, step: Step): Boolean {
    val list = parentList(workout, step) ?: return false
    return list.remove(step)
  }

  @JvmStatic
  fun duplicateStep(workout: Workout, step: Step): Step? {
    val list = parentList(workout, step) ?: return null
    val copy = copyStep(step)
    val idx = list.indexOf(step)
    if (idx < 0) {
      return null
    }
    list.add(idx + 1, copy)
    return copy
  }

  @JvmStatic
  fun addStepInsideRepeat(repeat: RepeatStep) {
    repeat.steps.add(Step())
  }

  @JvmStatic
  fun copyStep(source: Step): Step {
    if (source is RepeatStep) {
      val copy = RepeatStep()
      copy.repeatCount = source.repeatCount
      for (inner in source.steps) {
        copy.steps.add(copyStep(inner))
      }
      return copy
    }
    val copy = Step()
    copy.intensity = source.intensity
    copy.durationType = source.durationType
    copy.durationValue = source.durationValue
    copy.targetType = source.targetType
    if (source.targetValue != null) {
      copy.setTargetValue(source.targetValue.minValue, source.targetValue.maxValue)
    }
    copy.setHrCueIntervalSeconds(source.hrCueIntervalSeconds)
    copy.setPaceCueIntervalSeconds(source.paceCueIntervalSeconds)
    copy.setHrCueAnnouncement(source.hrCueAnnouncement)
    copy.setAudioCueScheme(source.audioCueScheme)
    source.name?.let { copy.setName(it) }
    return copy
  }
}
