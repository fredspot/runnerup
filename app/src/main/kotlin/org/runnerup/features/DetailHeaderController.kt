/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.ContentValues
import org.runnerup.common.util.Constants.DB
import org.runnerup.data.DBHelper

/** Loads activity header fields and binds summary widgets for [DetailActivity]. */
internal class DetailHeaderController(private val activity: DetailActivity) {

  fun fillHeaderData() {
    val from =
        arrayOf(
            DB.ACTIVITY.START_TIME,
            DB.ACTIVITY.DISTANCE,
            DB.ACTIVITY.TIME,
            DB.ACTIVITY.COMMENT,
            DB.ACTIVITY.SPORT,
            DB.ACTIVITY.ELEVATION_GAIN,
            DB.ACTIVITY.ELEVATION_LOSS,
            DB.ACTIVITY.STEPS,
        )
    activity.mDB.query(DB.ACTIVITY.TABLE, from, "_id == " + activity.mID, null, null, null, null, null)
        .use { c ->
          c.moveToFirst()
          val tmp = DBHelper.get(c)
          if (tmp.containsKey(DB.ACTIVITY.START_TIME)) {
            val st = tmp.getAsLong(DB.ACTIVITY.START_TIME)
            activity.mStartTime = st
            activity.menuController.setStartTime(st)
            activity.title = activity.formatter.formatDateTime(st)
          }
          if (tmp.containsKey(DB.ACTIVITY.COMMENT)) {
            activity.notes?.setText(tmp.getAsString(DB.ACTIVITY.COMMENT))
          }
          activity.headerData = tmp
          updateHeader(tmp, fromManualDistance = false)
          bindElevation(tmp)
          bindSteps(tmp)
        }
  }

  fun bindElevation(data: ContentValues) {
    val view = activity.findViewById<android.widget.TextView>(org.runnerup.R.id.elevation_summary) ?: return
    val gain = data.getAsDouble(DB.ACTIVITY.ELEVATION_GAIN)
    val loss = data.getAsDouble(DB.ACTIVITY.ELEVATION_LOSS)
    if (gain == null && loss == null) {
      view.visibility = android.view.View.GONE
      return
    }
    view.visibility = android.view.View.VISIBLE
    view.text =
        activity.getString(
            org.runnerup.R.string.elevation_gain_loss,
            activity.formatter.formatElevation(
                org.runnerup.core.util.Formatter.Format.TXT_SHORT,
                gain ?: 0.0,
            ),
            activity.formatter.formatElevation(
                org.runnerup.core.util.Formatter.Format.TXT_SHORT,
                loss ?: 0.0,
            ),
        )
  }

  fun bindSteps(data: ContentValues) {
    val view = activity.activitySteps
    val separator = activity.activityStepsSeparator
    val steps = data.getAsInteger(DB.ACTIVITY.STEPS)
    if (steps == null || steps <= 0) {
      view.visibility = android.view.View.GONE
      separator.visibility = android.view.View.GONE
      return
    }
    view.visibility = android.view.View.VISIBLE
    separator.visibility = android.view.View.VISIBLE
    view.text = activity.getString(org.runnerup.R.string.steps_count, steps)
  }

  fun updateHeader(data: ContentValues, fromManualDistance: Boolean) {
    val manualDistance = activity.manualDistance
    val sport = activity.sport
    DetailHeaderBinder.bind(
        activity.formatter,
        activity.activityDistance,
        activity.activityTime,
        activity.activityPace,
        activity.activityPaceSeparator,
        manualDistance,
        sport,
        data,
        fromManualDistance,
    )
  }
}
