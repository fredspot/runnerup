package org.runnerup.features

/** Start-screen view updates (GPS/HR/Wear, start button). */
internal class StartUiState(
    private val fragment: StartFragment,
    private val gps: StartGpsController,
    private val runUi: StartRunUiController,
) {

  fun updateView() {
    if (fragment.view == null) {
      return
    }
    runUi.updateNewStartButton()
    gps.updateGpsView()
    runUi.updateHrIndicator()
    gps.updateSatelliteInfo()
    val hrPresent = runUi.updateHrView()
    val wearPresent = runUi.updateWearOsView()
    runUi.updateNoDevicesConnected(!hrPresent && !wearPresent)
  }
}
