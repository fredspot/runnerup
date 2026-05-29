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

object WorkoutEditorRowOps {

  @JvmStatic
  fun canDrop(from: WorkoutEditorRow, to: WorkoutEditorRow): Boolean {
    if (!from.isDraggable) return false
    return when (to) {
      is WorkoutEditorRow.TopStep,
      is WorkoutEditorRow.RepeatHeader,
      is WorkoutEditorRow.RepeatChild,
      -> from.isDraggable
    }
  }

  /**
   * Moves data and returns a new row list reflecting the change. Caller should assign to adapter
   * rows and call notifyItemMoved(from, to).
   */
  @JvmStatic
  fun move(workout: Workout, rows: MutableList<WorkoutEditorRow>, from: Int, to: Int): Boolean {
    val fromRow = rows.getOrNull(from) ?: return false
    val toRow = rows.getOrNull(to) ?: return false
    if (!canDrop(fromRow, toRow)) return false

    val moved =
        when {
          fromRow is WorkoutEditorRow.RepeatChild && toRow is WorkoutEditorRow.RepeatChild ->
              moveRepeatChildWithin(fromRow, toRow)
          fromRow is WorkoutEditorRow.RepeatChild &&
              (toRow is WorkoutEditorRow.TopStep || toRow is WorkoutEditorRow.RepeatHeader) ->
              extractToWorkout(workout, fromRow, toRow)
          fromRow is WorkoutEditorRow.TopStep && toRow is WorkoutEditorRow.RepeatChild ->
              insertFromWorkoutBeforeChild(workout, fromRow, toRow)
          fromRow is WorkoutEditorRow.TopStep && toRow is WorkoutEditorRow.RepeatHeader ->
              insertFromWorkoutIntoRepeat(workout, fromRow, toRow.repeat, append = true)
          fromRow is WorkoutEditorRow.RepeatChild && toRow is WorkoutEditorRow.RepeatHeader ->
              moveRepeatChildToRepeat(fromRow, toRow.repeat)
          fromRow.topLevelStep() != null && toRow.topLevelStep() != null ->
              moveTopLevel(workout, fromRow.topLevelStep()!!, toRow.topLevelStep()!!)
          else -> false
        }

    if (!moved) return false
    rows.clear()
    rows.addAll(WorkoutEditorRow.build(workout))
    return true
  }

  private fun moveTopLevel(workout: Workout, fromStep: Step, toStep: Step): Boolean {
    val list = workout.steps
    val fromIdx = list.indexOf(fromStep)
    val toIdx = list.indexOf(toStep)
    return WorkoutEditorHelper.moveInList(list, fromIdx, toIdx)
  }

  private fun moveRepeatChildWithin(from: WorkoutEditorRow.RepeatChild, to: WorkoutEditorRow.RepeatChild): Boolean {
    if (from.repeat !== to.repeat) return false
    val list = from.repeat.steps
    val fromIdx = list.indexOf(from.step)
    val toIdx = list.indexOf(to.step)
    return WorkoutEditorHelper.moveInList(list, fromIdx, toIdx)
  }

  private fun extractToWorkout(
      workout: Workout,
      from: WorkoutEditorRow.RepeatChild,
      to: WorkoutEditorRow,
  ): Boolean {
    val topTarget =
        when (to) {
          is WorkoutEditorRow.TopStep -> to.step
          is WorkoutEditorRow.RepeatHeader -> to.repeat
          else -> return false
        }
    if (!from.repeat.steps.remove(from.step)) return false
    val insertIdx = workout.steps.indexOf(topTarget)
    if (insertIdx < 0) return false
    workout.steps.add(insertIdx, from.step)
    return true
  }

  private fun insertFromWorkoutBeforeChild(
      workout: Workout,
      from: WorkoutEditorRow.TopStep,
      to: WorkoutEditorRow.RepeatChild,
  ): Boolean {
    if (!workout.steps.remove(from.step)) return false
    val insertIdx = to.repeat.steps.indexOf(to.step)
    if (insertIdx < 0) {
      to.repeat.steps.add(from.step)
    } else {
      to.repeat.steps.add(insertIdx, from.step)
    }
    return true
  }

  private fun insertFromWorkoutIntoRepeat(
      workout: Workout,
      from: WorkoutEditorRow.TopStep,
      repeat: RepeatStep,
      append: Boolean,
  ): Boolean {
    if (!workout.steps.remove(from.step)) return false
    if (append) {
      repeat.steps.add(from.step)
    } else {
      repeat.steps.add(0, from.step)
    }
    return true
  }

  private fun moveRepeatChildToRepeat(
      from: WorkoutEditorRow.RepeatChild,
      target: RepeatStep,
  ): Boolean {
    if (from.repeat === target) {
      if (!from.repeat.steps.remove(from.step)) return false
      from.repeat.steps.add(from.step)
      return true
    }
    if (!from.repeat.steps.remove(from.step)) return false
    target.steps.add(from.step)
    return true
  }

  /**
   * Stable per [Step] / [RepeatStep] instance so moving a step into or out of a repeat block keeps
   * the same RecyclerView item id (avoids recycle glitches on first insert into a repeat).
   */
  @JvmStatic
  fun stableId(row: WorkoutEditorRow): Long =
      when (row) {
        is WorkoutEditorRow.TopStep -> stepId(row.step)
        is WorkoutEditorRow.RepeatHeader -> System.identityHashCode(row.repeat).toLong()
        is WorkoutEditorRow.RepeatChild -> stepId(row.step)
      }

  private fun stepId(step: Step): Long = System.identityHashCode(step).toLong()
}
