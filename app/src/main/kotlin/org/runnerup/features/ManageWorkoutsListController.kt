/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.runnerup.R
import org.runnerup.core.util.CardPressHelper
import org.runnerup.sync.SyncManager.WorkoutRef
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet

/** Sectioned workout list (account header + workout rows) replacing ExpandableListView. */
class ManageWorkoutsListController(
    recyclerView: RecyclerView,
    private val onHeaderClick: (String, Boolean) -> Unit,
    private val onWorkoutLongPress: (WorkoutRef) -> Unit,
) {
  private val expandedProviders = HashSet<String>()
  private val providers = ArrayList<ContentValues>()
  private val workouts = HashMap<String, ArrayList<WorkoutRef>>()
  private val rows = ArrayList<ListRow>()

  private val adapter = WorkoutListAdapter()

  init {
    recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
    recyclerView.adapter = adapter
    CardPressHelper.prepareRowHost(recyclerView)
  }

  fun isProviderExpanded(provider: String): Boolean = expandedProviders.contains(provider)

  fun setProvidersAndWorkouts(
      newProviders: ArrayList<ContentValues>,
      newWorkouts: HashMap<String, ArrayList<WorkoutRef>>,
  ) {
    providers.clear()
    providers.addAll(newProviders)
    workouts.clear()
    workouts.putAll(newWorkouts)
    rebuildRows()
    adapter.notifyDataSetChanged()
  }

  fun expandProvider(provider: String) {
    expandedProviders.add(provider)
    rebuildRows()
    adapter.notifyDataSetChanged()
  }

  fun collapseProvider(provider: String) {
    expandedProviders.remove(provider)
    rebuildRows()
    adapter.notifyDataSetChanged()
  }

  fun expandFirstGroupIfNeeded() {
    if (providers.isEmpty()) return
    val name = providers[0].getAsString(org.runnerup.common.util.Constants.DB.ACCOUNT.NAME) ?: return
    expandProvider(name)
  }

  fun notifyChanged() {
    rebuildRows()
    adapter.notifyDataSetChanged()
  }

  private fun rebuildRows() {
    rows.clear()
    for (i in providers.indices) {
      val name =
          providers[i].getAsString(org.runnerup.common.util.Constants.DB.ACCOUNT.NAME) ?: continue
      val expanded = expandedProviders.contains(name)
      rows.add(ListRow.Header(name, expanded))
      if (expanded) {
        for (ref in workouts[name].orEmpty()) {
          rows.add(ListRow.Workout(ref, name))
        }
      }
    }
  }

  private sealed class ListRow {
    data class Header(val provider: String, val expanded: Boolean) : ListRow()

    data class Workout(val ref: WorkoutRef, val provider: String) : ListRow()
  }

  private inner class WorkoutListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun getItemViewType(position: Int): Int =
        when (rows[position]) {
          is ListRow.Header -> VIEW_HEADER
          is ListRow.Workout -> VIEW_WORKOUT
        }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
      val inflater = LayoutInflater.from(parent.context)
      return when (viewType) {
        VIEW_HEADER -> {
          HeaderHolder(inflater.inflate(R.layout.manage_workouts_list_category, parent, false))
        }
        else -> {
          val row = inflater.inflate(R.layout.manage_workouts_list_row, parent, false)
          val card = row.findViewById<View>(R.id.manage_workout_row_card)
          CardPressHelper.prepareCard(card)
          WorkoutHolder(row)
        }
      }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
      when (val row = rows[position]) {
        is ListRow.Header -> (holder as HeaderHolder).bind(row)
        is ListRow.Workout -> (holder as WorkoutHolder).bind(row)
      }
    }

    inner class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      private val categoryText: TextView = itemView.findViewById(R.id.category_text)

      fun bind(row: ListRow.Header) {
        categoryText.text = row.provider
        val icon =
            if (row.expanded) R.drawable.ic_expand_up_white_24dp
            else R.drawable.ic_expand_down_white_24dp
        categoryText.setCompoundDrawablesWithIntrinsicBounds(0, 0, icon, 0)
        itemView.setOnClickListener { onHeaderClick(row.provider, row.expanded) }
      }
    }

    inner class WorkoutHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      private val card: View = itemView.findViewById(R.id.manage_workout_row_card)
      private val title: TextView = itemView.findViewById(R.id.manage_workout_row_title)

      fun bind(row: ListRow.Workout) {
        title.text = row.ref.workoutName
        card.setOnClickListener(null)
        card.isClickable = true
        WorkoutEditorLongPress.bind(card, Runnable { onWorkoutLongPress(row.ref) })
      }
    }
  }

  companion object {
    private const val VIEW_HEADER = 0
    private const val VIEW_WORKOUT = 1
  }
}
