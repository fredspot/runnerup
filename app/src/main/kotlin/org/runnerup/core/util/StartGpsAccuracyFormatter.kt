/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.core.util

import android.content.Context
import android.location.Location
import android.os.Build
import java.util.Locale
import org.runnerup.BuildConfig
import org.runnerup.tracking.Tracker

object StartGpsAccuracyFormatter {

  @JvmStatic
  fun format(
      context: Context,
      formatter: Formatter,
      tracker: Tracker?,
      accuracy: Float,
  ): String {
    var res = ""
    if (accuracy > 0) {
      val accString = formatter.formatElevation(Formatter.Format.TXT_LONG, accuracy.toDouble())
      val elevation = tracker?.currentElevation
      res =
          if (elevation != null) {
            String.format(
                Locale.getDefault(),
                context.getString(org.runnerup.common.R.string.GPS_accuracy_elevation),
                accString,
                formatter.formatElevation(Formatter.Format.TXT_LONG, elevation),
            )
          } else {
            String.format(
                Locale.getDefault(),
                context.getString(org.runnerup.common.R.string.GPS_accuracy_no_elevation),
                accString,
            )
          }
    }
    if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && tracker != null) {
      val location: Location? = tracker.lastKnownLocation
      if (location != null) {
        res +=
            String.format(
                Locale.getDefault(),
                " [%1\$s, %2\$s/%3\$s/s, %4$.1f/%5$.1f deg]",
                formatter.formatElevation(
                    Formatter.Format.TXT_LONG,
                    location.verticalAccuracyMeters.toDouble(),
                ),
                formatter.formatElevation(Formatter.Format.TXT_SHORT, location.speed.toDouble()),
                formatter.formatElevation(
                    Formatter.Format.TXT_LONG,
                    location.speedAccuracyMetersPerSecond.toDouble(),
                ),
                location.bearing,
                location.bearingAccuracyDegrees,
            )
      }
    }
    return res
  }
}
