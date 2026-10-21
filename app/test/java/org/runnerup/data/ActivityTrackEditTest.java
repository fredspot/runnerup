package org.runnerup.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;
import org.runnerup.data.ActivityTrackEdit.Point;
import org.runnerup.data.ActivityTrackEdit.TrimPlan;

public class ActivityTrackEditTest {

  @Test
  public void trimKeepsTheMiddleAndMovesTheStartForward() {
    TrimPlan plan =
        ActivityTrackEdit.plan(
            Arrays.asList(
                new Point(1, 0, 0, 1_000_000L),
                new Point(2, 400, 120_000, 1_120_000L),
                new Point(3, 800, 240_000, 1_240_000L),
                new Point(4, 1200, 360_000, 1_360_000L)),
            500,
            200);
    assertTrue(plan.ok);
    assertTrue(plan.applied);
    assertEquals(500, plan.keepFromM, 0.01);
    assertEquals(1000, plan.keepToM, 0.01);
    assertEquals(240_000, plan.minElapsedMs);
    assertEquals(240, plan.startShiftSec);
    assertEquals(Long.valueOf(1_240_000L), plan.firstEpochMs);
  }

  @Test
  public void trimThatRemovesTheTrackIsRejected() {
    TrimPlan plan =
        ActivityTrackEdit.plan(
            Arrays.asList(new Point(1, 0, 0, 1L), new Point(2, 1000, 300_000, 2L)),
            800,
            800);
    assertFalse(plan.ok);
  }

  @Test
  public void zeroTrimIsNotApplied() {
    TrimPlan plan = ActivityTrackEdit.plan(Arrays.asList(new Point(1, 10, 0, 1L)), 0, 0);
    assertTrue(plan.ok);
    assertFalse(plan.applied);
  }

  @Test
  public void parseDurationAcceptsHoursMinutesAndSeconds() {
    assertEquals(Long.valueOf(90), ActivityTrackEdit.parseDurationSeconds("1:30"));
    assertEquals(Long.valueOf(3723), ActivityTrackEdit.parseDurationSeconds("1:02:03"));
    assertEquals(Long.valueOf(45), ActivityTrackEdit.parseDurationSeconds("45"));
    assertNull(ActivityTrackEdit.parseDurationSeconds("nope"));
    assertNull(ActivityTrackEdit.parseDurationSeconds("--"));
  }
}
