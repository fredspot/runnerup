package org.runnerup.tracking.elevation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.runnerup.tracking.elevation.ElevationLookup.LatLng;

public class OpenTopoDataClientTest {

  @Test
  public void parseElevations_readsResultsInOrder() throws Exception {
    String json =
        "{\"results\":["
            + "{\"elevation\":100.5,\"location\":{\"lat\":1,\"lng\":2}},"
            + "{\"elevation\":null,\"location\":{\"lat\":3,\"lng\":4}},"
            + "{\"elevation\":220.0}"
            + "],\"status\":\"OK\"}";
    double[] elevations = OpenTopoDataClient.parseElevations(json, 3);
    assertEquals(100.5, elevations[0], 0.01);
    assertTrue(Double.isNaN(elevations[1]));
    assertEquals(220.0, elevations[2], 0.01);
  }

  @Test
  public void formatLocations_joinsLatLngWithPipes() {
    List<LatLng> locations =
        Arrays.asList(new LatLng(45.5, -73.6), new LatLng(45.6, -73.5));
    assertEquals("45.500000,-73.600000|45.600000,-73.500000", OpenTopoDataClient.formatLocations(locations));
  }

  @Test
  public void lookupTrack_interpolatesDownsampledDem() throws Exception {
    List<TopographyElevationService.TrackPoint> points =
        Arrays.asList(
            point(1, 0, 0, 0, 10),
            point(2, 0.001, 0.001, 50, 11),
            point(3, 0.002, 0.002, 100, 12));
    ElevationLookup lookup =
        locations -> {
          double[] out = new double[locations.size()];
          for (int i = 0; i < locations.size(); i++) {
            LatLng p = locations.get(i);
            out[i] = p.latitude * 1000;
          }
          return out;
        };
    double[] topo = TopographyElevationService.lookupTrack(points, lookup);
    assertEquals(3, topo.length);
    assertEquals(0.0, topo[0], 0.01);
    assertEquals(2.0, topo[2], 0.01);
  }

  @Test
  public void lookupTrack_usesIndexWhenDistanceIsMissing() throws Exception {
    List<TopographyElevationService.TrackPoint> points =
        Arrays.asList(
            point(1, 0, 0, 0, 10),
            point(2, 0.001, 0.001, 0, 11),
            point(3, 0.002, 0.002, 0, 12));
    ElevationLookup lookup =
        locations -> {
          double[] out = new double[locations.size()];
          for (int i = 0; i < locations.size(); i++) {
            out[i] = locations.get(i).latitude * 1000;
          }
          return out;
        };
    double[] topo = TopographyElevationService.lookupTrack(points, lookup);
    assertEquals(0.0, topo[0], 0.01);
    assertEquals(1.0, topo[1], 0.01);
    assertEquals(2.0, topo[2], 0.01);
  }

  @Test
  public void hasIncreasingDistance_requiresMoreThanOneMetreSpan() {
    assertEquals(false, TopographyElevationService.hasIncreasingDistance(new double[] {0, 0, 0}));
    assertEquals(true, TopographyElevationService.hasIncreasingDistance(new double[] {0, 50, 100}));
  }

  private static TopographyElevationService.TrackPoint point(
      long id, double lat, double lng, double dist, double alt) {
    return new TopographyElevationService.TrackPoint(id, lat, lng, dist, alt);
  }
}
