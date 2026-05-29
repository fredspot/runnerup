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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.Insets
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.core.util.Formatter
import org.runnerup.data.BestTimesDistances
import org.runnerup.data.DBHelper
import org.runnerup.data.entities.BestTimesEntity

class BestTimesDetailActivity : AppCompatActivity(), Constants {

  private fun interface OnItemClickListener {
    fun onItemClick(bestTime: BestTimesEntity?)
  }

  private var targetDistance = 0
  private var mDB: SQLiteDatabase? = null
  private lateinit var formatter: Formatter
  private lateinit var adapter: BestTimesListAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.best_times_detail)

    targetDistance = intent.getIntExtra("DISTANCE", 1000)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      title = "${getDistanceLabel(targetDistance)} - ${getString(R.string.best_times)}"
    }

    mDB = DBHelper.getReadableDatabase(this)
    formatter = Formatter(this)

    val recyclerView = findViewById<RecyclerView>(R.id.best_times_detail_list)
    recyclerView.layoutManager = LinearLayoutManager(this)
    val spacingPx = (16 * resources.displayMetrics.density).toInt()
    recyclerView.addItemDecoration(CardSpacingDecoration(spacingPx))
    adapter = BestTimesListAdapter()
    recyclerView.adapter = adapter
    adapter.setOnItemClickListener { bestTime ->
      if (bestTime != null && bestTime.activityId != null) {
        val intent = Intent(this, DetailActivity::class.java)
        intent.putExtra("ID", bestTime.activityId)
        intent.putExtra("mode", "details")
        startActivity(intent)
      }
    }

    val rootView = findViewById<View>(R.id.best_times_detail_layout)
    ViewCompat.setOnApplyWindowInsetsListener(
        rootView,
        OnApplyWindowInsetsListener { v, windowInsets ->
          val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
          v.setPadding(insets.left, 0, insets.right, insets.bottom)

          val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
          mlp.topMargin = insets.top
          WindowInsetsCompat.CONSUMED
        })

    loadBestTimes()
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(mDB)
  }

  override fun onSupportNavigateUp(): Boolean {
    onBackPressed()
    return true
  }

  private fun loadBestTimes() {
    val sql =
        "SELECT * FROM " +
            Constants.DB.BEST_TIMES.TABLE +
            " WHERE " +
            Constants.DB.BEST_TIMES.DISTANCE +
            " = ?" +
            " ORDER BY " +
            Constants.DB.BEST_TIMES.RANK

    val cursor = mDB!!.rawQuery(sql, arrayOf(targetDistance.toString()))
    adapter.swapCursor(cursor)
  }

  private fun getDistanceLabel(distance: Int): String {
    return BestTimesDistances.getLabel(distance)
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

  private inner class BestTimesListAdapter :
      RecyclerView.Adapter<BestTimesListAdapter.Holder>() {
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

    private fun getItem(position: Int): BestTimesEntity? {
      return if (cursor != null && cursor!!.moveToPosition(position)) {
        BestTimesEntity(cursor!!)
      } else {
        null
      }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
      val inflater = LayoutInflater.from(this@BestTimesDetailActivity)
      val view = inflater.inflate(R.layout.best_times_detail_row, parent, false)
      return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
      val bestTime = getItem(position) ?: return

      holder.rankText.text = "#${bestTime.rank}"

      if (bestTime.time != null) {
        val timeInSeconds = bestTime.time!! / 1000
        holder.timeText.text =
            formatter.formatElapsedTime(Formatter.Format.TXT_LONG, timeInSeconds)
      }

      if (bestTime.pace != null) {
        holder.paceText.text =
            formatter.formatPaceFromSecPerKm(Formatter.Format.TXT_LONG, bestTime.pace!!)
      }

      if (bestTime.startTime != null) {
        val date = Date(bestTime.startTime!! * 1000)
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        holder.dateText.text = dateFormat.format(date)
      }

      holder.hrText.text =
          formatter.formatBestTimesHeartRateLine(bestTime.avgHr, bestTime.maxHr)

      holder.itemView.setOnClickListener {
        onItemClickListener?.onItemClick(bestTime)
      }
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      val rankText: TextView = itemView.findViewById(R.id.rank_text)
      val timeText: TextView = itemView.findViewById(R.id.time_text)
      val paceText: TextView = itemView.findViewById(R.id.pace_text)
      val dateText: TextView = itemView.findViewById(R.id.date_text)
      val hrText: TextView = itemView.findViewById(R.id.hr_text)
    }
  }
}
