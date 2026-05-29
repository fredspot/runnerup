/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.view.View
import android.widget.ListView
import android.widget.TextView
import org.runnerup.common.util.Constants
import org.runnerup.core.util.Formatter
import org.runnerup.core.workout.Intensity
import org.runnerup.core.workout.Scope
import org.runnerup.core.workout.Step
import org.runnerup.core.workout.Workout
import org.runnerup.tracking.Tracker
import org.runnerup.tracking.component.TrackerHRM

/** Live run metrics row updates for [RunActivity]. */
internal class RunMetricsController(
    private val formatter: Formatter,
    private val activityTime: TextView,
    private val activityDistance: TextView,
    private val activityPace: TextView,
    private val lapTime: TextView,
    private val lapDistance: TextView,
    private val lapPace: TextView,
    private val intervalTime: TextView,
    private val intervalDistance: TextView,
    private val intervalPace: TextView,
    private val currentPace: TextView,
    private val tableRowInterval: View?,
    private val activityHr: TextView,
    private val lapHr: TextView,
    private val intervalHr: TextView,
    private val currentHr: TextView,
    private val activityHeaderHr: TextView,
    private val workoutList: ListView,
    private val workoutRows: ArrayList<RunActivity.WorkoutRow>,
) {

  @JvmField var currentStep: Step? = null

  fun updateMetrics(workout: Workout, tracker: Tracker) {
    val ad = workout.getDistance(Scope.ACTIVITY)
    val at = workout.getTime(Scope.ACTIVITY)
    val ap = workout.getSpeed(Scope.ACTIVITY)
    activityTime.text = formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(at))
    activityDistance.text = formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(ad))
    activityPace.text = formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, ap)

    val ld = workout.getDistance(Scope.LAP)
    val lt = workout.getTime(Scope.LAP)
    val lp = workout.getSpeed(Scope.LAP)
    lapTime.text = formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(lt))
    lapDistance.text = formatter.formatDistance(Formatter.Format.TXT_LONG, Math.round(ld))
    lapPace.text = formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, lp)

    val step = currentStep
    if (tableRowInterval != null &&
        step != null &&
        workout.getWorkoutType() != Constants.WORKOUT_TYPE.BASIC &&
        step.intensity == Intensity.ACTIVE) {
      val id = workout.getDistance(Scope.STEP)
      val it = workout.getTime(Scope.STEP)
      val ip = workout.getSpeed(Scope.STEP)
      tableRowInterval.visibility = View.VISIBLE
      intervalTime.text = formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, Math.round(it))
      intervalDistance.text = formatter.formatDistance(Formatter.Format.TXT_LONG, Math.round(id))
      intervalPace.text = formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, ip)
    } else {
      tableRowInterval?.visibility = View.GONE
    }

    val cp = workout.getSpeed(Scope.CURRENT)
    currentPace.text = formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, cp)

    if (tracker.isComponentConnected(TrackerHRM.NAME)) {
      val ahr = workout.getHeartRate(Scope.ACTIVITY)
      val ihr = workout.getHeartRate(Scope.STEP)
      val lhr = workout.getHeartRate(Scope.LAP)
      val chr = workout.getHeartRate(Scope.CURRENT)
      lapHr.text = formatter.formatHeartRate(Formatter.Format.TXT_SHORT, lhr)
      intervalHr.text = formatter.formatHeartRate(Formatter.Format.TXT_SHORT, ihr)
      currentHr.text = formatter.formatHeartRate(Formatter.Format.TXT_SHORT, chr)
      activityHr.text = formatter.formatHeartRate(Formatter.Format.TXT_SHORT, ahr)
      activityHr.visibility = View.VISIBLE
      lapHr.visibility = View.VISIBLE
      intervalHr.visibility = View.VISIBLE
      currentHr.visibility = View.VISIBLE
      activityHeaderHr.visibility = View.VISIBLE
    } else {
      activityHr.visibility = View.GONE
      lapHr.visibility = View.GONE
      intervalHr.visibility = View.GONE
      currentHr.visibility = View.GONE
      activityHeaderHr.visibility = View.GONE
    }

    val curr = workout.getCurrentStep()
    if (curr != currentStep) {
      (workoutList.adapter as? RunActivity.WorkoutAdapter)?.notifyDataSetChanged()
      currentStep = curr
      workoutList.setSelection(getPosition(workoutRows, currentStep))
    }
  }

  private fun getPosition(
      workoutRows: ArrayList<RunActivity.WorkoutRow>,
      step: Step?,
  ): Int {
    if (step == null) return 0
    for (i in workoutRows.indices) {
      if (workoutRows[i].step === step) return i
    }
    return 0
  }
}
