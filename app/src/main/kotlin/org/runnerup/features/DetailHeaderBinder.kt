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

import android.content.ContentValues
import android.view.View
import android.widget.TextView
import org.runnerup.common.util.Constants
import org.runnerup.core.util.ActivitySummaryBinder
import org.runnerup.core.util.Formatter
import org.runnerup.ui.common.widget.TitleSpinner

/** Binds the activity summary header on the detail screen. */
internal object DetailHeaderBinder {

  @JvmStatic
  fun bind(
      formatter: Formatter,
      distanceView: TextView,
      timeView: TextView,
      paceView: TextView,
      paceSeparator: View?,
      manualDistance: TitleSpinner,
      sport: TitleSpinner,
      data: ContentValues,
      fromManualDistance: Boolean,
  ) {
    var distanceMeters = 0.0
    if (data.containsKey(Constants.DB.ACTIVITY.DISTANCE)) {
      distanceMeters = data.getAsDouble(Constants.DB.ACTIVITY.DISTANCE)
      if (!fromManualDistance) {
        val distance = distanceMeters.toInt()
        manualDistance.setValue(distance.toLong().toString())
        manualDistance.setValue(distance)
      }
    } else {
      distanceView.text = ""
    }

    var durationSeconds = 0L
    if (data.containsKey(Constants.DB.ACTIVITY.TIME)) {
      durationSeconds = data.getAsLong(Constants.DB.ACTIVITY.TIME)
    }

    if (data.containsKey(Constants.DB.ACTIVITY.DISTANCE)) {
      ActivitySummaryBinder.bindActivityHeader(
          formatter,
          distanceView,
          timeView,
          paceView,
          paceSeparator,
          distanceMeters,
          durationSeconds,
      )
    } else {
      distanceView.text = ""
      timeView.text = ""
      paceView.visibility = View.GONE
      paceSeparator?.visibility = View.GONE
    }

    if (data.containsKey(Constants.DB.ACTIVITY.SPORT)) {
      sport.setValue(data.getAsInteger(Constants.DB.ACTIVITY.SPORT))
    }
  }
}
