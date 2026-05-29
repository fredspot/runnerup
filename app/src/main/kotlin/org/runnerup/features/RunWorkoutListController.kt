/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.ContentValues
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList
import java.util.Locale
import org.runnerup.R
import org.runnerup.core.util.Formatter
import org.runnerup.core.workout.Intensity
import org.runnerup.core.workout.Step

/** Workout step / lap strip for [RunActivity]. */
internal class RunWorkoutListController(
    private val activity: RunActivity,
    private val formatter: Formatter,
    private val rows: ArrayList<WorkoutRow>,
    recyclerView: RecyclerView,
) {

  class WorkoutRow {
    @JvmField var step: Step? = null
    @JvmField var lap: ContentValues? = null
    @JvmField var level: Int = 0
  }

  @JvmField var currentStep: Step? = null

  private val adapter = WorkoutListAdapter()
  private val layoutManager = LinearLayoutManager(activity)

  init {
    recyclerView.layoutManager = layoutManager
    recyclerView.adapter = adapter
    recyclerView.itemAnimator = null
  }

  fun notifyDataSetChanged() {
    adapter.notifyDataSetChanged()
  }

  fun scrollToCurrentStep() {
    val step = currentStep ?: return
    val position = getPosition(step)
    if (position >= 0) {
      layoutManager.scrollToPositionWithOffset(position, 0)
    }
  }

  fun populateFromWorkout(workout: org.runnerup.core.workout.Workout) {
    rows.clear()
    for (entry in workout.getStepList()) {
      val row = WorkoutRow()
      row.level = entry.level
      row.step = entry.step
      row.lap = null
      rows.add(row)
    }
    notifyDataSetChanged()
  }

  fun onCurrentStepChanged(step: Step?) {
    if (step == currentStep) {
      return
    }
    currentStep = step
    notifyDataSetChanged()
    scrollToCurrentStep()
  }

  private fun getPosition(step: Step?): Int {
    if (step == null) return 0
    for (i in rows.indices) {
      if (rows[i].step === step) return i
    }
    return 0
  }

  private inner class WorkoutListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int =
        if (rows[position].step != null) VIEW_TYPE_STEP else VIEW_TYPE_LAP

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
      val inflater = LayoutInflater.from(activity)
      val layout =
          if (viewType == VIEW_TYPE_STEP) R.layout.workout_row else R.layout.laplist_row
      return WorkoutRowHolder(inflater.inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
      val row = rows[position]
      if (row.step != null) {
        bindStepRow(holder.itemView, row.step!!, row.level)
      }
    }

    private fun bindStepRow(view: View, step: Step, level: Int) {
      val intensity = view.findViewById<TextView>(R.id.workout_step_intensity)
      val durationType = view.findViewById<TextView>(R.id.workout_step_duration_type)
      val durationValue = view.findViewById<TextView>(R.id.workout_step_duration_value)
      val targetPace = view.findViewById<TextView>(R.id.workout_step_pace)
      intensity.setPadding(level * 10, 0, 0, 0)
      intensity.setText(activity.resources.getText(step.intensity.textId))
      if (step.durationType != null) {
        durationType.setText(activity.resources.getText(step.durationType.textId))
        durationValue.text =
            formatter.format(
                Formatter.Format.TXT_LONG,
                step.durationType,
                step.durationValue,
            )
      } else {
        durationType.text = ""
        durationValue.text = ""
      }
      if (currentStep === step) {
        // highlight current step if needed
      } else {
        view.setBackgroundResource(android.R.color.black)
      }
      if (step.targetType == null) {
        targetPace.text = ""
      } else {
        val minValue = step.targetValue.minValue
        val maxValue = step.targetValue.maxValue
        targetPace.text =
            if (minValue == maxValue) {
              formatter.format(Formatter.Format.TXT_SHORT, step.targetType, minValue)
            } else {
              String.format(
                  Locale.getDefault(),
                  "%s-%s",
                  formatter.format(Formatter.Format.TXT_SHORT, step.targetType, minValue),
                  formatter.format(Formatter.Format.TXT_SHORT, step.targetType, maxValue),
              )
            }
      }
      if (step.intensity == Intensity.REPEAT) {
        durationValue.text =
            if (step.currentRepeat >= step.repeatCount) {
              activity.getString(org.runnerup.common.R.string.Finished)
            } else {
              String.format(
                  Locale.getDefault(),
                  "%d/%d",
                  step.currentRepeat + 1,
                  step.repeatCount,
              )
            }
      }
    }

  }

  private class WorkoutRowHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

  private companion object {
    private const val VIEW_TYPE_STEP = 0
    private const val VIEW_TYPE_LAP = 1
  }
}
