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

import android.app.AlertDialog
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import java.util.Date
import org.runnerup.R
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.ActivitySummaryBinder
import org.runnerup.core.util.BgTasks
import org.runnerup.core.util.Formatter
import org.runnerup.core.workout.Sport
import org.runnerup.data.ActivityCleaner
import org.runnerup.data.DBHelper
import org.runnerup.core.util.CardPressHelper
import org.runnerup.data.entities.ActivityEntity

class HistoryFragment : Fragment(R.layout.history) {

  private var db: SQLiteDatabase? = null
  private var formatter: Formatter? = null
  private var adapter: HistoryListAdapter? = null
  private var filterButton: Button? = null
  private var selectedYear = -1
  private var selectedMonth = -1
  private var loadGeneration = 0

  private val activityResultLauncher =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { reloadList() }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val recyclerView = view.findViewById<RecyclerView>(R.id.history_list)
    filterButton = view.findViewById(R.id.history_filter)
    val context = requireContext()

    view.findViewById<View>(R.id.history_add).setOnClickListener {
      activityResultLauncher.launch(Intent(context, ManualActivity::class.java))
    }

    filterButton?.setOnClickListener {
      if (selectedYear != -1 && selectedMonth != -1) {
        selectedYear = -1
        selectedMonth = -1
        filterButton?.text = "Filter"
        reloadList()
      } else {
        showFilterDialog()
      }
    }

    db = DBHelper.getReadableDatabase(context)
    formatter = Formatter(context)

    val spacingPx = (16 * resources.displayMetrics.density).toInt()
    recyclerView.layoutManager = LinearLayoutManager(context)
    recyclerView.addItemDecoration(HistoryItemSpacingDecoration(spacingPx))
    adapter =
        HistoryListAdapter(formatter) { activityId -> openDetail(activityId) }
    recyclerView.adapter = adapter

    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    ActivityCleaner().conditionalRecompute(db)
    reloadList()
  }

  override fun onResume() {
    super.onResume()
    reloadList()
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(db)
  }

  /** Called from [MainLayout] to set month filter programmatically. */
  @JvmOverloads
  fun applyFilter(year: Int, month: Int) {
    selectedYear = year
    selectedMonth = if (month in 1..12) month - 1 else month
    filterButton?.text = "Clear"
    reloadList()
  }

  private fun reloadList() {
    val database = db ?: return
    val gen = ++loadGeneration
    BgTasks.runDb(
        { queryActivities(database) },
        { activities ->
          if (!isAdded || gen != loadGeneration) return@runDb
          adapter?.submit(buildDisplayRows(activities))
        },
    )
  }

  private fun queryActivities(database: SQLiteDatabase): List<ActivityEntity> {
    val projection =
        arrayOf(
            "_id",
            DB.ACTIVITY.START_TIME,
            DB.ACTIVITY.DISTANCE,
            DB.ACTIVITY.TIME,
            DB.ACTIVITY.SPORT,
            DB.ACTIVITY.AVG_HR,
            DB.ACTIVITY.MAX_HR,
        )
    var whereClause = "deleted == 0"
    if (selectedYear != -1 && selectedMonth != -1) {
      val cal = Calendar.getInstance()
      cal.set(selectedYear, selectedMonth, 1, 0, 0, 0)
      cal.set(Calendar.MILLISECOND, 0)
      val startTime = cal.timeInMillis / 1000
      cal.add(Calendar.MONTH, 1)
      val endTime = cal.timeInMillis / 1000
      whereClause +=
          " AND ${DB.ACTIVITY.START_TIME} >= $startTime AND ${DB.ACTIVITY.START_TIME} < $endTime"
    }
    val result = ArrayList<ActivityEntity>()
    database
        .query(
            DB.ACTIVITY.TABLE,
            projection,
            whereClause,
            null,
            null,
            null,
            "${DB.ACTIVITY.START_TIME} desc",
        )
        .use { cursor ->
          while (cursor.moveToNext()) {
            result.add(ActivityEntity(cursor))
          }
        }
    return result
  }

  private fun buildDisplayRows(activities: List<ActivityEntity>): List<HistoryRow> {
    val fmt = formatter ?: return emptyList()
    val rows = ArrayList<HistoryRow>(activities.size)
    var prevYear = -1
    var prevMonth = -1
    for (entity in activities) {
      val startTime = entity.startTime ?: continue
      val cal = Calendar.getInstance()
      cal.time = Date(startTime * 1000)
      val year = cal.get(Calendar.YEAR)
      val month = cal.get(Calendar.MONTH)
      val showHeader = year != prevYear || month != prevMonth
      val headerText = if (showHeader) fmt.formatMonth(cal.time) else null
      rows.add(HistoryRow(entity, showHeader, headerText))
      prevYear = year
      prevMonth = month
    }
    return rows
  }

  private fun openDetail(activityId: Long?) {
    if (activityId == null) return
    val intent = Intent(requireContext(), DetailActivity::class.java)
    intent.putExtra("ID", activityId)
    intent.putExtra("mode", "details")
    intent.putExtra("source_tab", 1)
    activityResultLauncher.launch(intent)
  }

  private fun showFilterDialog() {
    val database = db ?: return
    val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.filter_dialog, null)
    val yearPicker = dialogView.findViewById<NumberPicker>(R.id.year_picker)
    val monthPicker = dialogView.findViewById<NumberPicker>(R.id.month_picker)

    var availableYears = getAvailableFilterYears(database)
    if (availableYears.isEmpty()) {
      availableYears = listOf(Calendar.getInstance().get(Calendar.YEAR))
    }
    val years = availableYears.map { it.toString() }.toTypedArray()
    yearPicker.minValue = 0
    yearPicker.maxValue = years.size - 1
    yearPicker.displayedValues = years

    val months =
        arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    monthPicker.minValue = 0
    monthPicker.maxValue = 11
    monthPicker.displayedValues = months

    if (selectedYear != -1 && selectedMonth != -1) {
      for (i in years.indices) {
        if (years[i].toInt() == selectedYear) {
          yearPicker.value = i
          break
        }
      }
      monthPicker.value = selectedMonth
    }

    AlertDialog.Builder(requireContext())
        .setView(dialogView)
        .setTitle("Filter by Month")
        .setPositiveButton("Apply") { _, _ ->
          selectedYear = years[yearPicker.value].toInt()
          selectedMonth = monthPicker.value
          filterButton?.text = "Clear"
          reloadList()
        }
        .setNeutralButton("Clear") { _, _ ->
          selectedYear = -1
          selectedMonth = -1
          filterButton?.text = "Filter"
          reloadList()
        }
        .setNegativeButton("Cancel", null)
        .show()
  }

  private fun getAvailableFilterYears(database: SQLiteDatabase): List<Int> {
    val years = ArrayList<Int>()
    val sql =
        "SELECT DISTINCT strftime('%Y', ${DB.ACTIVITY.START_TIME}, 'unixepoch') AS year" +
            " FROM ${DB.ACTIVITY.TABLE}" +
            " WHERE ${DB.ACTIVITY.DELETED} = 0" +
            " ORDER BY year DESC"
    try {
      database.rawQuery(sql, null).use { cursor ->
        while (cursor.moveToNext()) {
          val year = cursor.getString(0)
          if (year != null) {
            years.add(year.toInt())
          }
        }
      }
    } catch (_: Exception) {
    }
    return years
  }

  private data class HistoryRow(
      val entity: ActivityEntity,
      val showMonthHeader: Boolean,
      val monthHeaderText: String?,
  )

  private class HistoryItemSpacingDecoration(private val spacingPx: Int) :
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

  private class HistoryListAdapter(
      private val formatter: Formatter?,
      private val onItemClick: (Long?) -> Unit,
  ) : RecyclerView.Adapter<HistoryListAdapter.Holder>() {
    private val rows = ArrayList<HistoryRow>()

    fun submit(newRows: List<HistoryRow>) {
      rows.clear()
      rows.addAll(newRows)
      notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
      val view =
          LayoutInflater.from(parent.context).inflate(R.layout.history_row, parent, false)
      CardPressHelper.prepareRowHost(view)
      return Holder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
      holder.bind(rows[position], formatter)
    }

    class Holder(
        itemView: View,
        onItemClick: (Long?) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
      private val cardView: View = itemView.findViewById(R.id.history_row_card)
      private val sectionTitle: TextView = itemView.findViewById(R.id.history_section_title)
      private val dateText: TextView = itemView.findViewById(R.id.history_list_date)
      private val distanceText: TextView = itemView.findViewById(R.id.history_list_distance)
      private val durationText: TextView = itemView.findViewById(R.id.history_list_duration)
      private val paceText: TextView = itemView.findViewById(R.id.history_list_pace)
      private val emblem: ImageView = itemView.findViewById(R.id.history_list_emblem)
      private val additionalInfo: TextView = itemView.findViewById(R.id.history_list_additional)
      private var activityId: Long? = null

      init {
        CardPressHelper.prepareCard(cardView)
        cardView.setOnClickListener { onItemClick(activityId) }
      }

      fun bind(row: HistoryRow, formatter: Formatter?) {
        CardPressHelper.clearPressState(itemView, cardView)
        val ae = row.entity
        activityId = ae.id
        val fmt = formatter ?: return
        val context = itemView.context

        if (row.showMonthHeader && row.monthHeaderText != null) {
          sectionTitle.visibility = View.VISIBLE
          sectionTitle.text = row.monthHeaderText
        } else {
          sectionTitle.visibility = View.GONE
        }

        val startTime = ae.startTime
        dateText.text = if (startTime != null) fmt.formatDateTime(startTime) else ""

        val distance = ae.distance
        if (distance != null) {
          distanceText.text = fmt.formatDistance(Formatter.Format.TXT_SHORT, distance.toLong())
        } else {
          distanceText.text = ""
        }

        val sport = ae.sport
        val sportColor = ContextCompat.getColor(context, Sport.colorOf(sport))
        val sportDrawable: Drawable? =
            AppCompatResources.getDrawable(context, Sport.drawableColored16Of(sport))
        emblem.setImageDrawable(sportDrawable)
        distanceText.setTextColor(sportColor)
        additionalInfo.setTextColor(sportColor)

        additionalInfo.text = fmt.formatBestTimesHeartRateLine(ae.avgHr, ae.maxHr)

        val dur = ae.time
        if (distance != null && dur != null && dur != 0L) {
          ActivitySummaryBinder.bind(
              fmt,
              distanceText,
              durationText,
              paceText,
              distance,
              dur,
          )
        } else {
          durationText.text =
              if (dur != null) fmt.formatElapsedTime(Formatter.Format.TXT_SHORT, dur) else ""
          paceText.text = ""
        }
      }
    }
  }
}
