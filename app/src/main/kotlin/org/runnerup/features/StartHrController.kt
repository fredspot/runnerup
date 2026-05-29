/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

/** Heart-rate indicator and battery notification for [StartFragment]. */
internal class StartHrController(private val fragment: StartFragment) {

  fun updateHrView(): Boolean = fragment.performUpdateHrView()

  fun updateHrIndicator() {
    fragment.performUpdateHrIndicator()
  }

  fun notificationBatteryLevel(batteryLevel: Int) {
    fragment.performNotificationBatteryLevel(batteryLevel)
  }
}
