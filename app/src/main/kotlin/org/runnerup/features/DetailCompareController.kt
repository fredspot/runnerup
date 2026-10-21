package org.runnerup.features

import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jjoe64.graphview.DefaultLabelFormatter
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import com.jjoe64.graphview.series.PointsGraphSeries
import org.runnerup.R
import org.runnerup.analytics.ComparableSegmentLoader
import org.runnerup.analytics.MonthlyComparisonCalculator
import org.runnerup.analytics.RunConditions
import org.runnerup.analytics.RunConditionsAdjuster
import org.runnerup.analytics.RunEngineStats
import org.runnerup.analytics.SegmentComparer
import org.runnerup.analytics.SimilarRunMatcher
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.BgTasks
import org.runnerup.core.util.Formatter
import org.runnerup.core.util.HRZones
import org.runnerup.data.RunningActivityReader
import org.runnerup.data.RunningActivityReader.ActivityRow
import org.runnerup.data.RunningActivityReader.HrSample
import org.runnerup.ui.common.widget.TitleSpinner

/** Compare tab: similar distance/pace runs with HR overlay and BPM-vs-pace. */
internal class DetailCompareController {

  private var activity: DetailActivity? = null
  private var db: SQLiteDatabase? = null
  private var activityId: Long = -1
  private var formatter: Formatter? = null
  private var current: ActivityRow? = null
  private var similar: List<ActivityRow> = emptyList()
  private var similarForStats: List<ActivityRow> = emptyList()
  private var selected: ActivityRow? = null
  private var modeHrOverlay = true
  private var conditions: Map<Long, RunConditions> = emptyMap()
  private var segmentComparisons: List<SegmentComparer.Comparison> = emptyList()

  private var emptyHint: TextView? = null
  private var content: View? = null
  private var graphHost: LinearLayout? = null
  private var graphView: GraphView? = null
  private var adapter: SimilarAdapter? = null

  fun attach(
      activity: DetailActivity,
      root: View,
      db: SQLiteDatabase,
      activityId: Long,
      formatter: Formatter,
  ) {
    this.activity = activity
    this.db = db
    this.activityId = activityId
    this.formatter = formatter

    emptyHint = root.findViewById(R.id.compare_empty)
    content = root.findViewById(R.id.compare_content)
    graphHost = root.findViewById(R.id.compare_graph_host)

    val list = root.findViewById<RecyclerView>(R.id.compare_similar_list)
    adapter = SimilarAdapter { row -> select(row) }
    list.layoutManager = LinearLayoutManager(activity)
    list.adapter = adapter
    list.isNestedScrollingEnabled = false

    val spinner = root.findViewById<TitleSpinner>(R.id.compare_mode_spinner)
    spinner.setArrayEntries(
        arrayOf(
            activity.getString(R.string.compare_mode_hr),
            activity.getString(R.string.compare_mode_bpm_pace),
        ),
    )
    spinner.setViewSelection(0)
    spinner.setViewValue(0)
    spinner.setViewOnItemSelectedListener(
        object : AdapterView.OnItemSelectedListener {
          override fun onItemSelected(
              parent: AdapterView<*>?,
              view: View?,
              position: Int,
              id: Long,
          ) {
            spinner.setViewSelection(position)
            spinner.setViewValue(position)
            modeHrOverlay = position == 0
            refreshGraph()
          }

          override fun onNothingSelected(parent: AdapterView<*>?) {}
        },
    )

    loadSimilar()
  }

  fun reload() {
    if (db == null || activity == null) {
      return
    }
    loadSimilar()
  }

  fun updateForSport(sportValue: Int) {
    val tab = activity?.compareTab ?: return
    tab.visibility = if (sportValue == DB.ACTIVITY.SPORT_RUNNING) View.VISIBLE else View.GONE
  }

  private fun loadSimilar() {
    val database = db ?: return
    val id = activityId
    val host = activity ?: return
    val zoneBounds = MonthlyComparisonCalculator.resolveZoneBounds(HRZones(host))
    BgTasks.runDb(
        {
          val currentRow = RunningActivityReader.getActivityWithHr(database, id)
          val candidates =
              if (currentRow != null) {
                RunningActivityReader.getRunningActivitiesNearDistance(
                    database,
                    id,
                    currentRow.totalDistance,
                    SimilarRunMatcher.DISTANCE_TOLERANCE,
                )
              } else {
                emptyList()
              }
          val allSimilar = SimilarRunMatcher.findSimilar(currentRow, candidates)
          val ranked =
              SimilarRunMatcher.cap(
                  SimilarRunMatcher.sortBySimilarity(currentRow, allSimilar),
                  SimilarRunMatcher.MAX_RESULTS,
              )
          val segments = ComparableSegmentLoader.loadRunning(database, zoneBounds)
          val currentSegments = segments.filter { it.activityId == id }
          val historical = segments.filter { it.activityId != id }
          val comparisons = SegmentComparer.compare(currentSegments, historical)
          val conditionIds = ArrayList<Long>()
          conditionIds.add(id)
          for (row in ranked) {
            conditionIds.add(row.activityId)
          }
          val loadedConditions = ComparableSegmentLoader.loadConditions(database, conditionIds)
          CompareLoad(currentRow, ranked, allSimilar, comparisons, loadedConditions)
        },
        { result ->
          current = result.current
          similar = result.ranked
          similarForStats = result.allSimilar
          segmentComparisons = result.comparisons
          conditions = result.conditions
          selected = similar.firstOrNull()
          bindLoaded()
        },
    )
  }

  private fun bindLoaded() {
    val act = activity ?: return
    val fmt = formatter ?: return
    val currentRow = current
    if (currentRow == null || (similar.isEmpty() && segmentComparisons.isEmpty())) {
      emptyHint?.visibility = View.VISIBLE
      content?.visibility = View.GONE
      emptyHint?.setText(
          if (currentRow == null || currentRow.totalDistance <= 0) {
            R.string.compare_empty
          } else {
            R.string.compare_no_similar
          },
      )
      return
    }
    emptyHint?.visibility = View.GONE
    content?.visibility = View.VISIBLE
    val hasSimilar = similar.isNotEmpty()
    act.findViewById<View>(R.id.compare_engine).visibility =
        if (hasSimilar) View.VISIBLE else View.GONE
    act.findViewById<View>(R.id.compare_summary).visibility =
        if (hasSimilar) View.VISIBLE else View.GONE
    act.findViewById<View>(R.id.compare_similar_title).visibility =
        if (hasSimilar) View.VISIBLE else View.GONE
    act.findViewById<View>(R.id.compare_similar_list).visibility =
        if (hasSimilar) View.VISIBLE else View.GONE
    act.findViewById<View>(R.id.compare_mode_spinner).visibility =
        if (hasSimilar) View.VISIBLE else View.GONE
    graphHost?.visibility = if (hasSimilar) View.VISIBLE else View.GONE
    adapter?.setItems(similar, selected?.activityId ?: -1)
    val similarTitle = act.findViewById<TextView>(R.id.compare_similar_title)
    similarTitle.text =
        if (similarForStats.size > similar.size) {
          act.getString(
              R.string.compare_similar_runs_capped,
              similar.size,
              similarForStats.size,
          )
        } else {
          act.getString(R.string.compare_similar_runs)
        }
    if (hasSimilar) {
      bindEngine(act, currentRow)
      bindSummary(act, fmt, currentRow, selected)
      refreshGraph()
    }
    bindSegments(act, fmt)
  }

  private fun select(row: ActivityRow) {
    selected = row
    adapter?.setItems(similar, row.activityId)
    val act = activity ?: return
    val fmt = formatter ?: return
    val currentRow = current ?: return
    bindSummary(act, fmt, currentRow, row)
    refreshGraph()
  }

  private fun bindEngine(act: DetailActivity, thisRun: ActivityRow) {
    val snap = RunEngineStats.compute(thisRun, similarForStats)
    val bpmView = act.findViewById<TextView>(R.id.compare_engine_this_bpm)
    val hint = act.findViewById<TextView>(R.id.compare_engine_hint)
    val columns = act.findViewById<View>(R.id.compare_engine_columns)
    if (thisRun.avgHr <= 0) {
      bpmView.text = "--"
      hint.setText(R.string.compare_engine_no_hr)
      columns.visibility = View.GONE
      return
    }
    bpmView.text = act.getString(R.string.compare_engine_this_bpm, thisRun.avgHr)
    hint.setText(R.string.compare_engine_subtitle)
    columns.visibility = View.VISIBLE
    bindEngineColumn(
        act,
        R.id.compare_engine_year_percentile,
        R.id.compare_engine_year_delta,
        R.id.compare_engine_year_mean,
        R.id.compare_engine_year_n,
        snap.percentileYear,
        snap.deltaVsYear,
        snap.meanHrYear,
        snap.similarWithHrYear,
    )
    bindEngineColumn(
        act,
        R.id.compare_engine_all_percentile,
        R.id.compare_engine_all_delta,
        R.id.compare_engine_all_mean,
        R.id.compare_engine_all_n,
        snap.percentileAll,
        snap.deltaVsAll,
        snap.meanHrAll,
        snap.similarWithHrAll,
    )
  }

  private fun bindEngineColumn(
      act: DetailActivity,
      percentileId: Int,
      deltaId: Int,
      meanId: Int,
      nId: Int,
      percentile: Int?,
      delta: Int?,
      mean: Int?,
      n: Int,
  ) {
    val percentileView = act.findViewById<TextView>(percentileId)
    val deltaView = act.findViewById<TextView>(deltaId)
    val meanView = act.findViewById<TextView>(meanId)
    val nView = act.findViewById<TextView>(nId)
    val betterColor = ContextCompat.getColor(act, R.color.colorPrimary)
    val worseColor = Color.parseColor("#F59E0B")
    val neutral = ContextCompat.getColor(act, R.color.colorText)
    when {
      percentile != null -> {
        percentileView.text = act.getString(R.string.compare_engine_percentile, percentile)
        percentileView.setTextColor(if (percentile >= 50) betterColor else worseColor)
      }
      n <= 0 -> {
        percentileView.setText(R.string.compare_engine_no_similar_period)
        percentileView.setTextColor(neutral)
      }
      else -> {
        percentileView.setText(R.string.compare_engine_percentile_na)
        percentileView.setTextColor(neutral)
      }
    }
    when {
      delta == null -> {
        deltaView.text = ""
        deltaView.visibility = View.GONE
      }
      delta < 0 -> {
        deltaView.visibility = View.VISIBLE
        deltaView.text = act.getString(R.string.compare_engine_delta_lower, -delta)
        deltaView.setTextColor(betterColor)
      }
      delta > 0 -> {
        deltaView.visibility = View.VISIBLE
        deltaView.text = act.getString(R.string.compare_engine_delta_higher, delta)
        deltaView.setTextColor(worseColor)
      }
      else -> {
        deltaView.visibility = View.VISIBLE
        deltaView.setText(R.string.compare_engine_delta_same)
        deltaView.setTextColor(neutral)
      }
    }
    if (mean != null) {
      meanView.visibility = View.VISIBLE
      meanView.text = act.getString(R.string.compare_engine_mean, mean)
    } else {
      meanView.visibility = View.GONE
    }
    if (n > 0) {
      nView.visibility = View.VISIBLE
      nView.text = act.getString(R.string.compare_engine_n, n)
    } else {
      nView.visibility = View.GONE
    }
  }

  private fun bindSummary(
      act: DetailActivity,
      fmt: Formatter,
      thisRun: ActivityRow,
      other: ActivityRow?,
  ) {
    act.findViewById<TextView>(R.id.compare_this_distance).text = formatDistance(fmt, thisRun)
    act.findViewById<TextView>(R.id.compare_this_time).text = formatTime(fmt, thisRun)
    act.findViewById<TextView>(R.id.compare_this_pace).text = formatPace(fmt, thisRun)
    act.findViewById<TextView>(R.id.compare_this_hr).text = formatHr(thisRun.avgHr)
    bindConditions(act, fmt, thisRun, other)
    act.findViewById<TextView>(R.id.compare_other_distance).text =
        other?.let { formatDistance(fmt, it) } ?: "--"
    act.findViewById<TextView>(R.id.compare_other_time).text =
        other?.let { formatTime(fmt, it) } ?: "--"
    act.findViewById<TextView>(R.id.compare_other_pace).text =
        other?.let { formatPace(fmt, it) } ?: "--"
    act.findViewById<TextView>(R.id.compare_other_hr).text =
        other?.let { formatHr(it.avgHr) } ?: "--"
  }

  private fun bindConditions(
      act: DetailActivity,
      fmt: Formatter,
      thisRun: ActivityRow,
      other: ActivityRow?,
  ) {
    val thisConditions = conditions[thisRun.activityId] ?: RunConditions.EMPTY
    val otherConditions = other?.let { conditions[it.activityId] }
    act.findViewById<TextView>(R.id.compare_this_elevation).text =
        formatElevation(fmt, thisConditions.elevationGainM)
    act.findViewById<TextView>(R.id.compare_other_elevation).text =
        otherConditions?.let { formatElevation(fmt, it.elevationGainM) } ?: "--"
    act.findViewById<TextView>(R.id.compare_this_temperature).text =
        formatTemperature(act, thisConditions.avgTempC)
    act.findViewById<TextView>(R.id.compare_other_temperature).text =
        otherConditions?.let { formatTemperature(act, it.avgTempC) } ?: "--"
    act.findViewById<TextView>(R.id.compare_this_adjusted_pace).text =
        formatAdjustedPace(fmt, thisRun, thisConditions)
    act.findViewById<TextView>(R.id.compare_other_adjusted_pace).text =
        if (other != null && otherConditions != null) {
          formatAdjustedPace(fmt, other, otherConditions)
        } else {
          "--"
        }
  }

  private fun bindSegments(act: DetailActivity, fmt: Formatter) {
    val list = act.findViewById<LinearLayout>(R.id.compare_segments_list) ?: return
    val empty = act.findViewById<TextView>(R.id.compare_segments_empty)
    list.removeAllViews()
    if (segmentComparisons.isEmpty()) {
      empty?.visibility = View.VISIBLE
      return
    }
    empty?.visibility = View.GONE
    for (comparison in segmentComparisons) {
      val row = TextView(act)
      row.setTextColor(ContextCompat.getColor(act, R.color.colorText))
      row.textSize = 13f
      row.setPadding(0, (4 * act.resources.displayMetrics.density).toInt(), 0, 0)
      row.text = formatSegment(act, fmt, comparison)
      list.addView(row)
    }
  }

  private fun formatSegment(
      act: DetailActivity,
      fmt: Formatter,
      comparison: SegmentComparer.Comparison,
  ): String {
    val segment = comparison.segment
    val km =
        act.getString(
            R.string.compare_segment_km,
            segment.windowIndex + 1,
            segment.windowIndex + 2,
        )
    val elev =
        when {
          kotlin.math.abs(segment.netElevationM) <= SegmentComparer.ELEVATION_TOLERANCE_M ->
              act.getString(R.string.compare_segment_flat)
          segment.netElevationM > 0 ->
              act.getString(
                  R.string.compare_segment_elev_up,
                  kotlin.math.round(segment.netElevationM).toInt(),
              )
          else ->
              act.getString(
                  R.string.compare_segment_elev_down,
                  kotlin.math.round(kotlin.math.abs(segment.netElevationM)).toInt(),
              )
        }
    val context =
        when {
          segment.prevZoneNear <= 0 && segment.prevZoneFar <= 0 ->
              act.getString(R.string.compare_segment_start)
          segment.prevZoneFar <= 0 ->
              act.getString(R.string.compare_segment_after_one, segment.prevZoneNear)
          else ->
              act.getString(
                  R.string.compare_segment_after_two,
                  segment.prevZoneFar,
                  segment.prevZoneNear,
              )
        }
    val pace =
        act.getString(
            R.string.compare_segment_pace,
            fmt.formatPaceFromSecPerKm(Formatter.Format.TXT_SHORT, segment.adjustedPaceSecPerKm),
            fmt.formatPaceFromSecPerKm(
                Formatter.Format.TXT_SHORT,
                comparison.medianAdjustedPaceSecPerKm,
            ),
        )
    val hr =
        if (segment.avgHr > 0 && comparison.medianHr > 0) {
          " · " + act.getString(R.string.compare_segment_hr, segment.avgHr, comparison.medianHr)
        } else {
          ""
        }
    val temp =
        if (!RunConditionsAdjuster.hasTemperature(segment.avgTempC)) {
          " · " + act.getString(R.string.compare_temperature_unavailable)
        } else {
          ""
        }
    return "$km · $elev · $context · $pace$hr$temp"
  }

  private fun refreshGraph() {
    val host = graphHost ?: return
    val act = activity ?: return
    val fmt = formatter ?: return
    val database = db ?: return
    val currentRow = current ?: return
    if (graphView == null) {
      graphView = GraphView(act)
      host.removeAllViews()
      host.addView(
          graphView,
          LinearLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
          ),
      )
    }
    val graph = graphView ?: return
    graph.removeAllSeries()
    if (modeHrOverlay) {
      val thisId = currentRow.activityId
      val otherId = selected?.activityId
      BgTasks.runDb(
          {
            val thisHr = downsample(RunningActivityReader.getHrDistanceSamples(database, thisId))
            val otherHr =
                if (otherId != null) {
                  downsample(RunningActivityReader.getHrDistanceSamples(database, otherId))
                } else {
                  emptyList()
                }
            Pair(thisHr, otherHr)
          },
          { samples -> bindHrOverlay(graph, fmt, samples.first, samples.second) },
      )
    } else {
      bindBpmPace(graph, fmt, currentRow, similarForStats)
    }
  }

  private fun bindHrOverlay(
      graph: GraphView,
      fmt: Formatter,
      thisHr: List<HrSample>,
      otherHr: List<HrSample>,
  ) {
    graph.title = activity?.getString(R.string.compare_mode_hr)
    graph.gridLabelRenderer.verticalAxisTitle = "bpm"
    graph.gridLabelRenderer.horizontalAxisTitle =
        activity?.getString(org.runnerup.common.R.string.Distance)
    graph.gridLabelRenderer.setLabelFormatter(
        object : DefaultLabelFormatter() {
          override fun formatLabel(value: Double, isValueX: Boolean): String {
            return if (isValueX) {
              fmt.formatDistance(Formatter.Format.TXT_SHORT, Math.round(value))
            } else {
              fmt.formatHeartRate(Formatter.Format.TXT_SHORT, value)
            }
          }
        },
    )
    if (thisHr.isNotEmpty()) {
      val series = LineGraphSeries(toHrPoints(thisHr))
      series.color = Color.parseColor("#53B1FC")
      series.thickness = 6
      graph.addSeries(series)
    }
    if (otherHr.isNotEmpty()) {
      val series = LineGraphSeries(toHrPoints(otherHr))
      series.color = Color.parseColor("#F59E0B")
      series.thickness = 6
      graph.addSeries(series)
    }
    graph.viewport.isScalable = true
    graph.viewport.isScrollable = true
  }

  private fun bindBpmPace(
      graph: GraphView,
      fmt: Formatter,
      currentRow: ActivityRow,
      similarRows: List<ActivityRow>,
  ) {
    graph.title = activity?.getString(R.string.compare_mode_bpm_pace)
    graph.gridLabelRenderer.verticalAxisTitle = "bpm"
    graph.gridLabelRenderer.horizontalAxisTitle =
        activity?.getString(R.string.avg_pace)
    graph.gridLabelRenderer.setLabelFormatter(
        object : DefaultLabelFormatter() {
          override fun formatLabel(value: Double, isValueX: Boolean): String {
            return if (isValueX) {
              fmt.formatPaceFromSecPerKm(Formatter.Format.TXT_SHORT, value)
            } else {
              fmt.formatHeartRate(Formatter.Format.TXT_SHORT, value)
            }
          }
        },
    )
    val withHr =
        similarRows
            .filter { it.avgHr > 0 }
            .sortedBy { SimilarRunMatcher.paceSecPerKm(it.totalDistance, it.totalTime) }
    if (withHr.isNotEmpty()) {
      val points =
          withHr
              .map {
                DataPoint(
                    SimilarRunMatcher.paceSecPerKm(it.totalDistance, it.totalTime),
                    it.avgHr.toDouble(),
                )
              }
              .toTypedArray()
      val similarSeries = PointsGraphSeries(points)
      similarSeries.color = Color.parseColor("#F59E0B")
      similarSeries.size = 10f
      graph.addSeries(similarSeries)
    }
    if (currentRow.avgHr > 0) {
      val pace = SimilarRunMatcher.paceSecPerKm(currentRow.totalDistance, currentRow.totalTime)
      val currentSeries =
          PointsGraphSeries(arrayOf(DataPoint(pace, currentRow.avgHr.toDouble())))
      currentSeries.color = Color.parseColor("#53B1FC")
      currentSeries.size = 16f
      graph.addSeries(currentSeries)
    }
    graph.viewport.isScalable = true
    graph.viewport.isScrollable = true
  }

  private fun toHrPoints(samples: List<HrSample>): Array<DataPoint> {
    return samples.map { DataPoint(it.distanceM, it.hr.toDouble()) }.toTypedArray()
  }

  private fun downsample(samples: List<HrSample>): List<HrSample> {
    if (samples.size <= MAX_GRAPH_POINTS) {
      return samples
    }
    val step = samples.size / MAX_GRAPH_POINTS
    val out = ArrayList<HrSample>(MAX_GRAPH_POINTS)
    var i = 0
    while (i < samples.size) {
      out.add(samples[i])
      i += step
    }
    val last = samples.last()
    if (out.last() !== last) {
      out.add(last)
    }
    return out
  }

  private fun formatDistance(fmt: Formatter, row: ActivityRow): String {
    return fmt.formatDistance(Formatter.Format.TXT_SHORT, Math.round(row.totalDistance))
  }

  private fun formatTime(fmt: Formatter, row: ActivityRow): String {
    return fmt.formatElapsedTime(Formatter.Format.TXT_SHORT, row.totalTime)
  }

  private fun formatPace(fmt: Formatter, row: ActivityRow): String {
    val pace = SimilarRunMatcher.paceSecPerKm(row.totalDistance, row.totalTime)
    if (pace <= 0) {
      return "--"
    }
    return fmt.formatPaceFromSecPerKm(Formatter.Format.TXT_SHORT, pace)
  }

  private fun formatHr(hr: Int): String = if (hr > 0) hr.toString() else "--"

  private fun formatElevation(fmt: Formatter, meters: Double): String {
    if (meters <= 0) {
      return "--"
    }
    return fmt.formatElevation(Formatter.Format.TXT_SHORT, meters)
  }

  private fun formatTemperature(act: DetailActivity, tempC: Double?): String {
    if (!RunConditionsAdjuster.hasTemperature(tempC)) {
      return act.getString(R.string.compare_temperature_unavailable)
    }
    return "${kotlin.math.round(tempC!!).toInt()}°C"
  }

  private fun formatAdjustedPace(fmt: Formatter, row: ActivityRow, conditions: RunConditions): String {
    val pace = conditions.adjustedPaceSecPerKm(row.totalDistance, row.totalTime)
    if (pace <= 0) {
      return "--"
    }
    return fmt.formatPaceFromSecPerKm(Formatter.Format.TXT_SHORT, pace)
  }

  private class CompareLoad(
      val current: ActivityRow?,
      val ranked: List<ActivityRow>,
      val allSimilar: List<ActivityRow>,
      val comparisons: List<SegmentComparer.Comparison>,
      val conditions: Map<Long, RunConditions>,
  )

  private inner class SimilarAdapter(
      private val onClick: (ActivityRow) -> Unit,
  ) : RecyclerView.Adapter<SimilarAdapter.Holder>() {
    private var items: List<ActivityRow> = emptyList()
    private var selectedId: Long = -1

    fun setItems(rows: List<ActivityRow>, selectedId: Long) {
      items = rows
      this.selectedId = selectedId
      notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
      val view =
          LayoutInflater.from(parent.context)
              .inflate(R.layout.detail_compare_row, parent, false)
      return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
      val row = items[position]
      val fmt = formatter ?: return
      holder.date.text = fmt.formatDateTime(row.startTime)
      holder.distance.text = formatDistance(fmt, row)
      holder.pace.text = formatPace(fmt, row)
      holder.hr.text = if (row.avgHr > 0) "${row.avgHr}" else "--"
      holder.itemView.alpha = if (row.activityId == selectedId) 1f else 0.7f
      holder.itemView.setOnClickListener { onClick(row) }
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      val date: TextView = itemView.findViewById(R.id.compare_row_date)
      val distance: TextView = itemView.findViewById(R.id.compare_row_distance)
      val pace: TextView = itemView.findViewById(R.id.compare_row_pace)
      val hr: TextView = itemView.findViewById(R.id.compare_row_hr)
    }
  }

  companion object {
    private const val MAX_GRAPH_POINTS = 150
  }
}
