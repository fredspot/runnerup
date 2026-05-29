/*
 * Copyright (C) 2024 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import org.runnerup.core.workout.RepeatStep
import org.runnerup.core.workout.Step
import org.runnerup.core.workout.Workout

/** Flat rows for a single RecyclerView (reliable drag-and-drop). */
sealed class WorkoutEditorRow {

  abstract val isDraggable: Boolean

  data class TopStep(val step: Step) : WorkoutEditorRow() {
    override val isDraggable: Boolean = true
  }

  data class RepeatHeader(val repeat: RepeatStep) : WorkoutEditorRow() {
    override val isDraggable: Boolean = true
  }

  data class RepeatChild(val step: Step, val repeat: RepeatStep) : WorkoutEditorRow() {
    override val isDraggable: Boolean = true
  }

  fun topLevelStep(): Step? =
      when (this) {
        is TopStep -> step
        is RepeatHeader -> repeat
        else -> null
      }

  companion object {
    @JvmStatic
    fun build(workout: Workout): List<WorkoutEditorRow> {
      val rows = ArrayList<WorkoutEditorRow>()
      for (s in workout.steps) {
        if (s is RepeatStep) {
          rows.add(RepeatHeader(s))
          for (child in s.steps) {
            rows.add(RepeatChild(child, s))
          }
        } else {
          rows.add(TopStep(s))
        }
      }
      return rows
    }
  }
}
