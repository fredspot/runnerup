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

import android.content.Context
import android.database.Cursor
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Locale
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.core.util.Formatter

/** Binds monthly comparison summary rows loaded from [Constants.DB.MONTHLY_COMPARISON]. */
object MonthlyComparisonBinder {

  class Data {
    @JvmField var currentAvgPace: Double? = null
    @JvmField var currentTotalKm: Double? = null
    @JvmField var currentAvgBpm: Int = 0
    @JvmField var currentPbCount: Int = 0
    @JvmField var currentAvgDistancePerRun: Double? = null
    @JvmField var currentTop25Count: Int = 0

    @JvmField var otherAvgPace: Double? = null
    @JvmField var otherTotalKm: Double? = null
    @JvmField var otherAvgBpm: Int = 0
    @JvmField var otherPbCount: Double? = null
    @JvmField var otherAvgDistancePerRun: Double? = null
    @JvmField var otherTop25Count: Double? = null

    @JvmField var bestAvgPace: Double? = null
    @JvmField var bestAvgPaceMonth: String? = null
    @JvmField var bestTotalKm: Double? = null
    @JvmField var bestTotalKmMonth: String? = null
    @JvmField var bestAvgDistancePerRun: Double? = null
    @JvmField var bestAvgDistancePerRunMonth: String? = null
    @JvmField var bestAvgBpm: Int = 0
    @JvmField var bestAvgBpmMonth: String? = null
    @JvmField var bestPbCount: Int = 0
    @JvmField var bestPbCountMonth: String? = null
    @JvmField var bestTop25Count: Int = 0
    @JvmField var bestTop25CountMonth: String? = null

    @JvmField var currentZonePace: DoubleArray = DoubleArray(5)
    @JvmField var otherZonePace: DoubleArray = DoubleArray(5)
    @JvmField var bestZonePace: DoubleArray = DoubleArray(5)
    @JvmField var bestZoneMonth: Array<String?> = arrayOfNulls(5)
  }

  @JvmStatic
  fun readFromCursor(cursor: Cursor): Data {
    val data = Data()
    data.currentAvgPace = getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE)
    data.currentTotalKm = getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_TOTAL_KM)
    data.currentAvgBpm = getIntColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_BPM)
    data.currentPbCount = getIntColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_PB_COUNT)
    data.currentAvgDistancePerRun =
        getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_DISTANCE_PER_RUN)
    data.currentTop25Count =
        getIntColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_TOP25_COUNT)

    data.otherAvgPace = getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE)
    data.otherTotalKm = getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_TOTAL_KM)
    data.otherAvgBpm = getIntColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_BPM)
    data.otherPbCount = getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_PB_COUNT)
    data.otherAvgDistancePerRun =
        getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_DISTANCE_PER_RUN)
    data.otherTop25Count =
        getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_TOP25_COUNT)

    data.bestAvgPace = getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE)
    data.bestAvgPaceMonth =
        getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_MONTH)
    data.bestTotalKm = getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_TOTAL_KM)
    data.bestTotalKmMonth =
        getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_TOTAL_KM_MONTH)
    data.bestAvgDistancePerRun =
        getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_DISTANCE_PER_RUN)
    data.bestAvgDistancePerRunMonth =
        getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_DISTANCE_PER_RUN_MONTH)
    data.bestAvgBpm = getIntColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_BPM)
    data.bestAvgBpmMonth =
        getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_BPM_MONTH)
    data.bestPbCount = getIntColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_PB_COUNT)
    data.bestPbCountMonth =
        getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_PB_COUNT_MONTH)
    data.bestTop25Count = getIntColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_TOP25_COUNT)
    data.bestTop25CountMonth =
        getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_TOP25_COUNT_MONTH)

    data.currentZonePace = readZonePaceCurrent(cursor)
    data.otherZonePace = readZonePaceOther(cursor)
    data.bestZonePace = readZonePaceBest(cursor)
    data.bestZoneMonth = readZonePaceBestMonth(cursor)
    return data
  }

  @JvmStatic
  fun hasMeaningfulData(data: Data): Boolean {
    val currentTotalKm = data.currentTotalKm
    if (currentTotalKm != null && currentTotalKm > 0) return true
    val otherTotalKm = data.otherTotalKm
    if (otherTotalKm != null && otherTotalKm > 0) return true
    val bestTotalKm = data.bestTotalKm
    if (bestTotalKm != null && bestTotalKm > 0) return true
    return false
  }

  @JvmStatic
  fun bindZoneMetricLabels(context: Context, root: View, bounds: IntArray) {
    val aerobicLabel = root.findViewById<TextView>(R.id.metric_avg_pace_zone_3)
    val easyLabel = root.findViewById<TextView>(R.id.metric_easy_pace)
    if (bounds[5] > bounds[4]) {
      aerobicLabel.text =
          context.getString(
              R.string.monthly_comparison_zone_aerobic_metric,
              3,
              bounds[4],
              bounds[5] - 1,
          )
    } else {
      aerobicLabel.text =
          context.getString(R.string.monthly_comparison_zone_pace_metric_unconfigured, 3)
    }
    if (bounds[2] > bounds[0] && bounds[3] > bounds[2]) {
      easyLabel.text =
          context.getString(
              R.string.monthly_comparison_zone_easy_metric,
              bounds[0],
              bounds[3] - 1,
          )
    } else {
      easyLabel.text =
          context.getString(R.string.monthly_comparison_zone_pace_metric_unconfigured, 1)
    }
  }

  @JvmStatic
  fun bind(context: Context, root: View, formatter: Formatter, data: Data) {
    displayCurrentMonthData(context, root, formatter, data)
    displayOtherMonthsData(context, root, formatter, data)
    displayBestMonthData(context, root, formatter, data)
    displayZonePaceSection(context, root, formatter, data)
  }

  private fun displayCurrentMonthData(
      context: Context,
      root: View,
      formatter: Formatter,
      data: Data,
  ) {
    root.findViewById<TextView>(R.id.this_month_avg_pace).text =
        formatPaceSecondsPerKm(formatter, data.currentAvgPace)
    root.findViewById<TextView>(R.id.this_month_total_km).text =
        formatKm(data.currentTotalKm)
    root.findViewById<TextView>(R.id.this_month_avg_bpm).text =
        if (data.currentAvgBpm > 0) data.currentAvgBpm.toString() else "--"
    root.findViewById<TextView>(R.id.this_month_pb_count).text =
        data.currentPbCount.toString()
    root.findViewById<TextView>(R.id.this_month_avg_distance_per_run).text =
        formatDistancePerRunKm(data.currentAvgDistancePerRun)
    root.findViewById<TextView>(R.id.this_month_top25_count).text =
        data.currentTop25Count.toString()
  }

  private fun displayOtherMonthsData(
      context: Context,
      root: View,
      formatter: Formatter,
      data: Data,
  ) {
    root.findViewById<TextView>(R.id.other_months_avg_pace).text =
        formatPaceSecondsPerKm(formatter, data.otherAvgPace)
    root.findViewById<TextView>(R.id.other_months_total_km).text =
        formatKm(data.otherTotalKm)
    root.findViewById<TextView>(R.id.other_months_avg_bpm).text =
        if (data.otherAvgBpm > 0) data.otherAvgBpm.toString() else "--"
    root.findViewById<TextView>(R.id.other_months_pb_count).text =
        if (data.otherPbCount != null) {
          String.format(Locale.US, "%.2f", data.otherPbCount)
        } else {
          "--"
        }
    root.findViewById<TextView>(R.id.other_months_avg_distance_per_run).text =
        formatDistancePerRunKm(data.otherAvgDistancePerRun)
    root.findViewById<TextView>(R.id.other_months_top25_count).text =
        if (data.otherTop25Count != null) {
          String.format(Locale.US, "%.2f", data.otherTop25Count)
        } else {
          "--"
        }
  }

  private fun displayBestMonthData(
      context: Context,
      root: View,
      formatter: Formatter,
      data: Data,
  ) {
    val bestAvgPace = data.bestAvgPace
    bindBestMetric(
        root,
        R.id.best_month_avg_pace,
        R.id.best_month_avg_pace_label,
        formatPaceSecondsPerKm(formatter, bestAvgPace),
        bestAvgPace != null && bestAvgPace > 0,
        data.bestAvgPaceMonth,
    )
    val bestTotalKm = data.bestTotalKm
    bindBestMetric(
        root,
        R.id.best_month_total_km,
        R.id.best_month_total_km_label,
        formatKm(bestTotalKm),
        bestTotalKm != null && bestTotalKm > 0,
        data.bestTotalKmMonth,
    )
    val bestAvgDistancePerRun = data.bestAvgDistancePerRun
    bindBestMetric(
        root,
        R.id.best_month_avg_distance_per_run,
        R.id.best_month_avg_distance_per_run_label,
        formatDistancePerRunKm(bestAvgDistancePerRun),
        bestAvgDistancePerRun != null && bestAvgDistancePerRun > 0,
        data.bestAvgDistancePerRunMonth,
    )
    bindBestMetric(
        root,
        R.id.best_month_avg_bpm,
        R.id.best_month_avg_bpm_label,
        if (data.bestAvgBpm > 0) data.bestAvgBpm.toString() else "--",
        data.bestAvgBpm > 0,
        data.bestAvgBpmMonth,
    )
    bindBestMetric(
        root,
        R.id.best_month_pb_count,
        R.id.best_month_pb_count_label,
        data.bestPbCount.toString(),
        true,
        data.bestPbCountMonth,
    )
    bindBestMetric(
        root,
        R.id.best_month_top25_count,
        R.id.best_month_top25_count_label,
        data.bestTop25Count.toString(),
        true,
        data.bestTop25CountMonth,
    )
  }

  private fun displayZonePaceSection(
      context: Context,
      root: View,
      formatter: Formatter,
      data: Data,
  ) {
    bindZoneRow(
        context,
        root,
        formatter,
        R.id.this_month_avg_pace_zone_3,
        R.id.other_months_avg_pace_zone_3,
        R.id.best_month_avg_pace_zone_3,
        R.id.best_month_avg_pace_zone_3_label,
        data.currentZonePace[3],
        data.otherZonePace[3],
        data.bestZonePace[3],
        data.bestZoneMonth[3],
    )
    bindZoneRow(
        context,
        root,
        formatter,
        R.id.this_month_easy_pace,
        R.id.other_months_easy_pace,
        R.id.best_month_easy_pace,
        R.id.best_month_easy_pace_label,
        compositeEasyPace(data.currentZonePace),
        compositeEasyPace(data.otherZonePace),
        compositeEasyPace(data.bestZonePace),
        bestEasyMonthLabel(data.bestZonePace, data.bestZoneMonth),
    )
  }

  private fun bindBestMetric(
      root: View,
      valueId: Int,
      labelId: Int,
      valueText: String,
      hasValue: Boolean,
      month: String?,
  ) {
    val valueView = root.findViewById<TextView>(valueId)
    val labelView = root.findViewById<TextView>(labelId)
    if (hasValue) {
      valueView.text = valueText
      labelView.text = month ?: ""
    } else {
      valueView.text = "--"
      labelView.text = ""
    }
  }

  private fun bindZoneRow(
      context: Context,
      root: View,
      formatter: Formatter,
      thisMonthId: Int,
      otherMonthId: Int,
      bestValueId: Int,
      bestLabelId: Int,
      currentPace: Double,
      otherPace: Double,
      bestPace: Double,
      bestMonth: String?,
  ) {
    val thisMonthView = root.findViewById<TextView>(thisMonthId)
    val otherMonthView = root.findViewById<TextView>(otherMonthId)
    val bestValueView = root.findViewById<TextView>(bestValueId)
    val bestLabelView = root.findViewById<TextView>(bestLabelId)

    val current = if (currentPace > 0) currentPace else null
    val other = if (otherPace > 0) otherPace else null
    val best = if (bestPace > 0) bestPace else null

    thisMonthView.text = formatPaceSecondsPerKm(formatter, current)
    otherMonthView.text = formatPaceDeltaSecondsPerKm(context, current, other)
    applyPaceDeltaColor(context, otherMonthView, current, other)

    if (best != null) {
      bestValueView.text = formatPaceSecondsPerKm(formatter, best)
      bestLabelView.text = bestMonth ?: ""
    } else {
      bestValueView.text = "--"
      bestLabelView.text = ""
    }
  }

  private fun formatPaceSecondsPerKm(formatter: Formatter, paceSecondsPerKm: Double?): String {
    if (paceSecondsPerKm == null || paceSecondsPerKm <= 0) return "--"
    return formatter.formatPaceFromSecPerKm(Formatter.Format.TXT_SHORT, paceSecondsPerKm)
  }

  private fun formatPaceDeltaSecondsPerKm(
      context: Context,
      currentSecPerKm: Double?,
      baselineSecPerKm: Double?,
  ): String {
    if (currentSecPerKm == null ||
        currentSecPerKm <= 0 ||
        baselineSecPerKm == null ||
        baselineSecPerKm <= 0) {
      return "--"
    }
    val deltaSec = currentSecPerKm - baselineSecPerKm
    if (kotlin.math.abs(deltaSec) < 1.0) {
      return context.getString(R.string.monthly_comparison_zone_pace_same)
    }
    val sec = kotlin.math.round(kotlin.math.abs(deltaSec)).toLong()
    val minutes = sec / 60
    val seconds = sec % 60
    val sign = if (deltaSec > 0) "+" else "−"
    return String.format(Locale.US, "%s%d:%02d", sign, minutes, seconds)
  }

  private fun applyPaceDeltaColor(
      context: Context,
      view: TextView,
      current: Double?,
      baseline: Double?,
  ) {
    if (current == null || baseline == null || current <= 0 || baseline <= 0) {
      view.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary))
      return
    }
    val delta = current - baseline
    view.setTextColor(
        when {
          kotlin.math.abs(delta) < 1.0 ->
              ContextCompat.getColor(context, R.color.colorTextSecondary)
          delta < 0 -> ContextCompat.getColor(context, R.color.colorAccent)
          else -> ContextCompat.getColor(context, R.color.colorTextSecondary)
        },
    )
  }

  private fun formatKm(totalKm: Double?): String {
    if (totalKm != null && totalKm > 0) {
      return String.format(Locale.US, "%.1fk", totalKm)
    }
    return "--"
  }

  private fun formatDistancePerRunKm(avgDistancePerRun: Double?): String {
    if (avgDistancePerRun != null && avgDistancePerRun > 0) {
      return String.format(Locale.US, "%.1fk", avgDistancePerRun / 1000.0)
    }
    return "--"
  }

  private fun compositeEasyPace(zonePace: DoubleArray): Double {
    var count = 0
    var sum = 0.0
    if (zonePace[1] > 0) {
      sum += zonePace[1]
      count++
    }
    if (zonePace[2] > 0) {
      sum += zonePace[2]
      count++
    }
    return if (count > 0) sum / count else 0.0
  }

  private fun bestEasyMonthLabel(
      bestZonePace: DoubleArray,
      bestZoneMonth: Array<String?>,
  ): String {
    var bestZone = 0
    var bestPace = Double.MAX_VALUE
    if (bestZonePace[1] > 0 && bestZonePace[1] < bestPace) {
      bestPace = bestZonePace[1]
      bestZone = 1
    }
    if (bestZonePace[2] > 0 && bestZonePace[2] < bestPace) {
      bestZone = 2
    }
    return if (bestZone > 0 && bestZoneMonth[bestZone] != null) {
      bestZoneMonth[bestZone]!!
    } else {
      ""
    }
  }

  private fun readZonePaceCurrent(cursor: Cursor): DoubleArray {
    val pace = DoubleArray(5)
    pace[1] =
        doubleValue(
            getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE_ZONE_1),
        )
    pace[2] =
        doubleValue(
            getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE_ZONE_2),
        )
    pace[3] =
        doubleValue(
            getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE_ZONE_3),
        )
    pace[4] =
        doubleValue(
            getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE_ZONE_4),
        )
    return pace
  }

  private fun readZonePaceOther(cursor: Cursor): DoubleArray {
    val pace = DoubleArray(5)
    pace[1] =
        doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE_ZONE_1))
    pace[2] =
        doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE_ZONE_2))
    pace[3] =
        doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE_ZONE_3))
    pace[4] =
        doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.OTHER_AVG_PACE_ZONE_4))
    return pace
  }

  private fun readZonePaceBest(cursor: Cursor): DoubleArray {
    val pace = DoubleArray(5)
    pace[1] =
        doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_1))
    pace[2] =
        doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_2))
    pace[3] =
        doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_3))
    pace[4] =
        doubleValue(getDoubleColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_4))
    return pace
  }

  private fun readZonePaceBestMonth(cursor: Cursor): Array<String?> =
      arrayOf(
          null,
          getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_1_MONTH),
          getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_2_MONTH),
          getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_3_MONTH),
          getStringColumn(cursor, Constants.DB.MONTHLY_COMPARISON.BEST_AVG_PACE_ZONE_4_MONTH),
      )

  private fun getDoubleColumn(cursor: Cursor, column: String): Double? {
    val colIndex = cursor.getColumnIndex(column)
    if (colIndex < 0 || cursor.isNull(colIndex)) return null
    return cursor.getDouble(colIndex)
  }

  private fun getIntColumn(cursor: Cursor, column: String): Int {
    val colIndex = cursor.getColumnIndex(column)
    if (colIndex < 0 || cursor.isNull(colIndex)) return 0
    return cursor.getInt(colIndex)
  }

  private fun getStringColumn(cursor: Cursor, column: String): String? {
    val colIndex = cursor.getColumnIndex(column)
    if (colIndex < 0 || cursor.isNull(colIndex)) return null
    return cursor.getString(colIndex)
  }

  private fun doubleValue(value: Double?): Double = value ?: 0.0
}
