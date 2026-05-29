package org.runnerup.core.util

import android.view.View
import android.widget.TextView

/** Binds distance, elapsed time, and pace/speed for activity list rows. */
object ActivitySummaryBinder {
  @JvmStatic
  fun bind(
      formatter: Formatter,
      distanceView: TextView,
      timeView: TextView,
      paceView: TextView,
      distanceMeters: Double,
      durationSeconds: Long,
  ) {
    bind(
        formatter,
        distanceView,
        timeView,
        paceView,
        Formatter.Format.TXT_SHORT,
        Formatter.Format.TXT_SHORT,
        distanceMeters,
        durationSeconds,
    )
  }

  @JvmStatic
  fun bind(
      formatter: Formatter,
      distanceView: TextView,
      timeView: TextView,
      paceView: TextView,
      distanceFormat: Formatter.Format,
      paceFormat: Formatter.Format,
      distanceMeters: Double,
      durationSeconds: Long,
  ) {
    distanceView.text =
        formatter.formatDistance(distanceFormat, kotlin.math.round(distanceMeters).toLong())
    timeView.text = formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, durationSeconds)
    if (durationSeconds > 0) {
      paceView.text =
          formatter.formatVelocityByPreferredUnit(
              paceFormat,
              distanceMeters / durationSeconds,
          )
      paceView.visibility = View.VISIBLE
    } else {
      paceView.text = ""
    }
  }

  @JvmStatic
  fun bindActivityHeader(
      formatter: Formatter,
      distanceView: TextView,
      timeView: TextView,
      paceView: TextView,
      paceSeparator: View?,
      distanceMeters: Double,
      durationSeconds: Long,
  ) {
    distanceView.text =
        formatter.formatDistance(Formatter.Format.TXT_SHORT, kotlin.math.round(distanceMeters).toLong())
    if (durationSeconds > 0) {
      timeView.text = formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, durationSeconds)
      paceView.visibility = View.VISIBLE
      paceSeparator?.visibility = View.VISIBLE
      paceView.text =
          formatter.formatVelocityByPreferredUnit(
              Formatter.Format.TXT_LONG,
              distanceMeters / durationSeconds,
          )
    } else {
      timeView.text =
          if (durationSeconds == 0L && distanceMeters > 0) {
            ""
          } else {
            formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, durationSeconds)
          }
      paceView.visibility = View.GONE
      paceSeparator?.visibility = View.GONE
    }
  }
}
