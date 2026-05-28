package org.runnerup.features;

/** Heart-rate indicator and battery notification for {@link StartFragment}. */
final class StartHrController {

  private final StartFragment fragment;

  StartHrController(StartFragment fragment) {
    this.fragment = fragment;
  }

  boolean updateHrView() {
    return fragment.performUpdateHrView();
  }

  void updateHrIndicator() {
    fragment.performUpdateHrIndicator();
  }

  void notificationBatteryLevel(int batteryLevel) {
    fragment.performNotificationBatteryLevel(batteryLevel);
  }
}
