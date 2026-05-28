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
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.runnerup.R
import org.runnerup.core.notification.GpsBoundState
import org.runnerup.core.notification.GpsSearchingState
import org.runnerup.tracking.GpsStatus

/** GPS start/stop and GPS status UI for {@link StartFragment}. */
class StartGpsController(
    private val fragment: StartFragment,
) {
  private enum class GpsLevel {
    NOT_FIXED,
    POOR,
    ACCEPTABLE,
    GOOD,
  }

  fun startGps() {
    Log.v(fragment.javaClass.name, "StartFragment.startGps()")
    if (!fragment.sportWithoutGps) {
      val gpsStatus = fragment.mGpsStatus
      if (gpsStatus != null && !gpsStatus.isEnabled) {
        fragment.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
      }
      fragment.notificationStateManager.displayNotificationState(fragment.gpsSearchingState)
    }

    val gpsStatus = fragment.mGpsStatus
    if (gpsStatus != null && !gpsStatus.isStarted) {
      gpsStatus.start(fragment)
    }

    val tracker = fragment.mTracker
    if (tracker != null) {
      tracker.setWithoutGps(fragment.sportWithoutGps)
      tracker.connect()
    }
  }

  fun stopGps() {
    Log.e(fragment.javaClass.name, "StartFragment.stopGps() skipStop: " + fragment.runActivityPending)
    if (fragment.runActivityPending) {
      return
    }

    fragment.mGpsStatus?.stop(fragment)
    fragment.mTracker?.reset()
    fragment.notificationStateManager.cancelNotification()
  }

  fun updateGpsView() {
    val gpsStatus = fragment.mGpsStatus ?: return
    if (!gpsStatus.isEnabled || !gpsStatus.isStarted || fragment.sportWithoutGps) {
      if (fragment.statusDetailsShown) {
        fragment.gpsDetailMessage.setText(org.runnerup.common.R.string.GPS_indicator_off)
        fragment.gpsDetailRow.visibility = View.VISIBLE
        fragment.gpsMessage.visibility = View.GONE
      } else {
        fragment.gpsMessage.setText(org.runnerup.common.R.string.GPS_indicator_off)
        fragment.gpsMessage.visibility = View.VISIBLE
        fragment.gpsDetailRow.visibility = View.GONE
      }
      fragment.gpsIndicator.visibility = View.GONE
      fragment.gpsDetailIndicator.visibility = View.GONE
      return
    }

    if (fragment.statusDetailsShown) {
      fragment.gpsIndicator.visibility = View.GONE
      fragment.gpsMessage.visibility = View.GONE
      fragment.gpsDetailRow.visibility = View.VISIBLE
    } else {
      fragment.gpsIndicator.visibility = View.VISIBLE
      fragment.gpsMessage.visibility = View.VISIBLE
      fragment.gpsDetailRow.visibility = View.GONE
    }

    fragment.gpsDetailIndicator.visibility = View.VISIBLE

    val satFixedCount = gpsStatus.satellitesFixed
    val satAvailCount = gpsStatus.satellitesAvailable
    val accuracy = fragment.getGpsAccuracy()
    val gpsAccuracy = fragment.getGpsAccuracyString(accuracy)
    val gpsDetail =
        if (gpsAccuracy.isEmpty()) {
          fragment.getString(
              org.runnerup.common.R.string.GPS_status_no_accuracy,
              satFixedCount,
              satAvailCount,
          )
        } else {
          fragment.getString(
              org.runnerup.common.R.string.GPS_status_accuracy,
              satFixedCount,
              satAvailCount,
              gpsAccuracy,
          )
        }
    fragment.gpsDetailMessage.text = gpsDetail

    when (gpsLevel(accuracy, satFixedCount)) {
      GpsLevel.NOT_FIXED -> {
        fragment.gpsIndicator.setImageResource(R.drawable.ic_gps_0)
        fragment.gpsDetailIndicator.setImageResource(R.drawable.ic_gps_0)
        fragment.gpsMessage.setText(org.runnerup.common.R.string.Waiting_for_GPS)
      }
      GpsLevel.POOR -> {
        fragment.gpsIndicator.setImageResource(R.drawable.ic_gps_1)
        fragment.gpsDetailIndicator.setImageResource(R.drawable.ic_gps_1)
        fragment.gpsMessage.setText(org.runnerup.common.R.string.GPS_level_poor)
      }
      GpsLevel.ACCEPTABLE -> {
        fragment.gpsIndicator.setImageResource(R.drawable.ic_gps_2)
        fragment.gpsDetailIndicator.setImageResource(R.drawable.ic_gps_2)
        fragment.gpsMessage.setText(org.runnerup.common.R.string.GPS_level_acceptable)
      }
      GpsLevel.GOOD -> {
        fragment.gpsIndicator.setImageResource(R.drawable.ic_gps_3)
        fragment.gpsDetailIndicator.setImageResource(R.drawable.ic_gps_3)
        fragment.gpsMessage.setText(org.runnerup.common.R.string.GPS_level_good)
      }
    }
    if (gpsLevel(accuracy, satFixedCount) == GpsLevel.NOT_FIXED) {
      fragment.notificationStateManager.displayNotificationState(fragment.gpsSearchingState)
    } else {
      fragment.notificationStateManager.displayNotificationState(fragment.gpsBoundState)
    }
  }

  fun updateSatelliteInfo() {
    val view = fragment.view ?: return
    val satelliteInfo = view.findViewById<LinearLayout>(R.id.new_satellite_info) ?: return
    val satelliteCount = view.findViewById<TextView>(R.id.new_satellite_count) ?: return

    val gpsStatus = fragment.mGpsStatus
    if (gpsStatus != null && gpsStatus.isStarted) {
      val fixed = gpsStatus.satellitesFixed
      val available = gpsStatus.satellitesAvailable
      satelliteCount.text = "$fixed/$available"
      satelliteInfo.visibility = View.VISIBLE
    } else {
      satelliteInfo.visibility = View.GONE
    }
  }

  private fun gpsLevel(gpsAccuracyMeters: Float, sats: Int): GpsLevel {
    val gpsStatus = fragment.mGpsStatus
    if (gpsStatus == null || !gpsStatus.isFixed) {
      return GpsLevel.NOT_FIXED
    }
    if (gpsAccuracyMeters <= 7 && sats > 7) {
      return GpsLevel.GOOD
    }
    if (gpsAccuracyMeters <= 15 && sats > 4) {
      return GpsLevel.ACCEPTABLE
    }
    return GpsLevel.POOR
  }
}
