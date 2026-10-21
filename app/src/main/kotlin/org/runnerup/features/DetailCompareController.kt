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
import java.util.Locale
import org.runnerup.R
import org.runnerup.analytics.ComparableSegmentLoader
import org.runnerup.analytics.DailyWeatherStore
import org.runnerup.analytics.MonthlyComparisonCalculator
import org.runnerup.analytics.OpenMeteoWeatherClient
import org.runnerup.analytics.RunConditions
import org.runnerup.analytics.RunConditionsAdjuster
import org.runnerup.analytics.RunEngineStats
import org.runnerup.analytics.RunImprovementAssessor
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

/** Compare tab: similar distance/pace runs with HR overlay, weather/humidity adjustment, and segments. */
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
  private var weatherSyncStarted = false

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
    if (content?.visibility != View.VISIBLE) {
      emptyHint?.visibility = View.VISIBLE
      emptyHint?.text = "Loading comparison…"
    }
    val zoneBounds = MonthlyComparisonCalculator.resolveZoneBounds(HRZones(host))
    BgTasks.runDb(
        {
          val currentRow = RunningActivityReader.getActivityWithHr(database, id)
          val allRunning =
              if (currentRow != null) {
                RunningActivityReader.getAllRunningActivitiesExcept(database, id)
              } else {
                emptyList()
              }
          val distanceCandidates =
              if (currentRow != null && currentRow.totalDistance > 0) {
                val matched =
                    allRunning.filter { row ->
                      row.totalDistance > 0 &&
                          kotlin.math.abs(row.totalDistance - currentRow.totalDistance) /
                              currentRow.totalDistance <= 0.35
                    }
                matched.ifEmpty { allRunning.take(30) }
              } else {
                emptyList()
              }
          val candidateIds = ArrayList<Long>(distanceCandidates.size + 1)
          candidateIds.add(id)
          for (row in distanceCandidates) {
            candidateIds.add(row.activityId)
          }
          val loadedConditions = ComparableSegmentLoader.loadConditions(database, candidateIds)
          val allSimilar =
              SimilarRunMatcher.findBestComparable(currentRow, distanceCandidates, loadedConditions)
          val ranked = SimilarRunMatcher.cap(allSimilar, SimilarRunMatcher.MAX_RESULTS)
          val fastSegIds = java.util.LinkedHashSet<Long>()
          fastSegIds.add(id)
          for (r in allSimilar) fastSegIds.add(r.activityId)
          for (r in allRunning.take(60)) fastSegIds.add(r.activityId)
          val fastSegments =
              ComparableSegmentLoader.loadForActivities(
                  database,
                  ArrayList(fastSegIds),
                  zoneBounds,
              )
          val currentSegments = fastSegments.filter { seg -> seg.activityId == id }
          val historical = fastSegments.filter { seg -> seg.activityId != id }
          val comparisons = SegmentComparer.compareSmart(currentSegments, historical)
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
          BgTasks.runDb(
              {
                val allSegments = ComparableSegmentLoader.loadRunning(database, zoneBounds)
                val currentSegments = allSegments.filter { seg -> seg.activityId == id }
                val historical = allSegments.filter { seg -> seg.activityId != id }
                SegmentComparer.compareSmart(currentSegments, historical)
              },
              { fullComparisons ->
                val act = activity
                val fmt = formatter
                val cur = current
                if (act != null && !act.isFinishing && fmt != null && fullComparisons.isNotEmpty()) {
                  segmentComparisons = fullComparisons
                  bindSegments(act, fmt)
                  if (cur != null) {
                    bindEngine(act, cur)
                    bindVerdict(act, cur)
                  }
                }
              },
          )
          maybeSyncWeather(database, result.current, result.ranked)
        },
    )
  }

  private fun maybeSyncWeather(
      database: SQLiteDatabase,
      currentRow: ActivityRow?,
      peers: List<ActivityRow>,
  ) {
    if (weatherSyncStarted || currentRow == null) return
    val missingAny =
        (listOf(currentRow) + peers).any { row ->
          val cond = conditions[row.activityId]
          cond == null ||
              !RunConditionsAdjuster.hasTemperature(cond.avgTempC) ||
              !RunConditionsAdjuster.hasHumidity(cond.avgHumidityPct)
        }
    if (!missingAny) return
    weatherSyncStarted = true
    BgTasks.runNetwork(
        {
          val client = OpenMeteoWeatherClient()
          var fetched = 0
          for (row in (listOf(currentRow) + peers)) {
            val day =
                DailyWeatherStore.ensureForActivity(
                    database,
                    row.activityId,
                    row.startTime,
                    client,
                )
            if (day != null) fetched++
          }
          fetched += DailyWeatherStore.syncMissingRunDays(database, client, 15)
          fetched
        },
        { fetchedCount ->
          if (fetchedCount > 0 && activity?.isFinishing == false) {
            loadSimilar()
          }
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
    val hasAnyComparison = hasSimilar || segmentComparisons.isNotEmpty()
    act.findViewById<View>(R.id.compare_engine).visibility =
        if (hasAnyComparison) View.VISIBLE else View.GONE
    act.findViewById<View>(R.id.compare_summary).visibility =
        if (hasAnyComparison) View.VISIBLE else View.GONE
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
    if (hasAnyComparison) {
      bindEngine(act, currentRow)
      bindSummary(act, fmt, currentRow, selected)
      if (hasSimilar) {
        refreshGraph()
      }
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
    val snap = RunEngineStats.compute(thisRun, similarForStats, conditions)
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
    val segSummary = SegmentComparer.summarizeRun(segmentComparisons)
    if (segSummary != null && segSummary.avgHr > 0 && segSummary.avgBaselineHr > 0) {
      val d = segSummary.hrDeltaBpm
      val dStr = if (d > 0) "+$d bpm" else "$d bpm"
      hint.text =
          act.getString(R.string.compare_engine_subtitle) +
              "\nAll ${segSummary.matchedSegmentCount}x1km segments (${segSummary.totalHistoricalSegmentMatches} matches): " +
              "${segSummary.avgHr} vs ${segSummary.avgBaselineHr} bpm ($dStr)"
    } else {
      hint.setText(R.string.compare_engine_subtitle)
    }
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
    act.findViewById<TextView>(R.id.compare_this_avg_elevation)?.text =
        formatAvgElevation(fmt, thisConditions.avgElevationM)
    act.findViewById<TextView>(R.id.compare_other_avg_elevation)?.text =
        otherConditions?.let { formatAvgElevation(fmt, it.avgElevationM) } ?: "--"
    act.findViewById<TextView>(R.id.compare_this_temperature).text =
        formatTemperature(act, thisConditions.avgTempC)
    act.findViewById<TextView>(R.id.compare_other_temperature).text =
        otherConditions?.let { formatTemperature(act, it.avgTempC) } ?: "--"
    act.findViewById<TextView>(R.id.compare_this_humidity)?.text =
        formatHumidity(thisConditions.avgHumidityPct)
    act.findViewById<TextView>(R.id.compare_other_humidity)?.text =
        otherConditions?.let { formatHumidity(it.avgHumidityPct) } ?: "--"
    act.findViewById<TextView>(R.id.compare_this_adjusted_pace).text =
        formatAdjustedPace(fmt, thisRun, thisConditions)
    act.findViewById<TextView>(R.id.compare_other_adjusted_pace).text =
        if (other != null && otherConditions != null) {
          formatAdjustedPace(fmt, other, otherConditions)
        } else {
          "--"
        }
    bindVerdict(act, thisRun)
  }

  private fun bindVerdict(act: DetailActivity, thisRun: ActivityRow) {
    val verdictView = act.findViewById<TextView>(R.id.compare_improvement_verdict) ?: return
    val segSummary = SegmentComparer.summarizeRun(segmentComparisons)
    val verdict = RunImprovementAssessor.assess(thisRun, similarForStats, conditions)
    val betterColor = ContextCompat.getColor(act, R.color.colorPrimary)
    val worseColor = Color.parseColor("#F59E0B")

    if (segSummary != null && segSummary.matchedSegmentCount > 0) {
      verdictView.visibility = View.VISIBLE
      val savedSec = segSummary.paceSavedSecPerKm
      val absSecStr =
          String.format(Locale.US, "%ds", kotlin.math.round(kotlin.math.abs(savedSec)).toInt())
      val aero = segSummary.efficiencyGainPct
      val isImproved = aero >= 0.0 && (savedSec >= -2.0 || segSummary.hrDeltaBpm <= 0)
      verdictView.setTextColor(if (isImproved) betterColor else worseColor)
      val paceWord = if (savedSec >= 0) "faster" else "slower"
      val hrPart =
          if (segSummary.avgHr > 0 && segSummary.avgBaselineHr > 0) {
            val d = segSummary.hrDeltaBpm
            val sign = if (d > 0) "+$d" else "$d"
            " · ${segSummary.avgHr} vs ${segSummary.avgBaselineHr} bpm ($sign bpm)"
          } else {
            ""
          }
      val effPart =
          if (aero != 0.0) {
            String.format(Locale.US, " · %+.1f%% efficiency", aero)
          } else {
            ""
          }
      verdictView.text =
          "All ${segSummary.matchedSegmentCount}x1km segments (${segSummary.totalHistoricalSegmentMatches} matches / ${segSummary.distinctHistoricalRuns} runs): " +
              "$absSecStr/km $paceWord$hrPart$effPart (${segSummary.segmentsBetterCount}/${segSummary.matchedSegmentCount} km stronger)"
      return
    }

    if (!verdict.hasData() || verdict.adjustedPaceSavedSecPerKm == null) {
      verdictView.visibility = View.GONE
      return
    }
    verdictView.visibility = View.VISIBLE
    val savedSec = verdict.adjustedPaceSavedSecPerKm
    val absSecStr = String.format(Locale.US, "%ds", kotlin.math.round(kotlin.math.abs(savedSec)).toInt())
    val aero = verdict.aerobicGainPct
    val isImproved = (aero != null && aero >= 0.0) || (aero == null && savedSec >= 0.0)
    verdictView.setTextColor(if (isImproved) betterColor else worseColor)
    if (aero != null) {
      val aeroStr = String.format(Locale.US, "%+.1f%%", aero)
      verdictView.text =
          if (savedSec >= 0) {
            act.getString(R.string.compare_verdict_better, absSecStr, aeroStr, verdict.peerCount)
          } else {
            act.getString(R.string.compare_verdict_slower, absSecStr, aeroStr, verdict.peerCount)
          }
    } else {
      verdictView.text =
          if (savedSec >= 0) {
            act.getString(R.string.compare_verdict_pace_only_better, absSecStr, verdict.peerCount)
          } else {
            act.getString(R.string.compare_verdict_pace_only_slower, absSecStr, verdict.peerCount)
          }
    }
  }

  private fun bindSegments(act: DetailActivity, fmt: Formatter) {
    val list = act.findViewById<LinearLayout>(R.id.compare_segments_list) ?: return
    val empty = act.findViewById<TextView>(R.id.compare_segments_empty)
    val title = act.findViewById<TextView>(R.id.compare_segments_title)
    list.removeAllViews()
    if (segmentComparisons.isEmpty()) {
      empty?.visibility = View.VISIBLE
      title?.setText(R.string.compare_segments_title)
      return
    }
    empty?.visibility = View.GONE
    val dp = act.resources.displayMetrics.density
    val summary = SegmentComparer.summarizeRun(segmentComparisons)
    if (summary != null) {
      title?.text =
          "Segments (1 km) — ${summary.matchedSegmentCount} km · ${summary.totalHistoricalSegmentMatches} matches (${summary.distinctHistoricalRuns} runs)"
      val summaryCard = TextView(act)
      summaryCard.setBackgroundResource(R.drawable.card_background)
      val pad = (10 * dp).toInt()
      summaryCard.setPadding(pad, pad, pad, pad)
      val lp =
          LinearLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.WRAP_CONTENT,
          )
      lp.bottomMargin = (6 * dp).toInt()
      summaryCard.layoutParams = lp
      summaryCard.textSize = 13f
      val isBetter =
          summary.efficiencyGainPct >= 0.0
              && (summary.paceSavedSecPerKm >= -2.0 || summary.hrDeltaBpm <= 0)
      summaryCard.setTextColor(
          if (isBetter) {
            ContextCompat.getColor(act, R.color.colorPrimary)
          } else {
            ContextCompat.getColor(act, R.color.colorText)
          },
      )
      val adjPaceStr =
          fmt.formatPaceFromSecPerKm(Formatter.Format.TXT_SHORT, summary.avgAdjustedPaceSecPerKm)
      val basePaceStr =
          fmt.formatPaceFromSecPerKm(
              Formatter.Format.TXT_SHORT,
              summary.avgBaselineAdjustedPaceSecPerKm,
          )
      val deltaSec = kotlin.math.round(summary.paceSavedSecPerKm).toInt()
      val paceDeltaLabel =
          when {
            deltaSec > 0 -> "${deltaSec}s/km faster"
            deltaSec < 0 -> "${-deltaSec}s/km slower"
            else -> "same pace"
          }
      val hrSummary =
          if (summary.avgHr > 0 && summary.avgBaselineHr > 0) {
            val d = summary.hrDeltaBpm
            val dStr = if (d > 0) "+$d bpm" else "$d bpm"
            " · HR ${summary.avgHr} vs ${summary.avgBaselineHr} bpm ($dStr)"
          } else {
            ""
          }
      val effSummary =
          if (summary.efficiencyGainPct != 0.0) {
            String.format(Locale.US, " · %+.1f%% aerobic eff.", summary.efficiencyGainPct)
          } else {
            ""
          }
      summaryCard.text =
          "All-Segments Performance (${summary.segmentsBetterCount}/${summary.matchedSegmentCount} km stronger than usual)\n" +
              "Avg 1 km: $adjPaceStr vs $basePaceStr ($paceDeltaLabel)$hrSummary$effSummary\n" +
              "Priority: 1. Pace → 2. ΔElev (±10m/km) → 3. Altitude (<500m same) → 4. Temp → 5. Humidity"
      list.addView(summaryCard)
    }

    for (comparison in segmentComparisons) {
      val row = TextView(act)
      row.setTextColor(ContextCompat.getColor(act, R.color.colorText))
      row.textSize = 13f
      row.setPadding(0, (5 * dp).toInt(), 0, (2 * dp).toInt())
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
    // 1. Pace (raw & adjusted vs matched baseline)
    val rawPaceStr = fmt.formatPaceFromSecPerKm(Formatter.Format.TXT_SHORT, segment.paceSecPerKm)
    val adjPaceStr =
        fmt.formatPaceFromSecPerKm(Formatter.Format.TXT_SHORT, segment.adjustedPaceSecPerKm)
    val basePaceStr =
        fmt.formatPaceFromSecPerKm(
            Formatter.Format.TXT_SHORT,
            comparison.medianAdjustedPaceSecPerKm,
        )
    val paceDeltaSec =
        kotlin.math.round(comparison.medianAdjustedPaceSecPerKm - segment.adjustedPaceSecPerKm)
            .toInt()
    val paceDeltaTag =
        when {
          paceDeltaSec > 0 -> "-${paceDeltaSec}s"
          paceDeltaSec < 0 -> "+${-paceDeltaSec}s"
          else -> "0s"
        }
    val pacePart =
        if (kotlin.math.abs(segment.paceSecPerKm - segment.adjustedPaceSecPerKm) >= 2.0) {
          "$rawPaceStr (adj $adjPaceStr vs $basePaceStr, $paceDeltaTag)"
        } else {
          "$rawPaceStr vs $basePaceStr ($paceDeltaTag)"
        }

    // Heart rate vs matched segments
    val hrPart =
        if (segment.avgHr > 0 && comparison.medianHr > 0) {
          val hrDiff = segment.avgHr - comparison.medianHr
          val hrDiffStr = if (hrDiff > 0) "+$hrDiff" else "$hrDiff"
          " · ${segment.avgHr} vs ${comparison.medianHr} bpm ($hrDiffStr)"
        } else if (segment.avgHr > 0) {
          " · ${segment.avgHr} bpm"
        } else {
          ""
        }

    // 2. Change in elevation over 1 km (±10 m threshold for drastic climb/descent)
    val netInt = kotlin.math.round(segment.netElevationM).toInt()
    val elevChangePart =
        when {
          kotlin.math.abs(segment.netElevationM) < SegmentComparer.DRASTIC_ELEVATION_CHANGE_M ->
              "flat (${if (netInt >= 0) "+$netInt" else "$netInt"}m)"
          segment.netElevationM >= SegmentComparer.DRASTIC_ELEVATION_CHANGE_M ->
              "▲ +${netInt}m"
          else ->
              "▼ ${netInt}m"
        }

    // 3. Elevation / Altitude (<500 m all considered the same)
    val altPart =
        if (RunConditionsAdjuster.hasAltitude(segment.avgElevationM)) {
          val altM = kotlin.math.round(segment.avgElevationM!!).toInt()
          if (segment.avgElevationM > SegmentComparer.ALTITUDE_SAME_BELOW_M) {
            " · ⛰ ${altM}m"
          } else {
            " · <500m (${altM}m)"
          }
        } else {
          ""
        }

    // 4. Temperature & 5. Humidity
    val weatherPart =
        when {
          RunConditionsAdjuster.hasTemperature(segment.avgTempC) &&
              RunConditionsAdjuster.hasHumidity(segment.avgHumidityPct) ->
              " · ${kotlin.math.round(segment.avgTempC!!).toInt()}°C ${kotlin.math.round(segment.avgHumidityPct!!).toInt()}%"
          RunConditionsAdjuster.hasTemperature(segment.avgTempC) ->
              " · ${kotlin.math.round(segment.avgTempC!!).toInt()}°C"
          else -> ""
        }

    val matchBadge = " · [${comparison.matchCount} matches]"
    return "$km: $pacePart$hrPart · $elevChangePart$altPart$weatherPart$matchBadge"
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
        activity?.getString(R.string.compare_adjusted_pace)
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
    fun plotPace(row: ActivityRow): Double {
      val cond = conditions[row.activityId]
      val adj = cond?.adjustedPaceSecPerKm(row.totalDistance, row.totalTime) ?: 0.0
      return if (adj > 0) adj else SimilarRunMatcher.paceSecPerKm(row.totalDistance, row.totalTime)
    }
    val withHr =
        similarRows
            .filter { it.avgHr > 0 }
            .sortedBy { plotPace(it) }
    if (withHr.isNotEmpty()) {
      val points =
          withHr
              .map {
                DataPoint(
                    plotPace(it),
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
      val pace = plotPace(currentRow)
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

  private fun formatAvgElevation(fmt: Formatter, meters: Double?): String {
    if (!RunConditionsAdjuster.hasAltitude(meters)) {
      return "--"
    }
    return fmt.formatElevation(Formatter.Format.TXT_SHORT, meters!!)
  }

  private fun formatTemperature(act: DetailActivity, tempC: Double?): String {
    if (!RunConditionsAdjuster.hasTemperature(tempC)) {
      return act.getString(R.string.compare_temperature_unavailable)
    }
    return "${kotlin.math.round(tempC!!).toInt()}°C"
  }

  private fun formatHumidity(humidityPct: Double?): String {
    if (!RunConditionsAdjuster.hasHumidity(humidityPct)) {
      return "--"
    }
    return "${kotlin.math.round(humidityPct!!).toInt()}%"
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
