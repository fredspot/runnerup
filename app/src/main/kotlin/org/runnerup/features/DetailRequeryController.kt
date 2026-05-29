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
import android.database.Cursor
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.Bitfield
import org.runnerup.data.DBHelper
import org.runnerup.sync.Synchronizer
import org.runnerup.sync.Synchronizer.Feature

/** Reloads lap list and sync account state for [DetailActivity]. */
internal class DetailRequeryController(private val activity: DetailActivity) {

  fun requery() {
    loadLaps()
    loadReports()
    if (activity.saveModeController.mode == DetailSaveModeController.MODE_DETAILS) {
      activity.setUploadVisibility()
    }
    activity.lapListAdapter?.notifyDataSetChanged()
    DetailLapListController.bindLapHeader(
        activity.findViewById(org.runnerup.R.id.lap_list_header),
        activity.lapListHost,
    )
  }

  private fun loadLaps() {
    val from =
        arrayOf(
            "_id",
            DB.LAP.LAP,
            DB.LAP.INTENSITY,
            DB.LAP.TIME,
            DB.LAP.DISTANCE,
            DB.LAP.PLANNED_TIME,
            DB.LAP.PLANNED_DISTANCE,
            DB.LAP.PLANNED_PACE,
            DB.LAP.AVG_HR,
            DB.LAP.MAX_HR,
            DB.LAP.STEP,
        )
    activity.mDB.query(DB.LAP.TABLE, from, DB.LAP.ACTIVITY + " == " + activity.mID, null, null, null, "_id", null)
        .use { c ->
          activity.laps = DBHelper.toArray(c)
        }
    val laps = activity.laps ?: emptyArray()
    activity.intervalWorkout = DetailLapListController.isIntervalWorkout(laps)
    activity.lapHrPresent = false
    for (v in laps) {
      if (v.containsKey(DB.LAP.AVG_HR) && v.getAsInteger(DB.LAP.AVG_HR) > 0) {
        activity.lapHrPresent = true
        break
      }
      if (v.containsKey(DB.LAP.MAX_HR) && v.getAsInteger(DB.LAP.MAX_HR) > 0) {
        activity.lapHrPresent = true
        break
      }
    }
    activity.lapDisplayEntries =
        DetailLapListController.buildDisplayEntries(laps, activity.lapListHost)
  }

  private fun loadReports() {
    val sql =
        "SELECT DISTINCT " +
            "  acc._id, " +
            ("  acc." + DB.ACCOUNT.NAME + ", ") +
            ("  acc." + DB.ACCOUNT.FLAGS + ", ") +
            ("  acc." + DB.ACCOUNT.AUTH_CONFIG + ", ") +
            ("  acc." + DB.ACCOUNT.FORMAT + ", ") +
            ("  rep._id as repid, ") +
            ("  rep." + DB.EXPORT.ACCOUNT + ", ") +
            ("  rep." + DB.EXPORT.ACTIVITY + ", ") +
            ("  rep." + DB.EXPORT.EXTERNAL_ID + ", ") +
            ("  rep." + DB.EXPORT.STATUS) +
            (" FROM " + DB.ACCOUNT.TABLE + " acc ") +
            (" LEFT OUTER JOIN " + DB.EXPORT.TABLE + " rep ") +
            (" ON ( acc._id = rep." + DB.EXPORT.ACCOUNT) +
            ("     AND rep." + DB.EXPORT.ACTIVITY + " = " + activity.mID + " )")

    activity.mDB.rawQuery(sql, null).use { c ->
      activity.syncController.alreadySynched.clear()
      activity.syncController.synchedExternalId.clear()
      activity.syncController.pendingSynchronizers.clear()
      activity.reports.clear()
      if (c.moveToFirst()) {
        do {
          val tmp = DBHelper.get(c)
          val synchronizer = activity.syncManager.add(tmp) ?: continue
          if (!synchronizer.checkSupport(Feature.UPLOAD) || !synchronizer.isConfigured()) {
            continue
          }
          val name = tmp.getAsString(DB.ACCOUNT.NAME)
          activity.reports.add(tmp)
          if (tmp.containsKey("repid")) {
            activity.syncController.alreadySynched.add(name)
            if (tmp.containsKey(DB.EXPORT.STATUS) &&
                tmp.getAsInteger(DB.EXPORT.STATUS) ==
                    Synchronizer.ExternalIdStatus.getInt(Synchronizer.ExternalIdStatus.OK)) {
              val url =
                  activity.syncManager
                      .getSynchronizerByName(name)
                      ?.getActivityUrl(activity.syncController.synchedExternalId[name])
              if (url != null) {
                activity.syncController.synchedExternalId[name] =
                    tmp.getAsString(DB.EXPORT.EXTERNAL_ID)
              }
            }
          } else if (tmp.containsKey(DB.ACCOUNT.FLAGS) &&
              Bitfield.test(tmp.getAsLong(DB.ACCOUNT.FLAGS), DB.ACCOUNT.FLAG_UPLOAD)) {
            activity.syncController.pendingSynchronizers.add(name)
          }
        } while (c.moveToNext())
      }
    }
  }
}
