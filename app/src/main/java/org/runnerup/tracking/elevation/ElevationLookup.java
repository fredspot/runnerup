package org.runnerup.tracking.elevation;

import java.io.IOException;
import java.util.List;

/** Looks up ground elevation for GPS coordinates. */
public interface ElevationLookup {

  final class LatLng {
    public final double latitude;
    public final double longitude;

    public LatLng(double latitude, double longitude) {
      this.latitude = latitude;
      this.longitude = longitude;
    }
  }

  /**
   * Returns one elevation in metres per location. {@link Double#NaN} when the provider has no
   * value for that point.
   */
  double[] lookup(List<LatLng> locations) throws IOException;
}
