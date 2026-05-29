package org.runnerup.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.core.workout.RepeatStep
import org.runnerup.core.workout.Step
import org.runnerup.core.workout.Workout

class WorkoutEditorRowOpsTest {

  @Test
  fun moveTopLevelSteps() {
    val workout = Workout()
    val a = Step()
    val b = Step()
    workout.steps.add(a)
    workout.steps.add(b)
    val rows = WorkoutEditorRow.build(workout).toMutableList()
    assertTrue(WorkoutEditorRowOps.move(workout, rows, 0, 1))
    assertEquals(b, workout.steps[0])
    assertEquals(a, workout.steps[1])
  }

  @Test
  fun extractChildToWorkout() {
    val workout = Workout()
    val repeat = RepeatStep()
    val child = Step()
    repeat.steps.add(child)
    workout.steps.add(repeat)
    val rows = WorkoutEditorRow.build(workout).toMutableList()
    val childPos = rows.indexOfFirst { it is WorkoutEditorRow.RepeatChild }
    val headerPos = rows.indexOfFirst { it is WorkoutEditorRow.RepeatHeader }
    assertTrue(WorkoutEditorRowOps.move(workout, rows, childPos, headerPos))
    assertEquals(0, repeat.steps.size)
    assertEquals(2, workout.steps.size)
    assertEquals(child, workout.steps[0])
    assertEquals(repeat, workout.steps[1])
  }

  @Test
  fun stableIdUnchangedWhenStepMovesIntoRepeat() {
    val workout = Workout()
    val top = Step()
    val repeat = RepeatStep()
    workout.steps.add(top)
    workout.steps.add(repeat)
    val before = WorkoutEditorRow.build(workout)
    val topRow = before[0] as WorkoutEditorRow.TopStep
    val topId = WorkoutEditorRowOps.stableId(topRow)

    WorkoutEditorRowOps.move(
        workout,
        before.toMutableList(),
        0,
        before.indexOfFirst { it is WorkoutEditorRow.RepeatHeader },
    )

    val after = WorkoutEditorRow.build(workout)
    val childRow = after[1] as WorkoutEditorRow.RepeatChild
    assertEquals(topId, WorkoutEditorRowOps.stableId(childRow))
  }

  @Test
  fun insertTopStepIntoRepeat() {
    val workout = Workout()
    val top = Step()
    val repeat = RepeatStep()
    workout.steps.add(top)
    workout.steps.add(repeat)
    val rows = WorkoutEditorRow.build(workout).toMutableList()
    val headerPos = rows.indexOfFirst { it is WorkoutEditorRow.RepeatHeader }
    assertTrue(WorkoutEditorRowOps.move(workout, rows, 0, headerPos))
    assertEquals(1, workout.steps.size)
    assertEquals(top, repeat.steps[0])
  }
}
