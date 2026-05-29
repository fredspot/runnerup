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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.runnerup.R
import org.runnerup.analytics.BestTimesCalculator
import org.runnerup.common.util.Constants
import org.runnerup.core.util.BgTasks
import org.runnerup.core.util.Formatter
import org.runnerup.data.BestTimesDistances
import org.runnerup.data.DBHelper
import org.runnerup.data.entities.BestTimesEntity
import org.runnerup.data.entities.BestTimesSummaryEntity

class BestTimesFragment : Fragment(R.layout.best_times), Constants {

  private var db: SQLiteDatabase? = null
  private var adapter: BestTimesListAdapter? = null
  private val summaries = ArrayList<BestTimesSummaryEntity>()
  private val bestTimes = ArrayList<BestTimesEntity?>()
  private var formatter: Formatter? = null

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val recyclerView = view.findViewById<RecyclerView>(R.id.best_times_list)
    val context = requireContext()

    db = DBHelper.getWritableDatabase(context)
    formatter = Formatter(context)
    recyclerView.layoutManager = LinearLayoutManager(context)
    val spacing = (16 * resources.displayMetrics.density).toInt()
    recyclerView.addItemDecoration(SpacingDecoration(spacing))
    adapter = BestTimesListAdapter()
    recyclerView.adapter = adapter
    recyclerView.setOnLongClickListener {
      Log.d(TAG, "Long press detected - forcing recomputation")
      forceRecomputation()
      true
    }

    loadDistances()
  }

  override fun onResume() {
    super.onResume()
    loadDistances()
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(db)
  }

  private fun loadDistances() {
    val database = db ?: return
    summaries.clear()
    bestTimes.clear()

    val sql =
        "SELECT ${Constants.DB.BEST_TIMES.DISTANCE}" +
            ", AVG(${Constants.DB.BEST_TIMES.TIME}) as avg_time" +
            ", COUNT(*) as count" +
            " FROM ${Constants.DB.BEST_TIMES.TABLE}" +
            " GROUP BY ${Constants.DB.BEST_TIMES.DISTANCE}" +
            " ORDER BY ${Constants.DB.BEST_TIMES.DISTANCE}"

    Log.d(TAG, "Loading best times summaries from database...")
    database.rawQuery(sql, null).use { cursor ->
      var count = 0
      while (cursor.moveToNext()) {
        val summary = BestTimesSummaryEntity(cursor)
        summaries.add(summary)
        Log.d(
            TAG,
            "Found summary: ${summary.distance}m, avg=${summary.averageTime}ms, count=${summary.count}",
        )
        count++
      }
      Log.d(TAG, "Total summaries found: $count")
    }

    for (summary in summaries) {
      val bestTimeSql =
          "SELECT * FROM ${Constants.DB.BEST_TIMES.TABLE}" +
              " WHERE ${Constants.DB.BEST_TIMES.DISTANCE} = ?" +
              " AND ${Constants.DB.BEST_TIMES.RANK} = 1" +
              " LIMIT 1"
      database.rawQuery(bestTimeSql, arrayOf(summary.distance.toString())).use { cursor ->
        if (cursor.moveToFirst()) {
          bestTimes.add(BestTimesEntity(cursor))
        } else {
          bestTimes.add(null)
        }
      }
    }

    adapter?.notifyDataSetChanged()
  }

  private fun forceRecomputation() {
    val database = db ?: return
    Log.d(TAG, "Forcing recomputation of best times...")
    database.delete(
        Constants.DB.COMPUTATION_TRACKING.TABLE,
        "${Constants.DB.COMPUTATION_TRACKING.COMPUTATION_TYPE} = ?",
        arrayOf("best_times"),
    )
    Log.d(TAG, "Cleared computation tracking, triggering recomputation...")
    BgTasks.run(
        { BestTimesCalculator.computeBestTimes(database) },
        { Log.d(TAG, "Recomputation completed"); loadDistances() },
    )
  }

  private class SpacingDecoration(private val spacePx: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
      outRect.bottom = spacePx
    }
  }

  private inner class BestTimesListAdapter : RecyclerView.Adapter<BestTimesListAdapter.RowHolder>() {
    private val inflater = LayoutInflater.from(requireContext())

    override fun getItemCount(): Int = summaries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
      val row = inflater.inflate(R.layout.best_times_row, parent, false)
      return RowHolder(row)
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
      val summary = summaries[position]
      val bestTime = bestTimes.getOrNull(position)

      holder.distanceText.setOnClickListener {
        val intent = Intent(requireContext(), BestTimesDetailActivity::class.java)
        intent.putExtra("DISTANCE", summary.distance)
        startActivity(intent)
      }

      val activityId = bestTime?.activityId
      if (activityId != null) {
        holder.cardLayout.setOnClickListener {
          val intent = Intent(requireContext(), DetailActivity::class.java)
          intent.putExtra("ID", activityId)
          intent.putExtra("mode", "details")
          intent.putExtra("source_tab", 2)
          startActivity(intent)
        }
        holder.cardLayout.isClickable = true
      } else {
        holder.cardLayout.setOnClickListener(null)
        holder.cardLayout.isClickable = false
      }

      holder.distanceText.text = BestTimesDistances.getLabel(summary.distance)

      val fmt = formatter
      if (bestTime != null && fmt != null) {
        val time = bestTime.time
        holder.timeText.text =
            if (time != null) {
              fmt.formatElapsedTime(Formatter.Format.TXT_LONG, time / 1000)
            } else {
              "-"
            }

        val pace = bestTime.pace
        holder.paceText.text =
            if (pace != null) {
              fmt.formatPaceFromSecPerKm(Formatter.Format.TXT_LONG, pace)
            } else {
              "-"
            }

        val startTime = bestTime.startTime
        holder.dateText.text =
            if (startTime != null) {
              SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                  .format(Date(startTime * 1000))
            } else {
              "-"
            }

        holder.hrText.text = fmt.formatBestTimesHeartRateLine(bestTime.avgHr, bestTime.maxHr)
      } else {
        holder.timeText.text = "-"
        holder.paceText.text = "-"
        holder.dateText.text = "-"
        holder.hrText.text = "-"
      }
    }

    inner class RowHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      val distanceText: TextView = itemView.findViewById(R.id.distance_text)
      val cardLayout: View = itemView.findViewById(R.id.best_run_card)
      val timeText: TextView = itemView.findViewById(R.id.time_text)
      val paceText: TextView = itemView.findViewById(R.id.pace_text)
      val dateText: TextView = itemView.findViewById(R.id.date_text)
      val hrText: TextView = itemView.findViewById(R.id.hr_text)
    }
  }

  companion object {
    private const val TAG = "BestTimesFragment"
  }
}
