package org.runnerup.tracking;

public interface GpsInformation {
  float getGpsAccuracy();

  int getSatellitesAvailable();

  int getSatellitesFixed();
}
