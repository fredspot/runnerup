/*
 * Copyright (C) 2024 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.runnerup.R
import org.runnerup.core.util.CardPressHelper
import org.runnerup.core.workout.RepeatStep
import org.runnerup.core.workout.Step
import org.runnerup.core.workout.Workout

class WorkoutEditorStepsAdapter(
    private val activity: AppCompatActivity,
    private val onWorkoutChanged: Runnable,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  private var workout: Workout? = null
  private var rows: List<WorkoutEditorRow> = emptyList()
  private val touchHelper = ItemTouchHelper(DragCallback())

  fun attachToRecyclerView(recyclerView: RecyclerView) {
    CardPressHelper.prepareRowHost(recyclerView)
    touchHelper.attachToRecyclerView(recyclerView)
  }

  fun setWorkout(w: Workout) {
    workout = w
    rows = WorkoutEditorRow.build(w)
    notifyDataSetChanged()
  }

  private fun refreshRows() {
    workout?.let { rows = WorkoutEditorRow.build(it) }
  }

  override fun getItemCount(): Int = rows.size

  override fun getItemViewType(position: Int): Int =
      when (rows[position]) {
        is WorkoutEditorRow.TopStep -> VIEW_STEP
        is WorkoutEditorRow.RepeatHeader -> VIEW_STEP
        is WorkoutEditorRow.RepeatChild -> VIEW_STEP
        is WorkoutEditorRow.RepeatAdd -> VIEW_ADD
      }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    val inflater = LayoutInflater.from(activity)
    return if (viewType == VIEW_ADD) {
      AddHolder(inflater.inflate(R.layout.workout_editor_repeat_add_row, parent, false))
    } else {
      StepHolder(inflater.inflate(R.layout.workout_editor_step_row, parent, false))
    }
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    when (val row = rows[position]) {
      is WorkoutEditorRow.TopStep -> (holder as StepHolder).bind(row.step, indentDp = 0, showLabel = false)
      is WorkoutEditorRow.RepeatHeader ->
          (holder as StepHolder).bind(row.repeat, indentDp = 0, showLabel = true)
      is WorkoutEditorRow.RepeatChild ->
          (holder as StepHolder).bind(row.step, indentDp = 44, showLabel = false)
      is WorkoutEditorRow.RepeatAdd -> (holder as AddHolder).bind(row.repeat)
    }
  }

  private inner class StepHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val host: View = itemView.findViewById(R.id.workout_editor_row_host)
    private val label: TextView = itemView.findViewById(R.id.workout_editor_row_label)
    private val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)
    val stepButton: StepButton = itemView.findViewById(R.id.workout_step_button)

    fun bind(step: Step, indentDp: Int, showLabel: Boolean) {
      val density = activity.resources.displayMetrics.density
      val marginPx = (indentDp * density + 0.5f).toInt()
      (host.layoutParams as ViewGroup.MarginLayoutParams).marginStart = marginPx

      label.visibility = if (showLabel) View.VISIBLE else View.GONE

      stepButton.setEditorCardStyle(true)
      stepButton.setStep(step)
      stepButton.setOnChangedListener(onWorkoutChanged)

      WorkoutEditorDrag.bindHandle(dragHandle, touchHelper, this)

      val openActions = View.OnLongClickListener {
        showStepActions(step)
        true
      }
      itemView.setOnLongClickListener(openActions)
      stepButton.setOnLongClickListener(openActions)
    }
  }

  private inner class AddHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val button: Button = itemView.findViewById(R.id.add_inside_repeat_button)

    fun bind(repeat: RepeatStep) {
      button.setOnClickListener {
        WorkoutEditorHelper.addStepInsideRepeat(repeat)
        refreshRows()
        notifyDataSetChanged()
        onWorkoutChanged.run()
      }
    }
  }

  private fun showStepActions(step: Step) {
    val w = workout ?: return
    AlertDialog.Builder(activity)
        .setTitle(R.string.Workout_step_actions)
        .setItems(
            arrayOf(
                activity.getString(R.string.Duplicate_step),
                activity.getString(org.runnerup.common.R.string.Delete),
            ),
        ) { _, which ->
          when (which) {
            0 -> {
              WorkoutEditorHelper.duplicateStep(w, step)
              refreshRows()
              notifyDataSetChanged()
              onWorkoutChanged.run()
            }
            1 -> confirmDelete(step)
          }
        }
        .show()
  }

  private fun confirmDelete(step: Step) {
    val w = workout ?: return
    AlertDialog.Builder(activity)
        .setTitle(org.runnerup.common.R.string.Are_you_sure)
        .setPositiveButton(org.runnerup.common.R.string.Yes) { dialog, _ ->
          dialog.dismiss()
          WorkoutEditorHelper.deleteStep(w, step)
          refreshRows()
          notifyDataSetChanged()
          onWorkoutChanged.run()
        }
        .setNegativeButton(org.runnerup.common.R.string.No) { dialog, _ -> dialog.dismiss() }
        .show()
  }

  private inner class DragCallback : ItemTouchHelper.SimpleCallback(
      ItemTouchHelper.UP or ItemTouchHelper.DOWN,
      0,
  ) {
    override fun isLongPressDragEnabled(): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun canDropOver(
        recyclerView: RecyclerView,
        current: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
      val from = current.bindingAdapterPosition
      val to = target.bindingAdapterPosition
      if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
        return false
      }
      val fromRow = rows.getOrNull(from) ?: return false
      val toRow = rows.getOrNull(to) ?: return false
      return WorkoutEditorRow.canSwap(fromRow, toRow)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
      val w = workout ?: return false
      val from = viewHolder.bindingAdapterPosition
      val to = target.bindingAdapterPosition
      if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
        return false
      }
      if (!WorkoutEditorRow.move(w, rows, from, to)) {
        return false
      }
      refreshRows()
      notifyDataSetChanged()
      onWorkoutChanged.run()
      return true
    }
  }

  companion object {
    private const val VIEW_STEP = 0
    private const val VIEW_ADD = 1
  }
}
