/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.ContentValues
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.runnerup.R
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.ActivitySummaryBinder
import org.runnerup.core.util.Formatter
import org.runnerup.core.workout.Intensity
import org.runnerup.data.WorkoutStepGrouper

/** Lap list adapter and display-entry building for [DetailActivity]. */
internal object DetailLapListController {

  interface Host {
    fun labelForIntensity(intensity: Int): String

    fun getFormatter(): Formatter

    fun isLapHrPresent(): Boolean

    fun getLapDisplayEntries(): Array<WorkoutStepGrouper.LapDisplayEntry>?

    fun setLapDisplayEntries(entries: Array<WorkoutStepGrouper.LapDisplayEntry>)

    /** True when laps include recovery/rest phases (interval or structured workout). */
    fun isIntervalWorkout(): Boolean
  }

  /** Interval-tab runs record recovery or rest laps; basic runs do not. */
  @JvmStatic
  fun isIntervalWorkout(laps: Array<ContentValues>?): Boolean {
    if (laps == null) {
      return false
    }
    for (lap in laps) {
      val intensity = lap.getAsInteger(DB.LAP.INTENSITY) ?: continue
      if (intensity == DB.INTENSITY.RECOVERY || intensity == DB.INTENSITY.RESTING) {
        return true
      }
    }
    return false
  }

  @JvmStatic
  fun buildDisplayEntries(
      laps: Array<ContentValues>,
      host: Host,
  ): Array<WorkoutStepGrouper.LapDisplayEntry> =
      WorkoutStepGrouper.buildDisplayEntries(laps, host::labelForIntensity)

  @JvmStatic
  fun bindLapHeader(headerRow: View?, host: Host) {
    if (headerRow == null) return
    val hrHeader = headerRow.findViewById<TextView>(R.id.lap_list_header_hr)
    hrHeader.visibility = if (host.isLapHrPresent()) View.VISIBLE else View.GONE
  }

  @JvmStatic
  fun createAdapter(activity: DetailActivity, host: Host): LapListAdapter =
      LapListAdapter(activity, host)

  class LapListAdapter(
      private val activity: DetailActivity,
      private val host: Host,
  ) : RecyclerView.Adapter<LapListAdapter.Holder>() {

    override fun getItemViewType(position: Int): Int =
        host.getLapDisplayEntries()!![position].viewType

    override fun getItemCount(): Int {
      val entries = host.getLapDisplayEntries() ?: return 0
      return entries.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
      val inflater = LayoutInflater.from(activity)
      val layout =
          if (viewType == WorkoutStepGrouper.LapDisplayEntry.VIEW_STEP_SUMMARY) {
            R.layout.laplist_step_summary_row
          } else {
            R.layout.laplist_row
          }
      val view = inflater.inflate(layout, parent, false)
      return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
      val entry = host.getLapDisplayEntries()!![position]
      if (entry.viewType == WorkoutStepGrouper.LapDisplayEntry.VIEW_STEP_SUMMARY) {
        bindStepSummaryRow(holder, entry)
      } else {
        bindLapRow(holder, position, entry.lap)
      }
    }

    private fun bindStepSummaryRow(
        holder: Holder,
        entry: WorkoutStepGrouper.LapDisplayEntry,
    ) {
      holder.tv1.text = ""
      holder.tv0.text = entry.summaryLabel
      val formatter = host.getFormatter()
      ActivitySummaryBinder.bind(
          formatter,
          holder.tv2,
          holder.tv3,
          holder.tv4,
          Formatter.Format.TXT_LONG,
          Formatter.Format.TXT_LONG,
          entry.summaryDistance,
          entry.summaryTime,
      )
      bindLapHeartRate(
          formatter,
          holder.tvHr,
          entry.summaryAvgHr,
          entry.summaryMaxHr,
          host.isLapHrPresent(),
      )
    }

    private fun bindLapRow(holder: Holder, position: Int, lap: ContentValues) {
      val i = lap.getAsInteger(DB.LAP.INTENSITY)
      val intensity = Intensity.values()[i]
      holder.tv0.text =
          when (intensity) {
            Intensity.ACTIVE ->
                if (host.isIntervalWorkout()) {
                  lapIntensityAbbrev(activity, intensity)
                } else {
                  ""
                }
            Intensity.COOLDOWN,
            Intensity.RESTING,
            Intensity.RECOVERY,
            Intensity.WARMUP,
            Intensity.REPEAT -> lapIntensityAbbrev(activity, intensity)
            else -> ""
          }
      if (intensity == Intensity.ACTIVE) {
        var activeIdx = 0
        val entries = host.getLapDisplayEntries()!!
        for (j in 0..position) {
          if (entries[j].viewType != WorkoutStepGrouper.LapDisplayEntry.VIEW_LAP) {
            continue
          }
          val ji = entries[j].lap.getAsInteger(DB.LAP.INTENSITY)
          if (ji != null && ji == DB.INTENSITY.ACTIVE) {
            activeIdx++
          }
        }
        holder.tv1.text = activeIdx.toString()
      } else {
        holder.tv1.text = ""
      }
      val d = if (lap.containsKey(DB.LAP.DISTANCE)) lap.getAsDouble(DB.LAP.DISTANCE) else 0.0
      val t = if (lap.containsKey(DB.LAP.TIME)) lap.getAsLong(DB.LAP.TIME) else 0L
      val formatter = host.getFormatter()
      ActivitySummaryBinder.bind(
          formatter,
          holder.tv2,
          holder.tv3,
          holder.tv4,
          Formatter.Format.TXT_LONG,
          Formatter.Format.TXT_LONG,
          d,
          t,
      )
      val avgHr = if (lap.containsKey(DB.LAP.AVG_HR)) lap.getAsInteger(DB.LAP.AVG_HR) ?: 0 else 0
      val maxHr = if (lap.containsKey(DB.LAP.MAX_HR)) lap.getAsInteger(DB.LAP.MAX_HR) ?: 0 else 0
      bindLapHeartRate(formatter, holder.tvHr, avgHr, maxHr, host.isLapHrPresent())
    }

    private fun lapIntensityAbbrev(activity: DetailActivity, intensity: Intensity): String {
      val res = activity.resources
      return when (intensity) {
        Intensity.WARMUP -> res.getString(R.string.lap_list_intensity_warmup)
        Intensity.ACTIVE -> res.getString(R.string.lap_list_intensity_interval)
        Intensity.RECOVERY -> res.getString(R.string.lap_list_intensity_recovery)
        Intensity.COOLDOWN -> res.getString(R.string.lap_list_intensity_cooling)
        Intensity.RESTING -> res.getString(R.string.lap_list_intensity_rest)
        Intensity.REPEAT -> res.getString(R.string.lap_list_intensity_repeat)
        else -> res.getString(intensity.textId)
      }
    }

    private fun bindLapHeartRate(
        formatter: Formatter,
        tvHr: TextView,
        avgHr: Int,
        maxHr: Int,
        hrColumnPresent: Boolean,
    ) {
      val avg = if (avgHr > 0) avgHr else null
      val max = if (maxHr > 0) maxHr else null
      if (avg != null || max != null) {
        tvHr.visibility = View.VISIBLE
        tvHr.text = formatter.formatBestTimesHeartRateLine(avg, max)
      } else if (hrColumnPresent) {
        tvHr.visibility = View.INVISIBLE
      } else {
        tvHr.visibility = View.GONE
      }
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      val tv0: TextView = itemView.findViewById(R.id.lap_list_type)
      val tv1: TextView = itemView.findViewById(R.id.lap_list_id)
      val tv2: TextView = itemView.findViewById(R.id.lap_list_distance)
      val tv3: TextView = itemView.findViewById(R.id.lap_list_time)
      val tv4: TextView = itemView.findViewById(R.id.lap_list_pace)
      val tvHr: TextView = itemView.findViewById(R.id.lap_list_hr)
    }
  }
}
