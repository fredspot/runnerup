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
import android.widget.BaseAdapter
import android.widget.TextView
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
  }

  @JvmStatic
  fun buildDisplayEntries(
      laps: Array<ContentValues>,
      host: Host,
  ): Array<WorkoutStepGrouper.LapDisplayEntry> =
      WorkoutStepGrouper.buildDisplayEntries(laps, host::labelForIntensity)

  @JvmStatic
  fun createAdapter(activity: DetailActivity, host: Host): BaseAdapter =
      LapListAdapter(activity, host)

  private class ViewHolderLapList {
    lateinit var tv0: TextView
    lateinit var tv1: TextView
    lateinit var tv2: TextView
    lateinit var tv3: TextView
    lateinit var tv4: TextView
    lateinit var tvHr: TextView
  }

  private class LapListAdapter(
      private val activity: DetailActivity,
      private val host: Host,
  ) : BaseAdapter() {

    override fun getViewTypeCount(): Int = 2

    override fun getItemViewType(position: Int): Int =
        host.getLapDisplayEntries()!![position].viewType

    override fun getCount(): Int {
      val entries = host.getLapDisplayEntries() ?: return 0
      return entries.size
    }

    override fun getItem(position: Int): Any {
      val entry = host.getLapDisplayEntries()!![position]
      return if (entry.viewType == WorkoutStepGrouper.LapDisplayEntry.VIEW_LAP) entry.lap else entry
    }

    override fun getItemId(position: Int): Long {
      val entry = host.getLapDisplayEntries()!![position]
      if (entry.viewType == WorkoutStepGrouper.LapDisplayEntry.VIEW_LAP && entry.lap != null) {
        return entry.lap.getAsLong("_id")
      }
      return -position - 1L
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      val entry = host.getLapDisplayEntries()!![position]
      return if (entry.viewType == WorkoutStepGrouper.LapDisplayEntry.VIEW_STEP_SUMMARY) {
        bindStepSummaryRow(position, convertView, parent, entry)
      } else {
        bindLapRow(position, convertView, parent, entry.lap)
      }
    }

    private fun bindStepSummaryRow(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
        entry: WorkoutStepGrouper.LapDisplayEntry,
    ): View {
      var view = convertView
      val viewHolder: ViewHolderLapList
      if (view == null ||
          getItemViewType(position) != WorkoutStepGrouper.LapDisplayEntry.VIEW_STEP_SUMMARY) {
        viewHolder = ViewHolderLapList()
        val inflater = LayoutInflater.from(activity)
        view = inflater.inflate(R.layout.laplist_step_summary_row, parent, false)
        viewHolder.tv0 = view.findViewById(R.id.lap_list_type)
        viewHolder.tv1 = view.findViewById(R.id.lap_list_id)
        viewHolder.tv2 = view.findViewById(R.id.lap_list_distance)
        viewHolder.tv3 = view.findViewById(R.id.lap_list_time)
        viewHolder.tv4 = view.findViewById(R.id.lap_list_pace)
        viewHolder.tvHr = view.findViewById(R.id.lap_list_hr)
        view.tag = viewHolder
      } else {
        viewHolder = view.tag as ViewHolderLapList
      }
      viewHolder.tv1.text = ""
      viewHolder.tv0.text = entry.summaryLabel
      val formatter = host.getFormatter()
      ActivitySummaryBinder.bind(
          formatter,
          viewHolder.tv2,
          viewHolder.tv3,
          viewHolder.tv4,
          Formatter.Format.TXT_LONG,
          Formatter.Format.TXT_LONG,
          entry.summaryDistance,
          entry.summaryTime,
      )
      if (entry.summaryAvgHr > 0) {
        viewHolder.tvHr.visibility = View.VISIBLE
        viewHolder.tvHr.text =
            formatter.formatHeartRate(Formatter.Format.TXT_LONG, entry.summaryAvgHr.toDouble())
      } else if (host.isLapHrPresent()) {
        viewHolder.tvHr.visibility = View.INVISIBLE
      } else {
        viewHolder.tvHr.visibility = View.GONE
      }
      return view
    }

    private fun bindLapRow(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
        lap: ContentValues,
    ): View {
      var view = convertView
      val viewHolder: ViewHolderLapList
      if (view == null || getItemViewType(position) != WorkoutStepGrouper.LapDisplayEntry.VIEW_LAP) {
        viewHolder = ViewHolderLapList()
        val inflater = LayoutInflater.from(activity)
        view = inflater.inflate(R.layout.laplist_row, parent, false)
        viewHolder.tv0 = view.findViewById(R.id.lap_list_type)
        viewHolder.tv1 = view.findViewById(R.id.lap_list_id)
        viewHolder.tv2 = view.findViewById(R.id.lap_list_distance)
        viewHolder.tv3 = view.findViewById(R.id.lap_list_time)
        viewHolder.tv4 = view.findViewById(R.id.lap_list_pace)
        viewHolder.tvHr = view.findViewById(R.id.lap_list_hr)
        view.tag = viewHolder
      } else {
        viewHolder = view.tag as ViewHolderLapList
      }
      val i = lap.getAsInteger(DB.LAP.INTENSITY)
      val intensity = Intensity.values()[i]
      when (intensity) {
        Intensity.ACTIVE -> viewHolder.tv0.text = ""
        Intensity.COOLDOWN,
        Intensity.RESTING,
        Intensity.RECOVERY,
        Intensity.WARMUP,
        Intensity.REPEAT -> {
          val lapTypeLabel =
              when (intensity) {
                Intensity.RECOVERY -> "recover"
                Intensity.WARMUP -> "warmup"
                Intensity.COOLDOWN -> "cooling"
                Intensity.RESTING -> "rest"
                Intensity.REPEAT -> "repeat"
                else -> activity.resources.getString(intensity.textId)
              }
          viewHolder.tv0.text = lapTypeLabel
        }
        else -> {}
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
        viewHolder.tv1.text = activeIdx.toString()
      } else {
        viewHolder.tv1.text = ""
      }
      val d = if (lap.containsKey(DB.LAP.DISTANCE)) lap.getAsDouble(DB.LAP.DISTANCE) else 0.0
      val t = if (lap.containsKey(DB.LAP.TIME)) lap.getAsLong(DB.LAP.TIME) else 0L
      val formatter = host.getFormatter()
      ActivitySummaryBinder.bind(
          formatter,
          viewHolder.tv2,
          viewHolder.tv3,
          viewHolder.tv4,
          Formatter.Format.TXT_LONG,
          Formatter.Format.TXT_LONG,
          d,
          t,
      )
      val hr = if (lap.containsKey(DB.LAP.AVG_HR)) lap.getAsInteger(DB.LAP.AVG_HR) else 0
      if (hr > 0) {
        viewHolder.tvHr.visibility = View.VISIBLE
        viewHolder.tvHr.text = formatter.formatHeartRate(Formatter.Format.TXT_LONG, hr.toDouble())
      } else if (host.isLapHrPresent()) {
        viewHolder.tvHr.visibility = View.INVISIBLE
      } else {
        viewHolder.tvHr.visibility = View.GONE
      }
      return view
    }
  }
}
