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
import org.runnerup.analytics.ComparableSegmentLoader
import org.runnerup.analytics.DailyWeatherStore
import org.runnerup.analytics.OpenMeteoWeatherClient
import org.runnerup.analytics.RunConditions
import org.runnerup.analytics.RunConditionsAdjuster
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.BgTasks
import org.runnerup.core.util.Formatter
import org.runnerup.data.DBHelper

/** Loads activity header fields and binds summary widgets for [DetailActivity]. */
internal class DetailHeaderController(private val activity: DetailActivity) {

  private var weatherFetchStarted = false

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
          if (!c.moveToFirst()) return
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
    val startTimeSec = data.getAsLong(DB.ACTIVITY.START_TIME) ?: 0L
    val id = activity.mID
    BgTasks.runDb(
        {
          ComparableSegmentLoader.loadOne(activity.mDB, id)
        },
        { cond ->
          if (activity.isFinishing) return@runDb
          val parts = ArrayList<String>()
          if (gain != null || loss != null || cond.elevationGainM > 0) {
            parts.add(
                activity.getString(
                    org.runnerup.R.string.elevation_gain_loss,
                    activity.formatter.formatElevation(
                        Formatter.Format.TXT_SHORT,
                        gain ?: cond.elevationGainM,
                    ),
                    activity.formatter.formatElevation(
                        Formatter.Format.TXT_SHORT,
                        loss ?: 0.0,
                    ),
                ),
            )
          }
          if (RunConditionsAdjuster.hasAltitude(cond.avgElevationM)) {
            parts.add(
                activity.getString(
                    org.runnerup.R.string.header_avg_elevation,
                    activity.formatter.formatElevation(
                        Formatter.Format.TXT_SHORT,
                        cond.avgElevationM!!,
                    ),
                ),
            )
          }
          val weatherPart = formatHeaderWeather(cond)
          if (weatherPart != null) {
            parts.add(weatherPart)
          } else if (!weatherFetchStarted && startTimeSec > 0L) {
            weatherFetchStarted = true
            BgTasks.runNetwork(
                {
                  val wdb = DBHelper.getWritableDatabase(activity)
                  DailyWeatherStore.ensureForActivity(
                      wdb,
                      id,
                      startTimeSec,
                      OpenMeteoWeatherClient(),
                  ) != null
                },
                { fetched ->
                  if (fetched == true && !activity.isFinishing) {
                    bindElevation(data)
                  }
                },
            )
          }
          if (parts.isEmpty()) {
            view.visibility = android.view.View.GONE
          } else {
            view.visibility = android.view.View.VISIBLE
            view.text = parts.joinToString("  ·  ")
          }
        },
    )
  }

  private fun formatHeaderWeather(cond: RunConditions): String? {
    val hasTemp = RunConditionsAdjuster.hasTemperature(cond.avgTempC)
    val hasHum = RunConditionsAdjuster.hasHumidity(cond.avgHumidityPct)
    if (!hasTemp && !hasHum) return null
    val tStr = if (hasTemp) "${kotlin.math.round(cond.avgTempC!!).toInt()}°C" else null
    val hStr = if (hasHum) "${kotlin.math.round(cond.avgHumidityPct!!).toInt()}% RH" else null
    return listOfNotNull(tStr, hStr).joinToString(" · ")
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
