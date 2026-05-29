/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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

package org.runnerup.ui.common.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

internal class DisabledEntriesAdapter(ctx: Context, id: Int) : BaseAdapter() {
  private val entries: Array<String>
  private val inflator: LayoutInflater =
      ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
  private var disabled: HashSet<String>? = null

  init {
    entries = ctx.resources.getStringArray(id)
  }

  fun addDisabled(i: Int) {
    if (disabled == null) disabled = HashSet()
    if (i < entries.size) disabled!!.add(entries[i])
  }

  fun clearDisabled() {
    disabled?.clear()
  }

  override fun getCount(): Int = entries.size

  override fun getItem(position: Int): Any? =
      if (position < entries.size) entries[position] else null

  override fun getItemId(position: Int): Long = 0

  override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    val str = getItem(position) as String?
    var view = convertView
    if (view == null) {
      view =
          inflator.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false)
    }
    val ret = view!!.findViewById<TextView>(android.R.id.text1)
    ret.text = str

    view.isEnabled = disabled == null || !disabled!!.contains(str)

    return view
  }

  override fun areAllItemsEnabled(): Boolean = disabled == null || disabled!!.size == 0

  override fun isEnabled(position: Int): Boolean {
    if (disabled == null) return true

    val str = getItem(position) as String? ?: return true

    return !disabled!!.contains(str)
  }
}
