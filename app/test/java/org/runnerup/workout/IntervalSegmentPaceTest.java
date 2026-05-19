package org.runnerup.core.workout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IntervalSegmentPaceTest {

  private static final double PACE_420_PER_KM = 4.0 * 60.0 + 20.0;
  private static final double SPEED_420_MPS = 1000.0 / PACE_420_PER_KM;

  @Test
  public void paceSinceRepStart() {
    IntervalSegmentPace seg = new IntervalSegmentPace();
    seg.reset();
    double pace = seg.getPace(40, 40 * SPEED_420_MPS);
    assertTrue(pace > 0);
    double minPerKm = pace * 1000.0 / 60.0;
    assertEquals(4.0 + 20.0 / 60.0, minPerKm, 0.15);
  }

  @Test
  public void paceSinceLastCue() {
    IntervalSegmentPace seg = new IntervalSegmentPace();
    double dist40 = 40 * SPEED_420_MPS;
    seg.markCueEmitted(40, dist40);
    double pace = seg.getPace(80, dist40 + 40 * SPEED_420_MPS);
    double minPerKm = pace * 1000.0 / 60.0;
    assertEquals(4.0 + 20.0 / 60.0, minPerKm, 0.15);
  }

  @Test
  public void resetClearsAnchor() {
    IntervalSegmentPace seg = new IntervalSegmentPace();
    seg.markCueEmitted(30, 100);
    seg.reset();
    assertEquals(0, seg.getPace(2, 3), 0);
    double pace = seg.getPace(40, 40 * SPEED_420_MPS);
    assertTrue(pace > 0);
  }

  @Test
  public void insufficientSegmentReturnsZero() {
    IntervalSegmentPace seg = new IntervalSegmentPace();
    assertEquals(0, seg.getPace(1, 1), 0);
  }
}
