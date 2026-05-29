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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import org.runnerup.R
import org.runnerup.core.util.CardPressHelper
import org.runnerup.core.workout.Workout

/** Read-only workout step list for the Run tab advanced preview. */
class WorkoutEditorPreviewAdapter(private val fragment: Fragment) :
    RecyclerView.Adapter<WorkoutEditorPreviewAdapter.PreviewStepHolder>() {

  private var rows: List<WorkoutEditorRow> = emptyList()
  private val repeatDecoration = RepeatBlockItemDecoration(fragment.requireContext())

  fun attachToRecyclerView(recyclerView: RecyclerView) {
    CardPressHelper.prepareRowHost(recyclerView)
    recyclerView.addItemDecoration(repeatDecoration)
  }

  fun setWorkout(workout: Workout?) {
    rows = if (workout != null) WorkoutEditorRow.build(workout) else emptyList()
    repeatDecoration.setRows(rows)
    notifyDataSetChanged()
  }

  override fun getItemCount(): Int = rows.size

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewStepHolder {
    val view =
        LayoutInflater.from(fragment.requireContext())
            .inflate(R.layout.workout_editor_step_row, parent, false)
    return PreviewStepHolder(view)
  }

  override fun onBindViewHolder(holder: PreviewStepHolder, position: Int) {
    when (val row = rows[position]) {
      is WorkoutEditorRow.TopStep -> holder.bind(row, row.step, indentDp = 0)
      is WorkoutEditorRow.RepeatHeader -> holder.bind(row, row.repeat, indentDp = 0)
      is WorkoutEditorRow.RepeatChild -> holder.bind(row, row.step, indentDp = 44)
    }
  }

  class PreviewStepHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val host: View = itemView.findViewById(R.id.workout_editor_row_host)
    private val label: TextView = itemView.findViewById(R.id.workout_editor_row_label)
    private val dragHandle: View = itemView.findViewById(R.id.drag_handle)
    private val card: View = itemView.findViewById(R.id.workout_editor_step_card)
    private val repeatAccent: View = itemView.findViewById(R.id.repeat_accent)
    private val icon: ImageView = itemView.findViewById(R.id.step_icon)
    private val duration: TextView = itemView.findViewById(R.id.step_duration)
    private val goal: TextView = itemView.findViewById(R.id.step_goal)
    private val chevron: ImageView = itemView.findViewById(R.id.step_chevron)

    fun bind(row: WorkoutEditorRow, step: org.runnerup.core.workout.Step, indentDp: Int) {
      val ctx = itemView.context
      val density = ctx.resources.displayMetrics.density
      val indentPx = (indentDp * density + 0.5f).toInt()
      host.setPadding(indentPx, host.paddingTop, host.paddingEnd, host.paddingBottom)
      dragHandle.visibility = View.GONE
      chevron.visibility = View.GONE
      card.isClickable = false
      card.isFocusable = false
      val displayMode =
          when (row) {
            is WorkoutEditorRow.RepeatHeader -> WorkoutEditorStepBinder.DisplayMode.REPEAT_HEADER
            is WorkoutEditorRow.RepeatChild -> WorkoutEditorStepBinder.DisplayMode.REPEAT_CHILD
            else -> WorkoutEditorStepBinder.DisplayMode.NORMAL
          }
      when (displayMode) {
        WorkoutEditorStepBinder.DisplayMode.REPEAT_HEADER ->
            card.setBackgroundResource(R.drawable.workout_repeat_header_bg)
        WorkoutEditorStepBinder.DisplayMode.REPEAT_CHILD -> {
          card.setBackgroundResource(android.R.color.transparent)
          card.foreground = null
        }
        else -> CardPressHelper.prepareCard(card)
      }
      WorkoutEditorStepBinder.bind(
          ctx,
          step,
          WorkoutEditorStepBinder.Views(
              repeatAccent = repeatAccent,
              icon = icon,
              duration = duration,
              goal = goal,
              rowLabel = label,
          ),
          editable = false,
          showRepeatBlockLabel = row is WorkoutEditorRow.RepeatHeader,
          displayMode = displayMode,
      )
    }
  }
}
