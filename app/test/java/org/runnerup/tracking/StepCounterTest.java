package org.runnerup.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class StepCounterTest {

  @Test
  public void countsFromBaseline() {
    StepCounter c = new StepCounter();
    assertNull(c.getSteps());
    c.onTotalSinceBoot(1000);
    assertEquals(Integer.valueOf(0), c.getSteps());
    c.onTotalSinceBoot(1010);
    assertEquals(Integer.valueOf(10), c.getSteps());
  }

  @Test
  public void startFromCurrentZerosTheRun() {
    StepCounter c = new StepCounter();
    c.onTotalSinceBoot(500);
    c.onTotalSinceBoot(520);
    c.startFromCurrent();
    assertEquals(Integer.valueOf(0), c.getSteps());
    c.onTotalSinceBoot(525);
    assertEquals(Integer.valueOf(5), c.getSteps());
  }

  @Test
  public void holdExcludesLaterStepsUntilNextReading() {
    StepCounter c = new StepCounter();
    c.onTotalSinceBoot(100);
    c.onTotalSinceBoot(140);
    c.hold();
    assertEquals(Integer.valueOf(40), c.getSteps());
    c.onTotalSinceBoot(200);
    assertEquals(Integer.valueOf(40), c.getSteps());
    c.onTotalSinceBoot(205);
    assertEquals(Integer.valueOf(45), c.getSteps());
  }

  @Test
  public void rebootCommitsDeltaAndContinues() {
    StepCounter c = new StepCounter();
    c.onTotalSinceBoot(900);
    c.onTotalSinceBoot(950);
    c.onTotalSinceBoot(10);
    assertEquals(Integer.valueOf(50), c.getSteps());
    c.onTotalSinceBoot(12);
    assertEquals(Integer.valueOf(52), c.getSteps());
  }
}
