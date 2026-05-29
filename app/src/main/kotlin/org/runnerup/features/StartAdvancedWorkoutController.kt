/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList
import org.runnerup.R
import org.runnerup.core.workout.Workout
import org.runnerup.core.workout.Workout.StepListEntry
import org.runnerup.core.workout.WorkoutSerializer
import org.runnerup.ui.common.widget.SpinnerInterface.OnSetValueListener
import org.runnerup.ui.common.widget.TitleSpinner

/** Advanced workout tab: workout picker and step list for [StartFragment]. */
internal class StartAdvancedWorkoutController(private val fragment: StartFragment) {

  @JvmField var advancedWorkout: Workout? = null

  @JvmField var advancedAudioListAdapter: AudioSchemeListAdapter? = null

  private var advancedWorkoutSpinner: TitleSpinner? = null
  private var advancedWorkoutListAdapter: WorkoutListAdapter? = null
  private var stepsAdapter: WorkoutStepsAdapter? = null

  fun bindAdvancedTab(view: View, inflater: LayoutInflater, mDB: android.database.sqlite.SQLiteDatabase) {
    advancedAudioListAdapter = AudioSchemeListAdapter(mDB, inflater, false)
    advancedAudioListAdapter?.reload()
    val advancedAudioSpinner = view.findViewById<TitleSpinner>(R.id.advanced_audio_cue_spinner)
    advancedAudioSpinner.setAdapter(advancedAudioListAdapter)
    advancedAudioSpinner.setOnSetValueListener(
        StartConfigureAudioListener(fragment, advancedAudioListAdapter!!),
    )

    advancedWorkoutSpinner = view.findViewById(R.id.advanced_workout_spinner)
    advancedWorkoutListAdapter = WorkoutListAdapter(inflater)
    advancedWorkoutListAdapter?.reload()
    advancedWorkoutSpinner?.setAdapter(advancedWorkoutListAdapter)

    if ((advancedWorkoutListAdapter?.count ?: 0) > 1) {
      val firstWorkout = advancedWorkoutListAdapter?.getItem(0).toString()
      advancedWorkoutSpinner?.setValue(firstWorkout)
    }

    advancedWorkoutSpinner?.setOnSetValueListener(
        OnConfigureWorkoutsListener(advancedWorkoutListAdapter!!),
    )

    val stepList = view.findViewById<RecyclerView>(R.id.advanced_step_list)
    stepsAdapter = WorkoutStepsAdapter()
    stepList.layoutManager = LinearLayoutManager(fragment.requireContext())
    stepList.adapter = stepsAdapter
    stepList.itemAnimator = null
  }

  fun getSelectedWorkoutName(): String? = advancedWorkoutSpinner?.value?.toString()

  fun reloadAdapters() {
    advancedAudioListAdapter?.reload()
    advancedWorkoutListAdapter?.reload()
  }

  fun loadAdvanced(name: String?) {
    val ctx: Context = fragment.requireActivity().applicationContext
    var workoutName = name
    if (workoutName == null) {
      val pref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx)
      workoutName = pref.getString(fragment.getString(R.string.pref_advanced_workout), "")
    }
    advancedWorkout = null
    if ("" == workoutName) return
    try {
      advancedWorkout = WorkoutSerializer.readFile(ctx, workoutName)
      stepsAdapter?.setSteps(advancedWorkout?.stepList ?: ArrayList())
    } catch (ex: Exception) {
      ex.printStackTrace()
      AlertDialog.Builder(fragment.requireActivity())
          .setTitle(fragment.getString(org.runnerup.common.R.string.Failed_to_load_workout))
          .setMessage(ex.toString())
          .setPositiveButton(org.runnerup.common.R.string.OK) { dialog, _ -> dialog.dismiss() }
          .show()
    }
  }

  private inner class OnConfigureWorkoutsListener(private val adapter: WorkoutListAdapter) :
      OnSetValueListener {
    private var isInitialization = true

    override fun preSetValue(newValue: String): String {
      if (isInitialization) {
        isInitialization = false
        if (newValue != null &&
            newValue.contentEquals(adapter.getItem(adapter.count - 1) as String)) {
          if (adapter.count > 1) {
            return adapter.getItem(0).toString()
          }
        }
      }
      if (newValue != null &&
          newValue.contentEquals(adapter.getItem(adapter.count - 1) as String)) {
        if (adapter.count > 1) {
          return adapter.getItem(0).toString()
        }
        throw IllegalArgumentException()
      }
      loadAdvanced(newValue)
      return newValue
    }

    override fun preSetValue(newValue: Int): Int {
      loadAdvanced(null)
      return newValue
    }
  }

  private inner class WorkoutStepsAdapter : RecyclerView.Adapter<StepHolder>() {
    private var steps: List<StepListEntry> = ArrayList()

    fun setSteps(newSteps: List<StepListEntry>) {
      steps = newSteps
      notifyDataSetChanged()
    }

    override fun getItemCount(): Int = steps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepHolder {
      val button = StepButton(fragment.requireContext(), null)
      return StepHolder(button as View)
    }

    override fun onBindViewHolder(holder: StepHolder, position: Int) {
      val entry = steps[position]
      val button = holder.itemView as StepButton
      button.setStep(entry.step)
      val pxToDp = fragment.resources.displayMetrics.density
      button.setPadding((entry.level * 8 * pxToDp + 0.5f).toInt(), 0, 0, 0)
      button.setOnChangedListener(onWorkoutChanged)
    }

  }

  private class StepHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

  private val onWorkoutChanged = Runnable {
    val name = advancedWorkoutSpinner?.value?.toString() ?: return@Runnable
    val workout = advancedWorkout ?: return@Runnable
    val ctx = fragment.requireActivity().applicationContext
    try {
      WorkoutSerializer.writeFile(ctx, name, workout)
    } catch (ex: Exception) {
      AlertDialog.Builder(fragment.requireContext())
          .setTitle(org.runnerup.common.R.string.Failed_to_load_workout)
          .setMessage(ex.toString())
          .setPositiveButton(org.runnerup.common.R.string.OK) { dialog, _ -> dialog.dismiss() }
          .show()
    }
  }
}
