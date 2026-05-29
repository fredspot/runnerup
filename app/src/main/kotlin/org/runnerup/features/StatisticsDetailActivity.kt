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
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.Insets
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.core.util.CardPressHelper
import org.runnerup.core.util.Formatter
import org.runnerup.data.DBHelper
import org.runnerup.data.entities.MonthlyStatsEntity

class StatisticsDetailActivity : AppCompatActivity(), Constants {

  private fun interface OnItemClickListener {
    fun onItemClick(stats: MonthlyStatsEntity?)
  }

  private lateinit var monthWeekLauncher: ActivityResultLauncher<Intent>

  private var targetYear = 0
  private var mDB: SQLiteDatabase? = null
  private lateinit var formatter: Formatter
  private lateinit var adapter: StatisticsListAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.statistics_detail)

    targetYear = intent.getIntExtra("YEAR", 2024)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      title = "$targetYear - ${getString(R.string.statistics)}"
    }

    mDB = DBHelper.getReadableDatabase(this)
    formatter = Formatter(this)

    monthWeekLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
          if (result.resultCode == RESULT_OK) {
            finish()
          }
        }

    val recyclerView = findViewById<RecyclerView>(R.id.statistics_detail_list)
    recyclerView.layoutManager = LinearLayoutManager(this)
    val spacingPx = (16 * resources.displayMetrics.density).toInt()
    recyclerView.addItemDecoration(CardSpacingDecoration(spacingPx))
    adapter = StatisticsListAdapter()
    recyclerView.adapter = adapter
    adapter.setOnItemClickListener { stats ->
      if (stats != null && stats.month != null) {
        val intent = Intent(this, MonthWeekBreakdownActivity::class.java)
        intent.putExtra(MonthWeekBreakdownActivity.EXTRA_YEAR, targetYear)
        intent.putExtra(MonthWeekBreakdownActivity.EXTRA_MONTH, stats.month)
        monthWeekLauncher.launch(intent)
      }
    }

    val rootView = findViewById<View>(R.id.statistics_detail_layout)
    ViewCompat.setOnApplyWindowInsetsListener(
        rootView,
        OnApplyWindowInsetsListener { v, windowInsets ->
          val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
          val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
          mlp.topMargin = insets.top
          WindowInsetsCompat.CONSUMED
        })

    loadMonthlyStats()
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(mDB)
  }

  override fun onSupportNavigateUp(): Boolean {
    onBackPressed()
    return true
  }

  private fun loadMonthlyStats() {
    val sql =
        "SELECT * FROM " +
            Constants.DB.MONTHLY_STATS.TABLE +
            " WHERE " +
            Constants.DB.MONTHLY_STATS.YEAR +
            " = ?" +
            " ORDER BY " +
            Constants.DB.MONTHLY_STATS.MONTH

    val cursor = mDB!!.rawQuery(sql, arrayOf(targetYear.toString()))
    adapter.swapCursor(cursor)
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

  private inner class StatisticsListAdapter :
      RecyclerView.Adapter<StatisticsListAdapter.Holder>() {
    private var cursor: Cursor? = null
    private var onItemClickListener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener?) {
      onItemClickListener = listener
    }

    fun swapCursor(newCursor: Cursor?) {
      cursor?.close()
      cursor = newCursor
      notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
      return cursor?.count ?: 0
    }

    private fun getItem(position: Int): MonthlyStatsEntity? {
      return if (cursor != null && cursor!!.moveToPosition(position)) {
        MonthlyStatsEntity(cursor!!)
      } else {
        null
      }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
      val inflater = LayoutInflater.from(this@StatisticsDetailActivity)
      val view = inflater.inflate(R.layout.statistics_detail_row, parent, false)
      return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
      CardPressHelper.clearPressState(holder.itemView)
      val stats = getItem(position) ?: return

      if (stats.month != null && stats.month!! >= 1 && stats.month!! <= 12) {
        holder.monthText.text = MONTH_NAMES[stats.month!! - 1]
      } else {
        holder.monthText.text = "Month ${stats.month}"
      }

      if (stats.runCount != null) {
        holder.runCountText.text = "${stats.runCount} runs"
      } else {
        holder.runCountText.text = "0 runs"
      }

      if (stats.totalDistance != null) {
        holder.totalDistanceText.text =
            formatter.formatDistance(
                Formatter.Format.TXT_SHORT, stats.totalDistance!!.toLong())
      } else {
        holder.totalDistanceText.text = "-"
      }

      if (stats.avgPace != null) {
        holder.avgPaceText.text =
            formatter.formatPaceFromSecPerKm(Formatter.Format.TXT_SHORT, stats.avgPace!!)
      } else {
        holder.avgPaceText.text = "-"
      }

      if (stats.avgRunLength != null) {
        holder.avgRunLengthText.text =
            formatter.formatDistance(
                Formatter.Format.TXT_SHORT, stats.avgRunLength!!.toLong())
      } else {
        holder.avgRunLengthText.text = "-"
      }

      holder.itemView.setOnClickListener {
        onItemClickListener?.onItemClick(stats)
      }
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      val monthText: TextView = itemView.findViewById(R.id.month_text)
      val runCountText: TextView = itemView.findViewById(R.id.run_count_text)
      val totalDistanceText: TextView = itemView.findViewById(R.id.total_distance_text)
      val avgPaceText: TextView = itemView.findViewById(R.id.avg_pace_text)
      val avgRunLengthText: TextView = itemView.findViewById(R.id.avg_run_length_text)
    }
  }

  companion object {
    private val MONTH_NAMES =
        arrayOf(
            "January",
            "February",
            "March",
            "April",
            "May",
            "June",
            "July",
            "August",
            "September",
            "October",
            "November",
            "December",
        )
  }
}
