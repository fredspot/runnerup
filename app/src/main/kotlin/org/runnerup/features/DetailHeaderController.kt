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
        }
  }

  fun updateHeader(data: ContentValues, fromManualDistance: Boolean) {
    val manualDistance = activity.manualDistance ?: return
    val sport = activity.sport ?: return
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
