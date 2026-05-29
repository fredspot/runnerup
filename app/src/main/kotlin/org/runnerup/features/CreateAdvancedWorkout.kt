/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import org.json.JSONException
import org.runnerup.R
import org.runnerup.core.util.ViewUtil
import org.runnerup.core.workout.RepeatStep
import org.runnerup.core.workout.Step
import org.runnerup.core.workout.Workout
import org.runnerup.core.workout.WorkoutSerializer

class CreateAdvancedWorkout : AppCompatActivity() {

  private var advancedWorkout: Workout? = null
  private var advancedWorkoutStepsAdapter: WorkoutEditorStepsAdapter? = null
  private var workoutName: String = ""
  private var workoutExists = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    setContentView(R.layout.create_advanced_workout)

    workoutName = intent.getStringExtra(ManageWorkoutsActivity.WORKOUT_NAME) ?: ""
    workoutExists = intent.getBooleanExtra(ManageWorkoutsActivity.WORKOUT_EXISTS, false)

    setSupportActionBar(findViewById(R.id.workout_editor_toolbar))
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      title = workoutName
    }

    val advancedStepList = findViewById<RecyclerView>(R.id.new_advnced_workout_steps)
    advancedStepList.layoutManager = LinearLayoutManager(this)
    advancedWorkoutStepsAdapter = WorkoutEditorStepsAdapter(this, onWorkoutChanged)
    advancedWorkoutStepsAdapter!!.attachToRecyclerView(advancedStepList)
    advancedStepList.adapter = advancedWorkoutStepsAdapter

    try {
      createAdvancedWorkout(workoutName, workoutExists)
    } catch (e: Exception) {
      handleWorkoutFileException(e)
      return
    }

    ViewUtil.Insets(findViewById(R.id.create_advanced_workout_view), true)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.workout_editor_menu, menu)
    return true
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    menu.findItem(R.id.menu_workout_discard)?.isVisible = !workoutExists
    return super.onPrepareOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.menu_workout_add_step -> {
        addStep()
        return true
      }
      R.id.menu_workout_add_repeat -> {
        addRepeat()
        return true
      }
      R.id.menu_workout_rename -> {
        showRenameDialog()
        return true
      }
      R.id.menu_workout_save -> {
        saveAndFinish()
        return true
      }
      R.id.menu_workout_discard -> {
        confirmDiscard()
        return true
      }
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onSupportNavigateUp(): Boolean {
    finish()
    return true
  }

  @Throws(JSONException::class, java.io.IOException::class)
  private fun createAdvancedWorkout(name: String, exists: Boolean) {
    advancedWorkout =
        if (exists) {
          WorkoutSerializer.readFile(applicationContext, name)
        } else {
          Workout().also { WorkoutSerializer.writeFile(applicationContext, name, it) }
        }
    refreshStepList()
  }

  private fun refreshStepList() {
    advancedWorkout?.let { advancedWorkoutStepsAdapter?.setWorkout(it) }
  }

  private val onWorkoutChanged =
      Runnable {
        val workout = advancedWorkout ?: return@Runnable
        try {
          WorkoutSerializer.writeFile(applicationContext, workoutName, workout)
        } catch (ex: Exception) {
          AlertDialog.Builder(this@CreateAdvancedWorkout, R.style.AlertDialogTheme)
              .setTitle(org.runnerup.common.R.string.Failed_to_load_workout)
              .setMessage(ex.toString())
              .setPositiveButton(org.runnerup.common.R.string.OK) { dialog, _ -> dialog.dismiss() }
              .show()
        }
      }

  private fun addStep() {
    advancedWorkout?.addStep(Step())
    refreshStepList()
  }

  private fun addRepeat() {
    advancedWorkout?.addStep(RepeatStep())
    refreshStepList()
  }

  private fun saveAndFinish() {
    val workout = advancedWorkout ?: return
    try {
      WorkoutSerializer.writeFile(applicationContext, workoutName, workout)
      finish()
    } catch (e: Exception) {
      handleWorkoutFileException(e)
    }
  }

  private fun showRenameDialog() {
    val input =
        EditText(this).apply {
          setText(workoutName)
          setSelection(text.length)
        }
    AlertDialog.Builder(this, R.style.AlertDialogTheme)
        .setTitle(R.string.Rename_workout)
        .setMessage(org.runnerup.common.R.string.Set_workout_name)
        .setView(input)
        .setPositiveButton(org.runnerup.common.R.string.OK) { _, _ ->
          val newName = input.text.toString().trim()
          if (newName.isNotEmpty() && newName != workoutName) {
            renameWorkout(newName)
          }
        }
        .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ -> dialog.dismiss() }
        .show()
  }

  private fun renameWorkout(newName: String) {
    val ctx: Context = applicationContext
    val oldFile = WorkoutSerializer.getFile(ctx, workoutName)
    val newFile = WorkoutSerializer.getFile(ctx, newName)
    if (newFile.exists()) {
      Toast.makeText(this, R.string.Workout_name_exists, Toast.LENGTH_LONG).show()
      return
    }
    if (!oldFile.renameTo(newFile)) {
      Toast.makeText(
              this,
              getString(org.runnerup.common.R.string.Failed_to_create_workout),
              Toast.LENGTH_LONG,
          )
          .show()
      return
    }
    val pref = PreferenceManager.getDefaultSharedPreferences(this)
    val prefKey = getString(R.string.pref_advanced_workout)
    if (workoutName == pref.getString(prefKey, "")) {
      pref.edit().putString(prefKey, newName).apply()
    }
    workoutName = newName
    supportActionBar?.title = workoutName
    onWorkoutChanged.run()
  }

  private fun handleWorkoutFileException(e: Exception) {
    AlertDialog.Builder(this, R.style.AlertDialogTheme)
        .setTitle(getString(org.runnerup.common.R.string.Failed_to_create_workout))
        .setMessage(e.toString())
        .setPositiveButton(org.runnerup.common.R.string.OK) { dialog, _ -> dialog.dismiss() }
        .show()
  }

  private fun confirmDiscard() {
    AlertDialog.Builder(this, R.style.AlertDialogTheme)
        .setTitle(org.runnerup.common.R.string.Delete_workout)
        .setMessage(org.runnerup.common.R.string.Are_you_sure)
        .setPositiveButton(org.runnerup.common.R.string.Yes) { dialog, _ ->
          dialog.dismiss()
          WorkoutSerializer.getFile(applicationContext, workoutName).delete()
          finish()
        }
        .setNegativeButton(org.runnerup.common.R.string.No) { dialog, _ -> dialog.dismiss() }
        .show()
  }
}
