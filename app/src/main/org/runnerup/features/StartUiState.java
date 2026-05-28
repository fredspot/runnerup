package org.runnerup.features;

/** Start-screen view updates (GPS/HR/Wear, start button). */
final class StartUiState {

  private final StartFragment fragment;
  private final StartGpsController gps;
  private final StartHrController hr;

  StartUiState(StartFragment fragment, StartGpsController gps, StartHrController hr) {
    this.fragment = fragment;
    this.gps = gps;
    this.hr = hr;
  }

  void updateView() {
    if (fragment.getView() == null) {
      return;
    }
    fragment.performUpdateNewStartButton();
    gps.updateGpsView();
    hr.updateHrIndicator();
    gps.updateSatelliteInfo();
    boolean hrPresent = hr.updateHrView();
    boolean wearPresent = fragment.performUpdateWearOsView();
    fragment.performUpdateNoDevicesConnected(!hrPresent && !wearPresent);
  }
}
