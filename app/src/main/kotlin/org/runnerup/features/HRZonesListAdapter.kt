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
import org.runnerup.core.util.HRZones

class HRZonesListAdapter(ctx: Context, private val inflater: LayoutInflater) : BaseAdapter() {

  @JvmField val hrZones = HRZones(ctx)

  private var lastString: String? = null
  private var lastPosition = -1

  override fun getCount(): Int = hrZones.count

  override fun getItem(position: Int): Any? {
    if (position == lastPosition) return lastString

    if (position < hrZones.count) {
      val values = hrZones.getHRValues(position + 1) ?: return null
      val str = "Zone ${position + 1} (${values.first} - ${values.second})"

      lastPosition = position
      lastString = str

      return str
    }

    return null
  }

  override fun getItemId(position: Int): Long = 0

  override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    val view =
        convertView
            ?: inflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false)

    val textView = view.findViewById<TextView>(android.R.id.text1)
    val obj = getItem(position)
    textView.text = obj?.toString() ?: "???"

    return view
  }

  fun reload() {
    lastPosition = -1
    hrZones.reload()
  }
}
