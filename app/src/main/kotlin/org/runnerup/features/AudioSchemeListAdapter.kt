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

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import org.runnerup.common.util.Constants.DB

class AudioSchemeListAdapter(
    private val mDB: SQLiteDatabase,
    private val inflater: LayoutInflater,
    private val createNewItem: Boolean,
) : BaseAdapter() {

  private val audioSchemes = ArrayList<String>()

  override fun getCount(): Int = audioSchemes.size + 2

  override fun getItem(position: Int): Any {
    if (position == 0) {
      return inflater.context.getString(org.runnerup.common.R.string.Default)
    }

    var pos = position - 1

    if (pos < audioSchemes.size) return audioSchemes[pos]

    val context = inflater.context
    return if (createNewItem) {
      context.getString(org.runnerup.common.R.string.New_audio_scheme)
    } else {
      String.format(
          context.getString(org.runnerup.common.R.string.dialog_ellipsis),
          context.getString(org.runnerup.common.R.string.Manage_audio_cues),
      )
    }
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
    audioSchemes.clear()
    try {
      val from = arrayOf(DB.AUDIO_SCHEMES.NAME)
      mDB
          .query(
              DB.AUDIO_SCHEMES.TABLE,
              from,
              null,
              null,
              null,
              null,
              DB.AUDIO_SCHEMES.SORT_ORDER + " desc",
          )
          .use { c ->
            if (c.moveToFirst()) {
              do {
                audioSchemes.add(c.getString(0))
              } while (c.moveToNext())
            }
          }
    } catch (ex: IllegalStateException) {
      Log.e(javaClass.name, "Query failed:", ex)
    }
    notifyDataSetChanged()
  }
}
