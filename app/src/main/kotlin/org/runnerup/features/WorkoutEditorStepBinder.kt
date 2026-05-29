/*
 * Copyright (C) 2024 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Locale
import org.runnerup.R
import org.runnerup.core.util.Formatter
import org.runnerup.core.workout.Dimension
import org.runnerup.core.workout.Intensity
import org.runnerup.core.workout.Step

/** Binds a workout step to editor / preview row views (shared with Start tab). */
object WorkoutEditorStepBinder {

  enum class DisplayMode {
    NORMAL,
    REPEAT_HEADER,
    REPEAT_CHILD,
  }

  data class Views(
      val repeatAccent: View? = null,
      val icon: ImageView,
      val duration: TextView,
      val goal: TextView,
      val chevron: ImageView? = null,
      val dragHandle: View? = null,
      val rowLabel: TextView? = null,
  )

  @JvmStatic
  fun bind(
      context: Context,
      step: Step,
      views: Views,
      editable: Boolean,
      showRepeatBlockLabel: Boolean = false,
      displayMode: DisplayMode = DisplayMode.NORMAL,
  ) {
    val formatter = Formatter(context)
    views.rowLabel?.visibility = View.GONE
    views.repeatAccent?.visibility =
        if (displayMode == DisplayMode.REPEAT_CHILD) View.VISIBLE else View.GONE

    views.dragHandle?.visibility = if (editable) View.VISIBLE else View.GONE
    views.chevron?.visibility = if (editable) View.VISIBLE else View.GONE

    val intensity = step.intensity ?: Intensity.ACTIVE
    when (intensity) {
      Intensity.ACTIVE -> {
        views.icon.setImageResource(R.drawable.step_active)
        views.goal.setTextColor(ContextCompat.getColor(context, R.color.stepActive))
      }
      Intensity.RESTING -> {
        views.icon.setImageResource(R.drawable.step_resting)
        views.goal.setTextColor(ContextCompat.getColor(context, R.color.stepResting))
      }
      Intensity.REPEAT -> {
        views.icon.setImageResource(R.drawable.step_repeat)
        views.duration.visibility = View.VISIBLE
        views.duration.text =
            String.format(
                Locale.getDefault(),
                context.getString(org.runnerup.common.R.string.repeat_times),
                step.getRepeatCount(),
            )
        views.duration.setTextColor(ContextCompat.getColor(context, R.color.stepRepeat))
        views.goal.visibility = View.GONE
        return
      }
      Intensity.WARMUP -> {
        views.icon.setImageResource(R.drawable.step_warmup)
        views.goal.setTextColor(ContextCompat.getColor(context, R.color.stepWarmup))
      }
      Intensity.COOLDOWN -> {
        views.icon.setImageResource(R.drawable.step_cooldown)
        views.goal.setTextColor(ContextCompat.getColor(context, R.color.stepCooldown))
      }
      Intensity.RECOVERY -> {
        views.icon.setImageResource(R.drawable.step_recovery)
        views.goal.setTextColor(ContextCompat.getColor(context, R.color.stepRecovery))
      }
      else -> views.icon.setImageResource(0)
    }

    views.duration.visibility = View.VISIBLE
    views.goal.visibility = View.VISIBLE
    val durationType = step.durationType
    views.duration.text =
        if (durationType == null) {
          context.getString(org.runnerup.common.R.string.Until_press)
        } else {
          formatter.format(Formatter.Format.TXT_LONG, durationType, step.durationValue)
        }

    val goalType = step.targetType
    val target = step.targetValue
    views.goal.text =
        if (goalType == null || target == null) {
          appendCueSummary(context, step, context.getText(intensity.getTextId()))
        } else {
          val prefix =
              if (goalType == Dimension.HR || goalType == Dimension.HRZ) "HR " else ""
          val targetText =
              String.format(
                  Locale.getDefault(),
                  "%s%s-%s",
                  prefix,
                  formatter.format(Formatter.Format.TXT_SHORT, goalType, target.minValue),
                  formatter.format(Formatter.Format.TXT_LONG, goalType, target.maxValue),
              )
          appendCueSummary(context, step, targetText)
        }
  }

  private fun appendCueSummary(context: Context, step: Step, base: CharSequence): CharSequence {
    if (!step.hasPeriodicCues() && step.getAudioCueScheme() == null) {
      return base
    }
    val sb = StringBuilder(base)
    if (step.getPaceCueIntervalSeconds() > 0) {
      sb.append(" · ").append(step.getPaceCueIntervalSeconds()).append("s pace")
    }
    if (step.getHrCueIntervalSeconds() > 0) {
      sb.append(" · ").append(step.getHrCueIntervalSeconds()).append("s HR")
    }
    if (step.getAudioCueScheme() != null) {
      sb.append(" · ").append(step.getAudioCueScheme())
    }
    return sb
  }
}
