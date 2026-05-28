package org.runnerup.features;

/** GPS start/stop and GPS status UI for {@link StartFragment}. */
final class StartGpsController {

  private final StartFragment fragment;

  StartGpsController(StartFragment fragment) {
    this.fragment = fragment;
  }

  void startGps() {
    fragment.performStartGps();
  }

  void stopGps() {
    fragment.performStopGps();
  }

  void updateGpsView() {
    fragment.performUpdateGpsView();
  }

  void updateSatelliteInfo() {
    fragment.performUpdateSatelliteInfo();
  }
}
