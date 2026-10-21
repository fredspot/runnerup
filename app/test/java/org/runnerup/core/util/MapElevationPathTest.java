package org.runnerup.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class MapElevationPathTest {

  @Test
  public void segments_flatTrackIsOneColor() {
    double[] lat = {45, 45.01, 45.02};
    double[] lng = {9, 9.01, 9.02};
    double[] elev = {50, 50, 50};
    List<MapElevationPath.Segment> segs = MapElevationPath.segments(lat, lng, elev);
    assertEquals(1, segs.size());
    assertEquals(MapElevationColor.FALLBACK, segs.get(0).color);
    assertEquals(3, segs.get(0).lat.length);
  }

  @Test
  public void segments_hillUsesMoreThanOneColor() {
    double[] lat = {45, 45.01, 45.02, 45.03, 45.04};
    double[] lng = {9, 9.01, 9.02, 9.03, 9.04};
    double[] elev = {51, 52, 56, 54, 51};
    List<MapElevationPath.Segment> segs = MapElevationPath.segments(lat, lng, elev);
    Set<Integer> colors = new HashSet<>();
    for (MapElevationPath.Segment seg : segs) {
      colors.add(seg.color);
      assertTrue(seg.lat.length >= 2);
    }
    assertTrue(colors.size() > 1);
  }

  @Test
  public void minMax_ignoresMissingSamples() {
    List<Double> elev = new ArrayList<>();
    elev.add(Double.NaN);
    elev.add(51.0);
    elev.add(56.0);
    elev.add(null);
    double[] range = MapElevationPath.minMax(elev);
    assertEquals(51.0, range[0], 0.01);
    assertEquals(56.0, range[1], 0.01);
  }

  @Test
  public void fillGaps_carriesNearestElevation() {
    double[] elev = {Double.NaN, 10, Double.NaN, 20, Double.NaN};
    MapElevationPath.fillGaps(elev);
    assertEquals(10, elev[0], 0.01);
    assertEquals(10, elev[2], 0.01);
    assertEquals(20, elev[4], 0.01);
  }
}
