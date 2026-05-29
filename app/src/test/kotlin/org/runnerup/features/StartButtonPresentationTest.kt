package org.runnerup.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.runnerup.R

class StartButtonPresentationTest {

  @Test
  fun notStarted_showsStartGps() {
    val p = StartButtonPresentation.resolve(gpsStarted = false, gpsFixed = false, trackerConnected = false)
    assertEquals("Start GPS", p.label)
    assertTrue(p.enabled)
    assertEquals(R.drawable.button_start_gps, p.backgroundResId)
  }

  @Test
  fun startedNotFixed_showsDisabledStartActivity() {
    val p = StartButtonPresentation.resolve(gpsStarted = true, gpsFixed = false, trackerConnected = false)
    assertEquals("Start Activity", p.label)
    assertFalse(p.enabled)
    assertEquals(R.drawable.button_start_gps_disabled, p.backgroundResId)
  }

  @Test
  fun startedFixedNotConnected_showsDisabledStartActivity() {
    val p = StartButtonPresentation.resolve(gpsStarted = true, gpsFixed = true, trackerConnected = false)
    assertEquals("Start Activity", p.label)
    assertFalse(p.enabled)
  }

  @Test
  fun ready_showsEnabledStartActivity() {
    val p = StartButtonPresentation.resolve(gpsStarted = true, gpsFixed = true, trackerConnected = true)
    assertEquals("Start Activity", p.label)
    assertTrue(p.enabled)
    assertEquals(R.drawable.button_start_activity, p.backgroundResId)
  }
}
