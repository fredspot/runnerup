package org.runnerup.features

/** Start-screen view updates (GPS/HR/Wear, start button). */
internal class StartUiState(
    private val fragment: StartFragment,
    private val gps: StartGpsController,
    private val hr: StartHrController,
) {

  fun updateView() {
    if (fragment.view == null) {
      return
    }
    fragment.performUpdateNewStartButton()
    gps.updateGpsView()
    hr.updateHrIndicator()
    gps.updateSatelliteInfo()
    val hrPresent = hr.updateHrView()
    val wearPresent = fragment.performUpdateWearOsView()
    fragment.performUpdateNoDevicesConnected(!hrPresent && !wearPresent)
  }
}
