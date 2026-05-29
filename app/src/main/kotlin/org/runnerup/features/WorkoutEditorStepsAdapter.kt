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
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.runnerup.R
import org.runnerup.core.util.CardPressHelper
import org.runnerup.core.workout.Intensity
import org.runnerup.core.workout.Step
import org.runnerup.core.workout.Workout

class WorkoutEditorStepsAdapter(
    private val activity: AppCompatActivity,
    private val onWorkoutChanged: Runnable,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  private var workout: Workout? = null
  private val rows = ArrayList<WorkoutEditorRow>()
  private val touchHelper = ItemTouchHelper(DragCallback())
  private val repeatDecoration = RepeatBlockItemDecoration(activity)
  private var recyclerView: RecyclerView? = null

  init {
    setHasStableIds(true)
  }

  fun attachToRecyclerView(recyclerView: RecyclerView) {
    this.recyclerView = recyclerView
    CardPressHelper.prepareRowHost(recyclerView)
    recyclerView.addItemDecoration(repeatDecoration)
    touchHelper.attachToRecyclerView(recyclerView)
  }

  private fun refreshListUi() {
    reloadRows()
    recyclerView?.invalidateItemDecorations()
    notifyDataSetChanged()
  }

  private fun notifyStepRowChanged(step: Step) {
    val index =
        rows.indexOfFirst { row ->
          when (row) {
            is WorkoutEditorRow.TopStep -> row.step === step
            is WorkoutEditorRow.RepeatHeader -> row.repeat === step
            is WorkoutEditorRow.RepeatChild -> row.step === step
            else -> false
          }
        }
    if (index >= 0) {
      notifyItemChanged(index)
      recyclerView?.invalidateItemDecorations()
    }
  }

  fun setWorkout(w: Workout) {
    workout = w
    refreshListUi()
  }

  private fun reloadRows() {
    rows.clear()
    workout?.let { rows.addAll(WorkoutEditorRow.build(it)) }
    repeatDecoration.setRows(rows)
  }

  override fun getItemCount(): Int = rows.size

  override fun getItemId(position: Int): Long = WorkoutEditorRowOps.stableId(rows[position])

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    val view =
        LayoutInflater.from(activity).inflate(R.layout.workout_editor_step_row, parent, false)
    val card = view.findViewById<View>(R.id.workout_editor_step_card)
    CardPressHelper.prepareCard(card)
    return StepHolder(view)
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    when (val row = rows[position]) {
      is WorkoutEditorRow.TopStep -> (holder as StepHolder).bind(row.step, row, indentDp = 0, showLabel = false)
      is WorkoutEditorRow.RepeatHeader ->
          (holder as StepHolder).bind(row.repeat, row, indentDp = 0, showLabel = false)
      is WorkoutEditorRow.RepeatChild ->
          (holder as StepHolder).bind(row.step, row, indentDp = 44, showLabel = false, inRepeatBlock = true)
    }
  }

  private inner class StepHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val host: View = itemView.findViewById(R.id.workout_editor_row_host)
    private val label: TextView = itemView.findViewById(R.id.workout_editor_row_label)
    private val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)
    private val card: View = itemView.findViewById(R.id.workout_editor_step_card)
    private val repeatAccent: View = itemView.findViewById(R.id.repeat_accent)
    private val icon: ImageView = itemView.findViewById(R.id.step_icon)
    private val duration: TextView = itemView.findViewById(R.id.step_duration)
    private val goal: TextView = itemView.findViewById(R.id.step_goal)
    private val chevron: ImageView = itemView.findViewById(R.id.step_chevron)

    fun bind(
        step: Step,
        row: WorkoutEditorRow,
        indentDp: Int,
        showLabel: Boolean,
        inRepeatBlock: Boolean = false,
    ) {
      val density = activity.resources.displayMetrics.density
      val indentPx = (indentDp * density + 0.5f).toInt()
      host.setPadding(indentPx, 0, 0, 0)
      host.isSelected = false

      val displayMode =
          when (row) {
            is WorkoutEditorRow.RepeatHeader -> WorkoutEditorStepBinder.DisplayMode.REPEAT_HEADER
            is WorkoutEditorRow.RepeatChild -> WorkoutEditorStepBinder.DisplayMode.REPEAT_CHILD
            else -> WorkoutEditorStepBinder.DisplayMode.NORMAL
          }
      val repeatBlock = row is WorkoutEditorRow.RepeatHeader

      card.isClickable = true
      card.isFocusable = true
      when (displayMode) {
        WorkoutEditorStepBinder.DisplayMode.REPEAT_HEADER -> {
          card.setBackgroundResource(R.drawable.workout_repeat_header_bg)
          card.foreground = null
          card.stateListAnimator = null
        }
        WorkoutEditorStepBinder.DisplayMode.REPEAT_CHILD -> {
          card.setBackgroundResource(android.R.color.transparent)
          card.foreground = null
          card.stateListAnimator = null
        }
        else -> CardPressHelper.prepareCard(card)
      }
      repeatAccent.visibility =
          if (displayMode == WorkoutEditorStepBinder.DisplayMode.REPEAT_CHILD) {
            View.VISIBLE
          } else {
            View.GONE
          }

      WorkoutEditorStepBinder.bind(
          activity,
          step,
          WorkoutEditorStepBinder.Views(
              repeatAccent = repeatAccent,
              icon = icon,
              duration = duration,
              goal = goal,
              chevron = chevron,
              dragHandle = dragHandle,
              rowLabel = label,
          ),
          editable = true,
          showRepeatBlockLabel = showLabel,
          displayMode = displayMode,
      )

      WorkoutEditorDrag.bindHandle(dragHandle, touchHelper, this)

      card.setOnClickListener {
        val intensity = step.intensity ?: Intensity.ACTIVE
        val onSaved = Runnable {
          notifyStepRowChanged(step)
          onWorkoutChanged.run()
        }
        if (intensity == Intensity.REPEAT) {
          StepEditorDialog.showEditRepeatCount(activity, step, onSaved)
        } else {
          StepEditorDialog.showEditStep(activity, step, onSaved)
        }
      }

      val openActions = Runnable { showStepActions(step, repeatBlock) }
      WorkoutEditorLongPress.bind(card, openActions)
      WorkoutEditorLongPress.bind(host, openActions)
      WorkoutEditorLongPress.bind(dragHandle, openActions)
    }

  }

  private fun showStepActions(step: Step, repeatBlock: Boolean) {
    val w = workout ?: return
    val title =
        if (repeatBlock) R.string.Workout_repeat_actions else R.string.Workout_step_actions
    val duplicateLabel =
        if (repeatBlock) {
          activity.getString(R.string.Duplicate_repeat_block)
        } else {
          activity.getString(R.string.Duplicate_step)
        }
    AlertDialog.Builder(activity, R.style.AlertDialogTheme)
        .setTitle(title)
        .setItems(
            arrayOf(
                duplicateLabel,
                activity.getString(org.runnerup.common.R.string.Delete),
            ),
        ) { _, which ->
          when (which) {
            0 -> {
              WorkoutEditorHelper.duplicateStep(w, step)
              refreshListUi()
              onWorkoutChanged.run()
            }
            1 -> confirmDelete(step, repeatBlock)
          }
        }
        .show()
  }

  private fun confirmDelete(step: Step, repeatBlock: Boolean) {
    val w = workout ?: return
    val message =
        if (repeatBlock) {
          activity.getString(R.string.Repeat_block)
        } else {
          null
        }
    AlertDialog.Builder(activity, R.style.AlertDialogTheme)
        .setTitle(org.runnerup.common.R.string.Are_you_sure)
        .apply { if (message != null) setMessage(message) }
        .setPositiveButton(org.runnerup.common.R.string.Yes) { dialog, _ ->
          dialog.dismiss()
          WorkoutEditorHelper.deleteStep(w, step)
          refreshListUi()
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
      if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
      val fromRow = rows.getOrNull(from) ?: return false
      val toRow = rows.getOrNull(to) ?: return false
      return WorkoutEditorRowOps.canDrop(fromRow, toRow)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
      val w = workout ?: return false
      val from = viewHolder.bindingAdapterPosition
      val to = target.bindingAdapterPosition
      if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
      if (!WorkoutEditorRowOps.move(w, rows, from, to)) return false
      refreshListUi()
      onWorkoutChanged.run()
      return true
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
      super.onSelectedChanged(viewHolder, actionState)
      if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
        viewHolder?.itemView?.alpha = 0.92f
      } else if (viewHolder != null) {
        viewHolder.itemView.alpha = 1f
      }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
      super.clearView(recyclerView, viewHolder)
      viewHolder.itemView.alpha = 1f
    }
  }

}
