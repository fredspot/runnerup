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
import android.database.sqlite.SQLiteDatabase
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import androidx.preference.PreferenceManager
import java.util.ArrayList
import org.runnerup.R
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.HRZones
import org.runnerup.ui.common.widget.SpinnerInterface.OnSetValueListener
import org.runnerup.ui.common.widget.TitleSpinner

/** Interval workout tab spinners for [StartFragment]. */
internal class StartIntervalController(private val fragment: StartFragment) {

  private var intervalType: TitleSpinner? = null
  private var intervalTime: TitleSpinner? = null
  private var intervalDistance: TitleSpinner? = null
  private var intervalRestType: TitleSpinner? = null
  private var intervalRestTime: TitleSpinner? = null
  private var intervalRestDistance: TitleSpinner? = null
  private var intervalWarmupType: TitleSpinner? = null
  private var intervalWarmupTime: TitleSpinner? = null
  private var intervalWarmupDistance: TitleSpinner? = null
  private var intervalCooldownType: TitleSpinner? = null
  private var intervalCooldownTime: TitleSpinner? = null
  private var intervalCooldownDistance: TitleSpinner? = null
  private var intervalTargetHrz: TitleSpinner? = null

  @JvmField var intervalAudioListAdapter: AudioSchemeListAdapter? = null

  private var intervalTypeSetValue: OnSetValueListener? = null
  private var intervalRestTypeSetValue: OnSetValueListener? = null

  fun bindIntervalTab(
      view: View,
      inflater: LayoutInflater,
      mDB: SQLiteDatabase,
      onSetTimeValidator: OnSetValueListener,
  ) {
    intervalWarmupType = view.findViewById(R.id.interval_warmup_type)
    intervalWarmupTime = view.findViewById(R.id.interval_warmup_time)
    intervalWarmupTime?.setOnSetValueListener(onSetTimeValidator)
    intervalWarmupDistance = view.findViewById(R.id.interval_warmup_distance)
    intervalWarmupType?.setOnSetValueListener(
        makeIntervalPhaseTypeListener(intervalWarmupTime, intervalWarmupDistance),
    )

    intervalType = view.findViewById(R.id.interval_type)
    intervalTime = view.findViewById(R.id.start_interval_time)
    intervalTime?.setOnSetValueListener(onSetTimeValidator)
    intervalDistance = view.findViewById(R.id.interval_distance)
    intervalTypeSetValue = makeIntervalTypeSetValue(intervalTime, intervalDistance)
    intervalType?.setOnSetValueListener(intervalTypeSetValue)

    intervalRestType = view.findViewById(R.id.interval_rest_type)
    intervalRestTime = view.findViewById(R.id.interval_rest_time)
    intervalRestTime?.setOnSetValueListener(onSetTimeValidator)
    intervalRestDistance = view.findViewById(R.id.interval_rest_distance)
    intervalRestTypeSetValue = makeIntervalRestTypeSetValue(intervalRestTime, intervalRestDistance)
    intervalRestType?.setOnSetValueListener(intervalRestTypeSetValue)

    intervalCooldownType = view.findViewById(R.id.interval_cooldown_type)
    intervalCooldownTime = view.findViewById(R.id.interval_cooldown_time)
    intervalCooldownTime?.setOnSetValueListener(onSetTimeValidator)
    intervalCooldownDistance = view.findViewById(R.id.interval_cooldown_distance)
    intervalCooldownType?.setOnSetValueListener(
        makeIntervalPhaseTypeListener(intervalCooldownTime, intervalCooldownDistance),
    )

    syncIntervalPhasePickers(intervalWarmupType, intervalWarmupTime, intervalWarmupDistance)
    syncIntervalPhasePickers(intervalCooldownType, intervalCooldownTime, intervalCooldownDistance)

    intervalTargetHrz = view.findViewById(R.id.interval_target_hrz)
    configureIntervalTargetHrzSpinner(intervalTargetHrz)

    view.findViewById<TitleSpinner>(R.id.interval_hr_cue_seconds)

    intervalAudioListAdapter = AudioSchemeListAdapter(mDB, inflater, false)
    intervalAudioListAdapter?.reload()
    val intervalAudioSpinner = view.findViewById<TitleSpinner>(R.id.interval_audio_cue_spinner)
    intervalAudioSpinner.setAdapter(intervalAudioListAdapter)
    intervalAudioSpinner.setOnSetValueListener(
        StartConfigureAudioListener(fragment, intervalAudioListAdapter!!),
    )
  }

  fun reloadAudioAdapter() {
    intervalAudioListAdapter?.reload()
  }

  private fun configureIntervalTargetHrzSpinner(sp: TitleSpinner?) {
    if (sp == null) return
    val ctx = fragment.requireContext()
    val hz = HRZones(ctx)
    val count = hz.count
    val labels = ArrayList<String>()
    labels.add(fragment.getString(org.runnerup.common.R.string.Interval_no_hr_target))
    for (i in 0 until count) {
      val p: Pair<Int, Int>? = hz.getHRValues(i + 1)
      if (p != null) {
        labels.add("Zone " + (i + 1) + " (" + p.first + " - " + p.second + ")")
      }
    }
    sp.setArrayEntries(labels.toTypedArray())
    val pref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx)
    val saved = pref.getInt(fragment.getString(R.string.pref_interval_target_hrz), 0)
    if (saved >= 0 && saved < labels.size) {
      sp.setValue(saved)
    }
  }

  companion object {
    fun syncIntervalPhasePickers(
        typeSpinner: TitleSpinner?,
        timeSpinner: TitleSpinner?,
        distSpinner: TitleSpinner?,
    ) {
      if (typeSpinner == null || timeSpinner == null || distSpinner == null) return
      val v = typeSpinner.valueInt
      val time = v == DB.DIMENSION.TIME
      val dist = v == DB.DIMENSION.DISTANCE
      timeSpinner.visibility = if (time) View.VISIBLE else View.GONE
      distSpinner.visibility = if (dist) View.VISIBLE else View.GONE
    }

    fun makeIntervalPhaseTypeListener(
        timeSpinner: TitleSpinner?,
        distSpinner: TitleSpinner?,
    ): OnSetValueListener =
        object : OnSetValueListener {
          override fun preSetValue(newValue: String): String = newValue

          override fun preSetValue(newValue: Int): Int {
            val time = newValue == DB.DIMENSION.TIME
            val dist = newValue == DB.DIMENSION.DISTANCE
            timeSpinner?.visibility = if (time) View.VISIBLE else View.GONE
            distSpinner?.visibility = if (dist) View.VISIBLE else View.GONE
            return newValue
          }
        }

    private fun makeIntervalTypeSetValue(
        intervalTime: TitleSpinner?,
        intervalDistance: TitleSpinner?,
    ): OnSetValueListener =
        object : OnSetValueListener {
          override fun preSetValue(newValue: String): String = newValue

          override fun preSetValue(newValue: Int): Int {
            val time = newValue == 0
            intervalTime?.visibility = if (time) View.VISIBLE else View.GONE
            intervalDistance?.visibility = if (time) View.GONE else View.VISIBLE
            return newValue
          }
        }

    private fun makeIntervalRestTypeSetValue(
        intervalRestTime: TitleSpinner?,
        intervalRestDistance: TitleSpinner?,
    ): OnSetValueListener =
        object : OnSetValueListener {
          override fun preSetValue(newValue: String): String = newValue

          override fun preSetValue(newValue: Int): Int {
            val time = newValue == 0
            intervalRestTime?.visibility = if (time) View.VISIBLE else View.GONE
            intervalRestDistance?.visibility = if (time) View.GONE else View.VISIBLE
            return newValue
          }
        }
  }
}
