/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.util.Calendar
import java.util.Locale
import org.runnerup.R
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.Formatter
import org.runnerup.core.workout.Sport
import org.runnerup.data.ActivityEditStore
import org.runnerup.data.ActivityTrackEdit
import org.runnerup.data.DBHelper

/** Edits name, notes, sport, start time, distance, duration, track trim, and injury. */
class ActivityEditActivity : AppCompatActivity() {

  private var db: SQLiteDatabase? = null
  private var activityId: Long = -1
  private var originalDistanceM: Double = 0.0
  private var originalDurationSec: Long = 0
  private val calendar = Calendar.getInstance()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_edit)
    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setTitle(R.string.edit_activity_title)
    }

    activityId = intent.getLongExtra(EXTRA_ACTIVITY_ID, -1)
    if (activityId < 0) {
      finish()
      return
    }
    db = DBHelper.getWritableDatabase(this)
    if (!loadActivity()) {
      finish()
      return
    }

    findViewById<Button>(R.id.edit_date).setOnClickListener { pickDate() }
    findViewById<Button>(R.id.edit_time).setOnClickListener { pickTime() }
    findViewById<Button>(R.id.edit_injury).setOnClickListener {
      startActivity(
          Intent(this, InjuryEditorActivity::class.java).apply {
            putExtra(InjuryEditorActivity.EXTRA_ACTIVITY_ID, activityId)
            putExtra(InjuryEditorActivity.EXTRA_PHASE, DB.ACTIVITY_INJURY.PHASE_BEFORE)
          },
      )
    }
    findViewById<Button>(R.id.edit_save).setOnClickListener { onSave() }
  }

  override fun onSupportNavigateUp(): Boolean {
    finish()
    return true
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(db)
  }

  private fun loadActivity(): Boolean {
    val database = db ?: return false
    val sql =
        "SELECT ${DB.ACTIVITY.NAME}, ${DB.ACTIVITY.COMMENT}, ${DB.ACTIVITY.SPORT}, " +
            "${DB.ACTIVITY.START_TIME}, ${DB.ACTIVITY.DISTANCE}, ${DB.ACTIVITY.TIME} " +
            "FROM ${DB.ACTIVITY.TABLE} WHERE ${DB.PRIMARY_KEY} = ?"
    database.rawQuery(sql, arrayOf(activityId.toString())).use { cursor ->
      if (!cursor.moveToFirst()) {
        return false
      }
      findViewById<EditText>(R.id.edit_name).setText(if (cursor.isNull(0)) "" else cursor.getString(0))
      findViewById<EditText>(R.id.edit_notes).setText(if (cursor.isNull(1)) "" else cursor.getString(1))
      val sport = if (cursor.isNull(2)) 0 else cursor.getInt(2)
      val sports = Sport.getStringArray(resources)
      val spinner = findViewById<Spinner>(R.id.edit_sport)
      spinner.adapter =
          ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sports)
      if (sport in sports.indices) {
        spinner.setSelection(sport)
      }
      val startSec = if (cursor.isNull(3)) 0L else cursor.getLong(3)
      calendar.timeInMillis = startSec * 1000L
      originalDistanceM = if (cursor.isNull(4)) 0.0 else cursor.getDouble(4)
      originalDurationSec = if (cursor.isNull(5)) 0L else cursor.getLong(5)
      findViewById<EditText>(R.id.edit_distance)
          .setText(String.format(Locale.US, "%.2f", originalDistanceM / 1000.0))
      findViewById<EditText>(R.id.edit_duration).setText(formatDuration(originalDurationSec))
      refreshDateTimeLabels()
    }
    return true
  }

  private fun refreshDateTimeLabels() {
    val date = android.text.format.DateFormat.getMediumDateFormat(this).format(calendar.time)
    val time = android.text.format.DateFormat.getTimeFormat(this).format(calendar.time)
    findViewById<Button>(R.id.edit_date).text = getString(R.string.edit_activity_date) + ": " + date
    findViewById<Button>(R.id.edit_time).text = getString(R.string.edit_activity_time) + ": " + time
  }

  private fun pickDate() {
    DatePickerDialog(
            this,
            { _, year, month, day ->
              calendar.set(Calendar.YEAR, year)
              calendar.set(Calendar.MONTH, month)
              calendar.set(Calendar.DAY_OF_MONTH, day)
              refreshDateTimeLabels()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        )
        .show()
  }

  private fun pickTime() {
    TimePickerDialog(
            this,
            { _, hour, minute ->
              calendar.set(Calendar.HOUR_OF_DAY, hour)
              calendar.set(Calendar.MINUTE, minute)
              refreshDateTimeLabels()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true,
        )
        .show()
  }

  private fun onSave() {
    val durationText = findViewById<EditText>(R.id.edit_duration).text.toString()
    val durationSec = ActivityTrackEdit.parseDurationSeconds(durationText)
    val durationOverride =
        durationSec != null && durationSec != originalDurationSec
    if (durationText.isNotBlank() && durationText != "--" && durationSec == null) {
      Toast.makeText(this, R.string.edit_activity_bad_duration, Toast.LENGTH_SHORT).show()
      return
    }
    val distanceM = parseKm(findViewById<EditText>(R.id.edit_distance).text.toString())
    val distanceOverride = distanceM != null && kotlin.math.abs(distanceM - originalDistanceM) > 1.0
    val trimStart = (parseKm(findViewById<EditText>(R.id.edit_trim_start).text.toString()) ?: 0.0)
    val trimEnd = (parseKm(findViewById<EditText>(R.id.edit_trim_end).text.toString()) ?: 0.0)
    if (trimStart > 0 || trimEnd > 0) {
      AlertDialog.Builder(this)
          .setMessage(R.string.edit_activity_trim_confirm)
          .setPositiveButton(org.runnerup.common.R.string.Yes) { _, _ ->
            save(distanceOverride, distanceM ?: originalDistanceM, durationOverride, durationSec ?: originalDurationSec, trimStart, trimEnd)
          }
          .setNegativeButton(org.runnerup.common.R.string.No, null)
          .show()
      return
    }
    save(distanceOverride, distanceM ?: originalDistanceM, durationOverride, durationSec ?: originalDurationSec, 0.0, 0.0)
  }

  private fun save(
      distanceOverride: Boolean,
      distanceM: Double,
      durationOverride: Boolean,
      durationSec: Long,
      trimStartM: Double,
      trimEndM: Double,
  ) {
    val database = db ?: return
    val request =
        ActivityEditStore.Request(
            findViewById<EditText>(R.id.edit_name).text.toString(),
            findViewById<EditText>(R.id.edit_notes).text.toString(),
            findViewById<Spinner>(R.id.edit_sport).selectedItemPosition,
            calendar.timeInMillis / 1000L,
            distanceOverride,
            distanceM,
            durationOverride,
            durationSec,
            trimStartM,
            trimEndM,
        )
    when (ActivityEditStore.apply(database, activityId, request)) {
      ActivityEditStore.Status.OK -> {
        setResult(RESULT_OK)
        finish()
      }
      ActivityEditStore.Status.TRIM_TOO_MUCH ->
          Toast.makeText(this, R.string.edit_activity_trim_too_much, Toast.LENGTH_LONG).show()
      ActivityEditStore.Status.NOT_FOUND -> finish()
    }
  }

  private fun parseKm(text: String): Double? {
    val trimmed = text.trim().replace(',', '.')
    if (trimmed.isEmpty()) {
      return null
    }
    val km = trimmed.toDoubleOrNull() ?: return null
    if (km < 0) {
      return null
    }
    return km * 1000.0
  }

  private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) {
      return "0:00:00"
    }
    return Formatter.formatElapsedTimeHms(seconds)
  }

  companion object {
    const val EXTRA_ACTIVITY_ID = "activity_id"
  }
}
