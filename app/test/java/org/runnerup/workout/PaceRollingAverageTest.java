package org.runnerup.core.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PaceRollingAverageTest {

  private static final double PACE_420_PER_KM = 4.0 * 60.0 + 20.0; // 260 s/km
  private static final double SPEED_420_MPS = 1000.0 / PACE_420_PER_KM;

  @Test
  public void steadyPaceWithinWindow() {
    PaceRollingAverage avg = new PaceRollingAverage(20);
    for (int t = 1; t <= 25; t++) {
      avg.addSample(t, t * SPEED_420_MPS);
    }
    double pace = avg.getPace();
    assertTrue(pace > 0);
    double minPerKm = pace * 1000.0 / 60.0;
    assertEquals(4.0 + 20.0 / 60.0, minPerKm, 0.15);
  }

  @Test
  public void resetClearsState() {
    PaceRollingAverage avg = new PaceRollingAverage(20);
    for (int t = 1; t <= 10; t++) {
      avg.addSample(t, t * SPEED_420_MPS);
    }
    avg.reset();
    assertEquals(0, avg.getPace(), 0);
  }

  @Test
  public void insufficientDataReturnsZero() {
    PaceRollingAverage avg = new PaceRollingAverage(20);
    avg.addSample(1, 1);
    assertEquals(0, avg.getPace(), 0);
  }

  @Test
  public void recentWindowReflectsFastSegmentNotSlowStart() {
    PaceRollingAverage avg = new PaceRollingAverage(20);
    double slowSpeed = 1000.0 / (6.0 * 60.0); // 6:00/km
    for (int t = 1; t <= 15; t++) {
      avg.addSample(t, t * slowSpeed);
    }
    double distAt15 = 15 * slowSpeed;
    for (int t = 16; t <= 25; t++) {
      double extra = (t - 15) * SPEED_420_MPS;
      avg.addSample(t, distAt15 + extra);
    }
    double pace = avg.getPace();
    double minPerKm = pace * 1000.0 / 60.0;
    assertTrue("expected faster than 5:30/km, was " + minPerKm, minPerKm < 5.5);
  }
}
