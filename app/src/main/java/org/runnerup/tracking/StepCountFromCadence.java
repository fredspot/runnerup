package org.runnerup.tracking;

/**
 * Reconstructs a step count from stored cadence samples. {@code location.cadence} is strides/min
 * (the phone step counter delta is divided by 2 when recorded), so each stride is two steps.
 */
public final class StepCountFromCadence {

  static final double STEPS_PER_STRIDE = 2.0;
  static final long MAX_INTERVAL_MS = 10_000L;

  private StepCountFromCadence() {}

  /**
   * Integrates cadence over elapsed time. {@code elapsedMs} and {@code cadenceRpm} are parallel;
   * cadence may be NaN when that sample had none. Returns null when there is no usable cadence.
   */
  public static Integer compute(long[] elapsedMs, double[] cadenceRpm) {
    if (elapsedMs == null
        || cadenceRpm == null
        || elapsedMs.length == 0
        || elapsedMs.length != cadenceRpm.length) {
      return null;
    }
    long lastElapsed = -1;
    double lastCadence = Double.NaN;
    double steps = 0;
    boolean anyCadence = false;
    for (int i = 0; i < elapsedMs.length; i++) {
      long elapsed = elapsedMs[i];
      if (lastElapsed >= 0 && !Double.isNaN(lastCadence)) {
        long dt = elapsed - lastElapsed;
        if (dt > 0 && dt <= MAX_INTERVAL_MS) {
          steps += STEPS_PER_STRIDE * lastCadence / 60.0 * (dt / 1000.0);
        }
      }
      if (!Double.isNaN(cadenceRpm[i])) {
        lastCadence = Math.max(0, cadenceRpm[i]);
        if (cadenceRpm[i] > 0) {
          anyCadence = true;
        }
      }
      lastElapsed = elapsed;
    }
    if (!anyCadence) {
      return null;
    }
    int rounded = (int) Math.round(steps);
    return rounded > 0 ? rounded : null;
  }
}
