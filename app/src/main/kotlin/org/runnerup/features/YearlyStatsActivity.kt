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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.core.util.CardPressHelper
import org.runnerup.core.util.Formatter
import org.runnerup.data.DBHelper
import org.runnerup.data.entities.YearlyStatsEntity

class YearlyStatsActivity : AppCompatActivity(), Constants {

  private fun interface OnItemClickListener {
    fun onItemClick(stats: YearlyStatsEntity)
  }

  private var mDB: SQLiteDatabase? = null
  private lateinit var adapter: YearlyStatsListAdapter
  private val yearlyStats = mutableListOf<YearlyStatsEntity>()
  private lateinit var formatter: Formatter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.statistics_detail)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      title = getString(R.string.statistics_year_month_breakdown)
    }

    mDB = DBHelper.getReadableDatabase(this)
    formatter = Formatter(this)

    val recyclerView = findViewById<RecyclerView>(R.id.statistics_detail_list)
    recyclerView.layoutManager = LinearLayoutManager(this)
    val spacingPx = (16 * resources.displayMetrics.density).toInt()
    recyclerView.addItemDecoration(CardSpacingDecoration(spacingPx))
    adapter = YearlyStatsListAdapter()
    recyclerView.adapter = adapter
    adapter.setOnItemClickListener { stats ->
      val intent = Intent(this, StatisticsDetailActivity::class.java)
      intent.putExtra("YEAR", stats.year)
      startActivity(intent)
    }

    val rootView = findViewById<View>(R.id.statistics_detail_layout)
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
      val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
      val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
      mlp.topMargin = insets.top
      WindowInsetsCompat.CONSUMED
    }

    loadYears()
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(mDB)
  }

  override fun onSupportNavigateUp(): Boolean {
    onBackPressed()
    return true
  }

  private fun loadYears() {
    yearlyStats.clear()
    val sql =
        "SELECT * FROM " +
            Constants.DB.YEARLY_STATS.TABLE +
            " ORDER BY " +
            Constants.DB.YEARLY_STATS.YEAR +
            " DESC"

    mDB!!.rawQuery(sql, null).use { cursor ->
      while (cursor.moveToNext()) {
        yearlyStats.add(YearlyStatsEntity(cursor))
      }
    }
    adapter.setItems(yearlyStats)
  }

  private class CardSpacingDecoration(private val spacingPx: Int) :
      RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
      val position = parent.getChildAdapterPosition(view)
      if (position == RecyclerView.NO_POSITION) return
      if (position < state.itemCount - 1) {
        outRect.bottom = spacingPx
      }
    }
  }

  private inner class YearlyStatsListAdapter :
      RecyclerView.Adapter<YearlyStatsListAdapter.Holder>() {
    private val items = mutableListOf<YearlyStatsEntity>()
    private var onItemClickListener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener?) {
      onItemClickListener = listener
    }

    fun setItems(newItems: List<YearlyStatsEntity>) {
      items.clear()
      items.addAll(newItems)
      notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
      val inflater = LayoutInflater.from(this@YearlyStatsActivity)
      val view = inflater.inflate(R.layout.statistics_row, parent, false)
      return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
      CardPressHelper.clearPressState(holder.itemView)
      val stats = items[position]
      holder.yearText.text = stats.year.toString()
      if (stats.totalDistance != null && stats.avgPace != null && stats.runCount != null) {
        val distanceStr =
            formatter.formatDistance(
                Formatter.Format.TXT_SHORT, stats.totalDistance!!.toLong())
        val paceStr =
            formatter.formatPaceFromSecPerKm(Formatter.Format.TXT_SHORT, stats.avgPace!!)
        val summary = "$distanceStr • $paceStr • ${stats.runCount} runs"
        holder.statsSummaryText.text = summary
      } else {
        holder.statsSummaryText.text = ""
      }
      holder.itemView.setOnClickListener {
        onItemClickListener?.onItemClick(stats)
      }
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      val yearText: TextView = itemView.findViewById(R.id.year_text)
      val statsSummaryText: TextView = itemView.findViewById(R.id.stats_summary_text)
    }
  }
}
