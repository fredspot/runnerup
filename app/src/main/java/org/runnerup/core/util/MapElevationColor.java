package org.runnerup.core.util;

/**
 * Maps altitude onto a low→high color ramp so a route line can show hills. A range under 0.5 m
 * uses a single high-contrast color instead of a washed-out ramp.
 */
public final class MapElevationColor {

  /** Used when there is no usable elevation range (matches the graph accent). */
  public static final int FALLBACK = 0xFF22D3EE;

  private static final double MIN_RANGE_M = 0.5;
  private static final int BUCKETS = 8;
  private static final int[] STOPS = {
    0xFF2563EB, // low
    0xFF06B6D4,
    0xFF22C55E,
    0xFFFACC15,
    0xFFEF4444 // high
  };

  private MapElevationColor() {}

  public static boolean hasRange(double minM, double maxM) {
    return maxM - minM >= MIN_RANGE_M;
  }

  public static int color(double elevationM, double minM, double maxM) {
    if (!hasRange(minM, maxM) || Double.isNaN(elevationM)) {
      return FALLBACK;
    }
    double t = (elevationM - minM) / (maxM - minM);
    if (t < 0) {
      t = 0;
    } else if (t > 1) {
      t = 1;
    }
    int bucket = (int) Math.round(t * (BUCKETS - 1));
    return mix(bucket / (double) (BUCKETS - 1));
  }

  static int mix(double t) {
    if (t <= 0) {
      return STOPS[0];
    }
    if (t >= 1) {
      return STOPS[STOPS.length - 1];
    }
    double scaled = t * (STOPS.length - 1);
    int i = (int) Math.floor(scaled);
    if (i >= STOPS.length - 1) {
      return STOPS[STOPS.length - 1];
    }
    float local = (float) (scaled - i);
    return lerpArgb(STOPS[i], STOPS[i + 1], local);
  }

  static int lerpArgb(int a, int b, float t) {
    int ar = (a >> 16) & 0xFF;
    int ag = (a >> 8) & 0xFF;
    int ab = a & 0xFF;
    int br = (b >> 16) & 0xFF;
    int bg = (b >> 8) & 0xFF;
    int bb = b & 0xFF;
    int r = Math.round(ar + (br - ar) * t);
    int g = Math.round(ag + (bg - ag) * t);
    int bl = Math.round(ab + (bb - ab) * t);
    return 0xFF000000 | (r << 16) | (g << 8) | bl;
  }
}
