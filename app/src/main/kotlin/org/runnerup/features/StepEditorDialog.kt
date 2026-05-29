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
import android.util.Pair
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import org.runnerup.R
import org.runnerup.data.DBHelper
import org.runnerup.common.util.Constants.DB.DIMENSION
import org.runnerup.core.util.Formatter
import org.runnerup.core.util.SafeParse
import org.runnerup.ui.common.widget.NumberPicker
import org.runnerup.ui.common.widget.TitleSpinner
import org.runnerup.core.workout.Dimension
import org.runnerup.core.workout.Intensity
import org.runnerup.core.workout.Range
import org.runnerup.core.workout.Step

object StepEditorDialog {

  @JvmStatic
  fun showEditStep(context: Context, step: Step, onChanged: Runnable?) {
    if (step.intensity == Intensity.REPEAT) {
      showEditRepeatCount(context, step, onChanged)
      return
    }
    val inflater = LayoutInflater.from(context)
    val layout = inflater.inflate(R.layout.step_dialog, null)
    val save = setupEditStep(context, inflater, step, layout)
    AlertDialog.Builder(context, R.style.AlertDialogTheme)
        .setTitle(org.runnerup.common.R.string.Edit_step)
        .setView(layout)
        .setPositiveButton(org.runnerup.common.R.string.OK) { dialog, _ ->
          save.run()
          dialog.dismiss()
          onChanged?.run()
        }
        .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ -> dialog.dismiss() }
        .show()
  }

  @JvmStatic
  fun showEditRepeatCount(context: Context, step: Step, onChanged: Runnable?) {
    val numberPicker = NumberPicker(context, null)
    numberPicker.orientation = LinearLayout.VERTICAL
    numberPicker.setDigits(4)
    numberPicker.setRange(0, 9999, true)
    numberPicker.value = step.getRepeatCount()
    val wrapper = LinearLayout(context)
    wrapper.layoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT,
        )
    wrapper.gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
    wrapper.addView(numberPicker)
    wrapper.setBackgroundColor(ContextCompat.getColor(context, R.color.backgroundPrimary))
    wrapper.setPadding(
        (24 * context.resources.displayMetrics.density).toInt(),
        (16 * context.resources.displayMetrics.density).toInt(),
        (24 * context.resources.displayMetrics.density).toInt(),
        (16 * context.resources.displayMetrics.density).toInt(),
    )
    AlertDialog.Builder(context, R.style.AlertDialogTheme)
        .setTitle(org.runnerup.common.R.string.repeat)
        .setView(wrapper)
        .setPositiveButton(org.runnerup.common.R.string.OK) { dialog, _ ->
          step.setRepeatCount(numberPicker.value)
          dialog.dismiss()
          onChanged?.run()
        }
        .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ -> dialog.dismiss() }
        .show()
  }

  @JvmStatic
  fun setupEditStep(
      context: Context,
      inflater: LayoutInflater,
      step: Step,
      layout: View,
  ): Runnable {
    val formatter = Formatter(context)
    val stepType = layout.findViewById<TitleSpinner>(R.id.step_dialog_intensity)
    stepType.setValue((step.intensity ?: Intensity.ACTIVE).value)

    val hrZonesAdapter = HRZonesListAdapter(context, inflater)
    val durationType = layout.findViewById<TitleSpinner>(R.id.step_dialog_duration_type)
    val durationTime = layout.findViewById<TitleSpinner>(R.id.step_dialog_duration_time)
    val durationDistance = layout.findViewById<TitleSpinner>(R.id.step_dialog_duration_distance)
    durationType.setOnSetValueListener(
        object : org.runnerup.ui.common.widget.SpinnerInterface.OnSetValueListener {
          override fun preSetValue(newValue: String?): String? = null

          override fun preSetValue(newValue: Int): Int {
            when (newValue) {
              DIMENSION.TIME -> {
                durationTime.isEnabled = true
                durationTime.visibility = View.VISIBLE
                durationTime.setValue(
                    formatter.formatElapsedTime(
                        Formatter.Format.TXT, step.durationValue.toLong()))
                durationDistance.visibility = View.GONE
              }
              DIMENSION.DISTANCE -> {
                durationTime.visibility = View.GONE
                durationDistance.isEnabled = true
                durationDistance.visibility = View.VISIBLE
                durationDistance.setValue(step.durationValue.toLong().toString())
              }
              else -> {
                durationTime.isEnabled = false
                durationDistance.isEnabled = false
              }
            }
            return newValue
          }
        })
    if (step.durationType == null) {
      durationType.setValue(-1)
    } else {
      durationType.setValue(step.durationType.value)
    }

    val targetType = layout.findViewById<TitleSpinner>(R.id.step_dialog_target_type)
    val targetPaceLo = layout.findViewById<TitleSpinner>(R.id.step_dialog_target_pace_lo)
    val targetPaceHi = layout.findViewById<TitleSpinner>(R.id.step_dialog_target_pace_hi)
    val targetHrz = layout.findViewById<TitleSpinner>(R.id.step_dialog_target_hrz)

    val audioCue = layout.findViewById<TitleSpinner>(R.id.step_dialog_audio_cue)
    val hrCueSeconds = layout.findViewById<TitleSpinner>(R.id.step_dialog_hr_cue_seconds)
    val hrCueAnnouncement = layout.findViewById<TitleSpinner>(R.id.step_dialog_hr_cue_announcement)
    val paceCueSeconds = layout.findViewById<TitleSpinner>(R.id.step_dialog_pace_cue_seconds)

    val audioAdapter =
        AudioSchemeListAdapter(DBHelper.getReadableDatabase(context), inflater, false)
    audioAdapter.reload()
    audioCue.setAdapter(audioAdapter)
    if (step.getAudioCueScheme() != null) {
      audioCue.setValue(step.getAudioCueScheme())
    } else {
      audioCue.setValue(context.getString(org.runnerup.common.R.string.Default))
    }
    hrCueSeconds.setValue(step.getHrCueIntervalSeconds().toString())
    hrCueAnnouncement.setValue(step.getHrCueAnnouncement())
    paceCueSeconds.setValue(step.getPaceCueIntervalSeconds().toString())

    if (!hrZonesAdapter.hrZones.isConfigured) {
      targetType.addDisabledValue(DIMENSION.HRZ)
    } else {
      targetHrz.setAdapter(hrZonesAdapter)
    }

    targetType.setOnSetValueListener(
        object : org.runnerup.ui.common.widget.SpinnerInterface.OnSetValueListener {
          override fun preSetValue(newValue: String?): String? = null

          override fun preSetValue(newValue: Int): Int {
            val target: Range? = step.targetValue
            when (newValue) {
              DIMENSION.PACE -> {
                targetPaceLo.isEnabled = true
                targetPaceHi.isEnabled = true
                targetPaceLo.visibility = View.VISIBLE
                targetPaceHi.visibility = View.VISIBLE
                if (target != null) {
                  targetPaceLo.setValue(
                      formatter.formatPace(Formatter.Format.TXT_SHORT, target.minValue))
                  targetPaceHi.setValue(
                      formatter.formatPace(Formatter.Format.TXT_SHORT, target.maxValue))
                }
                targetHrz.visibility = View.GONE
              }
              DIMENSION.HR, DIMENSION.HRZ -> {
                targetPaceLo.visibility = View.GONE
                targetPaceHi.visibility = View.GONE
                targetHrz.isEnabled = true
                targetHrz.visibility = View.VISIBLE
                if (target != null) {
                  var matchedZone =
                      hrZonesAdapter.hrZones.match(target.minValue, target.maxValue) - 2
                  if (matchedZone < 0) matchedZone = 0
                  targetHrz.setValue(matchedZone)
                } else {
                  targetHrz.setValue(0)
                }
              }
              else -> {
                targetPaceLo.isEnabled = false
                targetPaceHi.isEnabled = false
                targetHrz.isEnabled = false
              }
            }
            return newValue
          }
        })
    if (step.targetType == null) {
      targetType.setValue(-1)
    } else if (step.targetType.value == DIMENSION.HR) {
      targetType.setValue(DIMENSION.HRZ)
    } else {
      targetType.setValue(step.targetType.value)
    }

    return Runnable {
      step.intensity = Intensity.valueOf(stepType.valueInt)
      step.durationType = Dimension.valueOf(durationType.valueInt)
      when (durationType.valueInt) {
        DIMENSION.DISTANCE ->
            step.durationValue =
                SafeParse.parseDouble(durationDistance.value.toString(), 1000.0)
        DIMENSION.TIME ->
            step.durationValue =
                SafeParse.parseSeconds(durationTime.value.toString(), 60).toDouble()
      }
      step.targetType = Dimension.valueOf(targetType.valueInt)
      when (targetType.valueInt) {
        DIMENSION.PACE -> {
          val unitMeters = Formatter.getUnitMeters(context)
          val paceLo = SafeParse.parseSeconds(targetPaceLo.value.toString(), 5 * 60).toDouble()
          val paceHi = SafeParse.parseSeconds(targetPaceHi.value.toString(), 5 * 60).toDouble()
          step.setTargetValue(paceLo / unitMeters, paceHi / unitMeters)
        }
        DIMENSION.HRZ -> {
          step.targetType = Dimension.HR
          val range: Pair<Int, Int> =
              hrZonesAdapter.hrZones.getHRValues(targetHrz.valueInt + 1)
          step.setTargetValue(range.first.toDouble(), range.second.toDouble())
        }
      }
      val scheme = audioCue.value.toString()
      step.setAudioCueScheme(
          if (scheme == context.getString(org.runnerup.common.R.string.Default)) {
            null
          } else {
            scheme
          })
      step.setHrCueIntervalSeconds(SafeParse.parseInt(hrCueSeconds.value.toString(), 0))
      step.setHrCueAnnouncement(hrCueAnnouncement.valueInt)
      step.setPaceCueIntervalSeconds(SafeParse.parseInt(paceCueSeconds.value.toString(), 0))
    }
  }
}
