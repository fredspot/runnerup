package org.runnerup.tracking;

/**
 * Counts steps from Android {@code TYPE_STEP_COUNTER}, which reports the total since last reboot.
 * The first reading (or {@link #startFromCurrent()}) is the baseline. {@link #hold()} freezes the
 * count so pause walking is not included.
 */
public final class StepCounter {

  private Float lastTotal;
  private Float baseline;
  private int committed;

  public void reset() {
    lastTotal = null;
    baseline = null;
    committed = 0;
  }

  /** Next reading, or the last one if already present, becomes the new zero. */
  public void startFromCurrent() {
    committed = 0;
    baseline = lastTotal;
  }

  /** Commit the current segment and ignore further totals until the next reading. */
  public void hold() {
    committed += currentDelta();
    baseline = null;
  }

  public void onTotalSinceBoot(float total) {
    if (baseline != null && lastTotal != null && total < baseline) {
      committed += currentDelta();
      baseline = total;
      lastTotal = total;
      return;
    }
    lastTotal = total;
    if (baseline == null) {
      baseline = total;
    }
  }

  /** Null until at least one hardware reading. */
  public Integer getSteps() {
    if (lastTotal == null && committed == 0) {
      return null;
    }
    return committed + currentDelta();
  }

  private int currentDelta() {
    if (baseline == null || lastTotal == null) {
      return 0;
    }
    int delta = Math.round(lastTotal - baseline);
    return Math.max(0, delta);
  }
}
