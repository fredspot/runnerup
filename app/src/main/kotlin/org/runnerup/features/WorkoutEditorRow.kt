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

  data class RepeatAdd(val repeat: RepeatStep) : WorkoutEditorRow() {
    override val isDraggable: Boolean = false
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
          rows.add(RepeatAdd(s))
        } else {
          rows.add(TopStep(s))
        }
      }
      return rows
    }

    @JvmStatic
    fun canSwap(a: WorkoutEditorRow, b: WorkoutEditorRow): Boolean {
      if (!a.isDraggable || !b.isDraggable) {
        return false
      }
      if (a is RepeatChild && b is RepeatChild) {
        return a.repeat === b.repeat
      }
      if (a.topLevelStep() != null && b.topLevelStep() != null) {
        return true
      }
      return false
    }

    @JvmStatic
    fun move(workout: Workout, rows: List<WorkoutEditorRow>, from: Int, to: Int): Boolean {
      val fromRow = rows.getOrNull(from) ?: return false
      val toRow = rows.getOrNull(to) ?: return false
      if (!canSwap(fromRow, toRow)) {
        return false
      }
      if (fromRow is RepeatChild && toRow is RepeatChild) {
        val list = fromRow.repeat.steps
        val fromIdx = list.indexOf(fromRow.step)
        val toIdx = list.indexOf(toRow.step)
        return WorkoutEditorHelper.moveInList(list, fromIdx, toIdx)
      }
      val fromStep = fromRow.topLevelStep() ?: return false
      val toStep = toRow.topLevelStep() ?: return false
      val list = workout.steps
      val fromIdx = list.indexOf(fromStep)
      val toIdx = list.indexOf(toStep)
      return WorkoutEditorHelper.moveInList(list, fromIdx, toIdx)
    }
  }
}
