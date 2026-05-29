/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.features

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import java.io.File
import org.runnerup.core.workout.WorkoutSerializer

class WorkoutListAdapter(private val inflater: LayoutInflater) : BaseAdapter() {

  private var workoutList: Array<String> = emptyArray()

  override fun getCount(): Int = workoutList.size + 1

  override fun getItem(position: Int): Any {
    if (position < workoutList.size) return workoutList[position]

    val context = inflater.context
    return String.format(
        context.getString(org.runnerup.common.R.string.dialog_ellipsis),
        context.getString(org.runnerup.common.R.string.Manage_workouts),
    )
  }

  override fun getItemId(position: Int): Long = 0

  override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    val view =
        convertView
            ?: inflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false)

    view.findViewById<TextView>(android.R.id.text1).text = getItem(position).toString()
    return view
  }

  fun find(name: String): Int {
    for (i in 0 until count) {
      if (name.contentEquals(getItem(i).toString())) return i
    }
    return 0
  }

  fun reload() {
    val list = load(inflater.context)
    workoutList =
        if (list == null) {
          emptyArray()
        } else {
          list.map { s -> s.substring(0, s.lastIndexOf('.')) }.toTypedArray()
        }
    notifyDataSetChanged()
  }

  companion object {
    @JvmStatic
    fun load(ctx: Context): Array<String>? {
      val f = ctx.getDir(WorkoutSerializer.WORKOUTS_DIR, 0)
      return f.list { _, filename -> filename.endsWith(".json") }
    }
  }
}
