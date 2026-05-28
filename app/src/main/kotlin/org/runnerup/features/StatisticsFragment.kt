/*
 * Copyright (C) 2024 RunnerUp
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

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.runnerup.R
import org.runnerup.data.DBHelper

class StatisticsFragment : Fragment(R.layout.statistics) {

  private var db: SQLiteDatabase? = null

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    db = DBHelper.getWritableDatabase(requireContext())

    val recyclerView = view.findViewById<RecyclerView>(R.id.statistics_grid)
    val spacingPx = (16 * resources.displayMetrics.density).toInt()
    recyclerView.layoutManager = GridLayoutManager(requireContext(), SPAN_COUNT)
    recyclerView.addItemDecoration(GridSpacingDecoration(SPAN_COUNT, spacingPx))
    recyclerView.adapter = StatisticsCardAdapter { position -> openCategory(position) }
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(db)
  }

  private fun openCategory(position: Int) {
    val context = requireContext()
    val intent =
        when (position) {
          0 -> Intent(context, MonthlyComparisonActivity::class.java)
          1 -> Intent(context, YearlyStatsActivity::class.java)
          2 -> Intent(context, HRZoneActivity::class.java)
          3 -> Intent(context, YearlyCumulativeActivity::class.java)
          4 -> Intent(context, DistributionActivity::class.java)
          5 -> Intent(context, PaceActivity::class.java)
          6 -> Intent(context, WeeklyKmActivity::class.java)
          7 -> Intent(context, RunCalendarActivity::class.java)
          else -> return
        }
    startActivity(intent)
  }

  private class StatisticsCardAdapter(
      private val onCategoryClick: (Int) -> Unit,
  ) : RecyclerView.Adapter<StatisticsCardAdapter.CardHolder>() {

    override fun getItemCount(): Int = CATEGORY_ICONS.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder {
      val view =
          LayoutInflater.from(parent.context)
              .inflate(R.layout.statistics_card, parent, false)
      return CardHolder(view, onCategoryClick)
    }

    override fun onBindViewHolder(holder: CardHolder, position: Int) {
      holder.bind(position)
    }

    class CardHolder(
        itemView: View,
        onCategoryClick: (Int) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
      private val iconView: ImageView = itemView.findViewById(R.id.statistics_card_icon)
      private val labelView: TextView = itemView.findViewById(R.id.statistics_card_label)

      init {
        itemView.setOnClickListener {
          val position = bindingAdapterPosition
          if (position != RecyclerView.NO_POSITION) {
            onCategoryClick(position)
          }
        }
      }

      fun bind(position: Int) {
        iconView.setImageResource(CATEGORY_ICONS[position])
        labelView.setText(CATEGORY_LABELS[position])
      }
    }
  }

  /** Half-spacing on each cell edge so gap between cells equals [spacingPx]. */
  private class GridSpacingDecoration(
      private val spanCount: Int,
      private val spacingPx: Int,
  ) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
      val position = parent.getChildAdapterPosition(view)
      if (position == RecyclerView.NO_POSITION) return
      val column = position % spanCount
      val half = spacingPx / 2
      outRect.left = if (column == 0) 0 else half
      outRect.right = if (column == spanCount - 1) 0 else half
      outRect.top = if (position < spanCount) 0 else half
      outRect.bottom = half
    }
  }

  companion object {
    private const val SPAN_COUNT = 2

    private val CATEGORY_ICONS =
        intArrayOf(
            R.drawable.ic_tab_main_24dp,
            R.drawable.ic_tab_history_24dp,
            R.drawable.ic_heartrate_white_24dp,
            R.drawable.ic_tab_besttimes_24dp,
            R.drawable.ic_bell_curve_24dp,
            R.drawable.ic_shoes_24dp,
            R.drawable.ic_statistics_line_chart_24dp,
            R.drawable.ic_calendar_24dp,
        )

    private val CATEGORY_LABELS =
        intArrayOf(
            R.string.statistics_monthly_comparison,
            R.string.statistics_year_month_breakdown,
            R.string.statistics_hr_zones,
            R.string.statistics_yearly_progress,
            R.string.statistics_distribution,
            R.string.statistics_pace,
            R.string.statistics_weekly_km,
            R.string.statistics_run_calendar,
        )
  }
}
