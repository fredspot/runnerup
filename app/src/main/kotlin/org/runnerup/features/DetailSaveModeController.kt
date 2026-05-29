/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import org.runnerup.R
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.AutomaticBackupManager
import org.runnerup.data.ActivityCleaner
import org.runnerup.data.PathSimplifier
import org.runnerup.core.workout.Sport
import org.runnerup.ui.common.widget.TitleSpinner
import org.runnerup.ui.common.widget.WidgetUtil
import java.util.ArrayList

/** Save-after-run flow and edit mode for [DetailActivity]. */
internal class DetailSaveModeController(private val activity: DetailActivity) {

  companion object {
    const val MODE_SAVE = 0
    const val MODE_DETAILS = 1
  }

  @JvmField var mode: Int = MODE_DETAILS
  @JvmField var edit: Boolean = false
  @JvmField var hasUnsavedChanges: Boolean = false

  private var saveMenuItem: MenuItem? = null
  private var saveButton: Button? = null
  private var notes: EditText? = null
  private var sport: TitleSpinner? = null
  private var manualDistance: TitleSpinner? = null
  private var headerData: ContentValues? = null
  private var mDB: SQLiteDatabase? = null
  private var mID: Long = 0
  private var rootView: View? = null

  fun bind(
      mode: Int,
      saveButton: Button,
      discardButton: Button,
      resumeButton: Button,
      notes: EditText,
      sport: TitleSpinner,
      manualDistance: TitleSpinner,
      headerData: ContentValues,
      mDB: SQLiteDatabase,
      mID: Long,
      rootView: View,
  ) {
    this.mode = mode
    this.saveButton = saveButton
    this.notes = notes
    this.sport = sport
    this.manualDistance = manualDistance
    this.headerData = headerData
    this.mDB = mDB
    this.mID = mID
    this.rootView = rootView

    saveButton.setOnClickListener { onSaveButtonClick() }

    if (mode == MODE_SAVE) {
      val buttonsLayout = activity.findViewById<View>(R.id.buttons)
      buttonsLayout.visibility = View.GONE
      resumeButton.setOnClickListener { onResumeClick() }
      discardButton.setOnClickListener { onDiscardClick() }
      setEdit(true)
      autoSaveActivity()
    } else if (mode == MODE_DETAILS) {
      resumeButton.visibility = View.GONE
      discardButton.visibility = View.GONE
      setEdit(false)
    }
  }

  fun setSaveMenuItem(item: MenuItem?) {
    saveMenuItem = item
    updateSaveMenuVisibility()
  }

  fun attachNotesChangeListener() {
    if (notes == null || mode != MODE_SAVE) return
    notes?.addTextChangedListener(
        object : android.text.TextWatcher {
          override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

          override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            markAsUnsaved()
          }

          override fun afterTextChanged(s: android.text.Editable?) {}
        },
    )
  }

  fun handleBackPressedSave(): Boolean {
    if (mode != MODE_SAVE) return false
    saveActivity()
    val returnIntent = Intent()
    val sportValue = sport?.valueInt ?: return false
    if (Sport.hasManualDistance(sportValue)) {
      returnIntent.putExtra("MANUAL_DISTANCE", headerData?.getAsDouble(DB.ACTIVITY.DISTANCE))
    }
    activity.getSharedPreferences("nav_prefs", Activity.MODE_PRIVATE)
        .edit()
        .putInt("navigate_to_tab", 0)
        .apply()
    activity.setResult(Activity.RESULT_OK, returnIntent)
    activity.finish()
    return true
  }

  fun setEdit(value: Boolean) {
    edit = value
    if (value) {
      saveButton?.visibility = View.VISIBLE
    } else {
      saveButton?.visibility = View.GONE
    }
    notes?.let { WidgetUtil.setEditable(it, value) }
    sport?.isEnabled = value
    activity.updateViewForSport(sport?.valueInt ?: 0)
    rootView?.let { androidx.core.view.ViewCompat.requestApplyInsets(it) }
  }

  fun saveActivity() {
    val db = mDB ?: return
    val data = headerData ?: return
    val sportSpinner = sport ?: return
    val notesView = notes ?: return
    val sportValue = sportSpinner.valueInt
    data.put(DB.ACTIVITY.COMMENT, notesView.text.toString())
    data.put(DB.ACTIVITY.SPORT, sportValue)
    val whereArgs = arrayOf(mID.toString())
    db.update(DB.ACTIVITY.TABLE, data, "_id = ?", whereArgs)
    try {
      val simplifier = PathSimplifier.getPathSimplifierForSave(activity)
      if (simplifier != null) {
        val ids: ArrayList<String> = simplifier.getNoisyLocationIDsAsStrings(db, mID)
        ActivityCleaner.deleteLocations(db, ids)
        ActivityCleaner().recompute(db, mID)
      }
    } catch (e: Exception) {
      Log.e("DetailActivity", "Failed to simplify path: " + e.message)
    }
    AutomaticBackupManager.createBackupIfNeeded(activity)
  }

  private fun autoSaveActivity() {
    saveActivity()
    val returnIntent = Intent()
    val sportValue = sport?.valueInt ?: 0
    if (Sport.hasManualDistance(sportValue)) {
      returnIntent.putExtra("MANUAL_DISTANCE", headerData?.getAsDouble(DB.ACTIVITY.DISTANCE))
    }
    activity.setResult(Activity.RESULT_OK, returnIntent)
    hasUnsavedChanges = false
    updateSaveMenuVisibility()
  }

  private fun onSaveButtonClick() {
    saveActivity()
    if (mode == MODE_DETAILS) {
      setEdit(false)
      activity.requery()
      return
    }
    activity.startUploadAfterSave()
  }

  private fun onDiscardClick() {
    AlertDialog.Builder(activity)
        .setTitle(org.runnerup.common.R.string.Discard)
        .setMessage(org.runnerup.common.R.string.Are_you_sure)
        .setPositiveButton(org.runnerup.common.R.string.Yes) { dialog, _ ->
          dialog.dismiss()
          activity.setResult(Activity.RESULT_CANCELED)
          activity.finish()
        }
        .setNegativeButton(org.runnerup.common.R.string.No) { dialog, _ -> dialog.dismiss() }
        .show()
  }

  private fun onResumeClick() {
    activity.setResult(Activity.RESULT_FIRST_USER)
    activity.finish()
  }

  fun onMenuSaveSelected() {
    saveActivity()
    hasUnsavedChanges = false
    updateSaveMenuVisibility()
  }

  fun markAsUnsaved() {
    if (!hasUnsavedChanges) {
      hasUnsavedChanges = true
      updateSaveMenuVisibility()
    }
  }

  fun updateSaveMenuVisibility() {
    saveMenuItem?.isVisible = mode == MODE_SAVE && hasUnsavedChanges
  }
}
