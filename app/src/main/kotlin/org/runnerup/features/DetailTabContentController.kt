/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.os.Bundle
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.runnerup.BuildConfig
import org.runnerup.R
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.SafeParse
import org.runnerup.core.util.MapWrapper
import org.runnerup.ui.common.widget.SpinnerInterface.OnSetValueListener
import org.runnerup.core.workout.Sport

/** Binds Detail tab pager content after ViewPager2 inflates tab layouts. */
internal class DetailTabContentController(
    private val activity: DetailActivity,
) {

  fun scheduleBind(savedInstanceState: Bundle?) {
    val pager = activity.detailPager ?: return
    pager.viewTreeObserver.addOnGlobalLayoutListener(
        object : ViewTreeObserver.OnGlobalLayoutListener {
          override fun onGlobalLayout() {
            if (activity.detailTabContentBound || activity.isFinishing) {
              removeListener()
              return
            }
            if (activity.findViewById<android.view.View>(R.id.summary_sport) != null) {
              removeListener()
              bind(savedInstanceState)
            }
          }

          private fun removeListener() {
            val observer = pager.viewTreeObserver
            if (observer.isAlive) {
              observer.removeOnGlobalLayoutListener(this)
            }
          }
        },
    )
  }

  fun bind(savedInstanceState: Bundle?) {
    if (activity.detailTabContentBound || activity.isFinishing) {
      return
    }
    activity.sport = activity.findViewById(R.id.summary_sport)
    activity.manualDistance = activity.findViewById(R.id.summary_manual_distance)
    activity.notes = activity.findViewById(R.id.notes_text)
    val lapList: RecyclerView? = activity.findViewById(R.id.laplist)
    val sport = activity.sport
    val manualDistance = activity.manualDistance
    val notes = activity.notes
    if (sport == null || manualDistance == null || notes == null || lapList == null) {
      return
    }
    activity.detailTabContentBound = true

    sport.setOnSetValueListener(
        object : OnSetValueListener {
          override fun preSetValue(newValue: String): String = newValue

          override fun preSetValue(newValue: Int): Int {
            activity.updateViewForSport(newValue)
            ViewCompat.requestApplyInsets(activity.rootView)
            activity.headerData.put(DB.ACTIVITY.SPORT, newValue)
            return newValue
          }
        },
    )
    sport.setArrayEntries(Sport.getStringArray(activity.resources))

    manualDistance.setOnSetValueListener(
        object : OnSetValueListener {
          override fun preSetValue(newValue: String): String {
            val dist = SafeParse.parseDouble(newValue, 0.0)
            activity.headerData.put(DB.ACTIVITY.DISTANCE, dist)
            activity.updateHeader(activity.headerData, true)
            return newValue
          }

          override fun preSetValue(newValue: Int): Int = newValue
        },
    )
    val injuryController = DetailInjuryController(activity, activity.mDB) { activity.mID }
    activity.injuryController = injuryController
    injuryController.bindViews(activity)
    activity.menuController.bind(activity.mDB, activity.mID, sport)

    if (BuildConfig.USING_OSMDROID || BuildConfig.MAPBOX_ENABLED > 0) {
      val mapView = activity.findViewById<android.view.View>(R.id.mapview)
      if (mapView != null) {
        val mapWrapper =
            MapWrapper(activity, activity.mDB, activity.mID, activity.formatter, mapView)
        activity.mapWrapper = mapWrapper
        mapWrapper.onCreate(savedInstanceState)
      }
    }

    activity.fillHeaderData()
    activity.requery()

    lapList.layoutManager = LinearLayoutManager(activity)
    activity.lapListAdapter =
        DetailLapListController.createAdapter(activity, activity.lapListHost)
    lapList.adapter = activity.lapListAdapter

    val graphTabLayout: LinearLayout? = activity.findViewById(R.id.tab_graph)
    val hrzonesBarLayout: LinearLayout? = activity.findViewById(R.id.hrzonesBarLayout)
    if (graphTabLayout != null && hrzonesBarLayout != null) {
      activity.graphController.attach(
          activity,
          graphTabLayout,
          hrzonesBarLayout,
          activity.formatter,
          activity.mDB,
          activity.mID,
          sport.valueInt,
      )
    }

    val discardButton: Button = activity.findViewById(R.id.discard_button) ?: return
    activity.saveModeController.bind(
        activity.saveModeController.mode,
        activity.saveButton,
        discardButton,
        activity.resumeButton,
        notes,
        sport,
        manualDistance,
        activity.headerData,
        activity.mDB,
        activity.mID,
        activity.rootView,
    )

    injuryController.renderIcons()
  }
}
