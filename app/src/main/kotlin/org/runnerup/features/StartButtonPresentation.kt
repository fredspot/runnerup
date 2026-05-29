/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import androidx.annotation.DrawableRes
import org.runnerup.R

/** Pure start-button label/background/enabled rules for the Run tab. */
internal data class StartButtonPresentation(
    val label: String,
    @DrawableRes val backgroundResId: Int,
    val enabled: Boolean,
) {
  companion object {
    fun resolve(
        gpsStarted: Boolean,
        gpsFixed: Boolean,
        trackerConnected: Boolean,
    ): StartButtonPresentation =
        when {
          gpsStarted && gpsFixed && trackerConnected ->
              StartButtonPresentation(
                  label = "Start Activity",
                  backgroundResId = R.drawable.button_start_activity,
                  enabled = true,
              )
          gpsStarted ->
              StartButtonPresentation(
                  label = "Start Activity",
                  backgroundResId = R.drawable.button_start_gps_disabled,
                  enabled = false,
              )
          else ->
              StartButtonPresentation(
                  label = "Start GPS",
                  backgroundResId = R.drawable.button_start_gps,
                  enabled = true,
              )
        }
  }
}
