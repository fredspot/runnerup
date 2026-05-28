package org.runnerup.data;

/**
 * Pace validation ranges in seconds per kilometer.
 *
 * <p>Best-times uses a wider fast end (2:00/km) for segment hunting; monthly comparison uses a
 * slightly slower floor (2:30/km) to exclude GPS noise on aggregated monthly zone pace.
 */
public final class RunningPaceBounds {

  /** Best-times segment gate: 2:00–12:00 / km. */
  public static final double BEST_TIMES_MIN_SEC_PER_KM = 120.0;

  public static final double BEST_TIMES_MAX_SEC_PER_KM = 720.0;

  /** Monthly zone-pace gate: 2:30–15:00 / km. */
  public static final double MONTHLY_MIN_SEC_PER_KM = 150.0;

  public static final double MONTHLY_MAX_SEC_PER_KM = 900.0;

  private RunningPaceBounds() {}

  public static boolean isValidBestTimesPace(double secPerKm) {
    return secPerKm >= BEST_TIMES_MIN_SEC_PER_KM && secPerKm <= BEST_TIMES_MAX_SEC_PER_KM;
  }

  public static boolean isValidMonthlyPace(double secPerKm) {
    return secPerKm >= MONTHLY_MIN_SEC_PER_KM && secPerKm <= MONTHLY_MAX_SEC_PER_KM;
  }
}
