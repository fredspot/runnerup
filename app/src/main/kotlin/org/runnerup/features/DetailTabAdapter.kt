/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/** ViewPager2 pages for {@link DetailActivity} (overview, laps, map, graph). */
class DetailTabAdapter(private val pageLayouts: IntArray) :
    RecyclerView.Adapter<DetailTabAdapter.PageViewHolder>() {

  class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

  override fun getItemCount(): Int = pageLayouts.size

  override fun getItemViewType(position: Int): Int = position

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
    val view =
        LayoutInflater.from(parent.context).inflate(pageLayouts[viewType], parent, false)
    view.layoutParams =
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    return PageViewHolder(view)
  }

  override fun onBindViewHolder(holder: PageViewHolder, position: Int) {}
}
