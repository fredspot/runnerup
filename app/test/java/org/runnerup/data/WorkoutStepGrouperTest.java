package org.runnerup.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.runnerup.common.util.Constants;

public class WorkoutStepGrouperTest {

  @Test
  public void shouldShowStepSummary_forWorkoutStepIntensities() {
    assertTrue(WorkoutStepGrouper.shouldShowStepSummary(Constants.DB.INTENSITY.WARMUP));
    assertTrue(WorkoutStepGrouper.shouldShowStepSummary(Constants.DB.INTENSITY.COOLDOWN));
    assertTrue(WorkoutStepGrouper.shouldShowStepSummary(Constants.DB.INTENSITY.ACTIVE));
  }

  @Test
  public void shouldShowStepSummary_falseForRest() {
    assertFalse(WorkoutStepGrouper.shouldShowStepSummary(Constants.DB.INTENSITY.RESTING));
  }
}
