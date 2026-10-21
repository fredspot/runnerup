package org.runnerup.analytics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.runnerup.data.RunningPaceBounds;

/** Splits a track into 1 km windows with pace, elevation, temperature, and prior HR zones. */
public final class ComparableSegmentExtractor {

  public static final double WINDOW_METERS = 1000;

  private ComparableSegmentExtractor() {}

  public static final class Sample {
    public final double distanceM;
    public final long elapsedMs;
    public final int hr;
    public final Double elevationM;
    public final Double temperatureC;

    public Sample(
        double distanceM, long elapsedMs, int hr, Double elevationM, Double temperatureC) {
      this.distanceM = distanceM;
      this.elapsedMs = elapsedMs;
      this.hr = hr;
      this.elevationM = elevationM;
      this.temperatureC = temperatureC;
    }
  }

  /**
   * Zone 1–4 from {@code zoneBounds} (pairs of inclusive min, exclusive max). Zone 5 is at or
   * above the top of zone 4. 0 means no heart rate.
   */
  public static int zoneForHr(int hr, int[] zoneBounds) {
    if (hr <= 0 || zoneBounds == null || zoneBounds.length < 8) {
      return 0;
    }
    for (int zone = 0; zone < 4; zone++) {
      int lo = zoneBounds[zone * 2];
      int hi = zoneBounds[zone * 2 + 1];
      if (hi > lo && hr >= lo && hr < hi) {
        return zone + 1;
      }
    }
    if (zoneBounds[7] > zoneBounds[6] && hr >= zoneBounds[6] && hr < zoneBounds[7]) {
      return 4;
    }
    if (zoneBounds[7] > 0 && hr >= zoneBounds[7]) {
      return 5;
    }
    if (zoneBounds[6] > 0 && hr >= zoneBounds[6]) {
      return 4;
    }
    return 0;
  }

  public static List<ComparableSegment> extract(
      long activityId, List<Sample> samples, int[] zoneBounds) {
    if (samples == null || samples.size() < 2) {
      return Collections.emptyList();
    }
    List<Sample> points = new ArrayList<>(samples);
    Collections.sort(
        points,
        Comparator.comparingDouble((Sample s) -> s.distanceM).thenComparingLong(s -> s.elapsedMs));
    double maxDistance = points.get(points.size() - 1).distanceM;
    int windows = (int) Math.floor(maxDistance / WINDOW_METERS);
    if (windows < 1) {
      return Collections.emptyList();
    }
    List<Window> built = new ArrayList<>();
    for (int i = 0; i < windows; i++) {
      double start = i * WINDOW_METERS;
      double end = start + WINDOW_METERS;
      Long elapsedStart = interpolateElapsed(points, start);
      Long elapsedEnd = interpolateElapsed(points, end);
      if (elapsedStart == null || elapsedEnd == null || elapsedEnd <= elapsedStart) {
        continue;
      }
      double pace = (elapsedEnd - elapsedStart) / 1000.0;
      if (pace < RunningPaceBounds.BEST_TIMES_MIN_SEC_PER_KM
          || pace > RunningPaceBounds.MONTHLY_MAX_SEC_PER_KM) {
        continue;
      }
      Double elevStart = interpolateElevation(points, start);
      Double elevEnd = interpolateElevation(points, end);
      double net = 0;
      if (elevStart != null && elevEnd != null) {
        net = elevEnd - elevStart;
      }
      int avgHr = averageHr(points, start, end);
      Double avgTemp = averageTemp(points, start, end);
      double adjusted =
          RunConditionsAdjuster.adjustedPace(pace, net, WINDOW_METERS, avgTemp);
      built.add(new Window(i, pace, adjusted, avgHr, zoneForHr(avgHr, zoneBounds), net, avgTemp));
    }
    List<ComparableSegment> segments = new ArrayList<>();
    for (int i = 0; i < built.size(); i++) {
      Window window = built.get(i);
      int prevNear = 0;
      int prevFar = 0;
      if (window.index > 0) {
        prevNear = zoneAt(built, window.index - 1);
      }
      if (window.index > 1) {
        prevFar = zoneAt(built, window.index - 2);
      }
      segments.add(
          new ComparableSegment(
              activityId,
              window.index,
              window.paceSecPerKm,
              window.adjustedPaceSecPerKm,
              window.avgHr,
              window.netElevationM,
              window.avgTempC,
              prevNear,
              prevFar));
    }
    return segments;
  }

  private static int zoneAt(List<Window> windows, int index) {
    for (Window window : windows) {
      if (window.index == index) {
        return window.zone;
      }
    }
    return 0;
  }

  private static Long interpolateElapsed(List<Sample> points, double distance) {
    Double value = interpolate(points, distance, false);
    if (value == null) {
      return null;
    }
    return Math.round(value);
  }

  private static Double interpolateElevation(List<Sample> points, double distance) {
    return interpolate(points, distance, true);
  }

  private static Double interpolate(List<Sample> points, double distance, boolean elevation) {
    Sample prev = null;
    for (Sample point : points) {
      if (point.distanceM < distance) {
        if (!elevation || point.elevationM != null) {
          prev = point;
        } else if (prev == null) {
          prev = point;
        }
        continue;
      }
      Sample next = point;
      if (elevation && next.elevationM == null) {
        continue;
      }
      if (Math.abs(next.distanceM - distance) < 0.01) {
        return elevation ? next.elevationM : (double) next.elapsedMs;
      }
      if (prev == null) {
        return null;
      }
      double span = next.distanceM - prev.distanceM;
      if (span <= 0) {
        return elevation ? valueOrNull(prev, true) : (double) prev.elapsedMs;
      }
      if (elevation) {
        if (prev.elevationM == null || next.elevationM == null) {
          return null;
        }
        double t = (distance - prev.distanceM) / span;
        return prev.elevationM + t * (next.elevationM - prev.elevationM);
      }
      double t = (distance - prev.distanceM) / span;
      return prev.elapsedMs + t * (next.elapsedMs - prev.elapsedMs);
    }
    return null;
  }

  private static Double valueOrNull(Sample sample, boolean elevation) {
    if (!elevation) {
      return (double) sample.elapsedMs;
    }
    return sample.elevationM;
  }

  private static int averageHr(List<Sample> points, double start, double end) {
    long sum = 0;
    int count = 0;
    for (Sample point : points) {
      if (point.distanceM <= start || point.distanceM > end || point.hr <= 0) {
        continue;
      }
      sum += point.hr;
      count++;
    }
    if (count == 0) {
      return 0;
    }
    return (int) Math.round(sum / (double) count);
  }

  private static Double averageTemp(List<Sample> points, double start, double end) {
    double sum = 0;
    int count = 0;
    for (Sample point : points) {
      if (point.distanceM <= start || point.distanceM > end || point.temperatureC == null) {
        continue;
      }
      if (point.temperatureC.isNaN()) {
        continue;
      }
      sum += point.temperatureC;
      count++;
    }
    if (count == 0) {
      return null;
    }
    return sum / count;
  }

  private static final class Window {
    final int index;
    final double paceSecPerKm;
    final double adjustedPaceSecPerKm;
    final int avgHr;
    final int zone;
    final double netElevationM;
    final Double avgTempC;

    Window(
        int index,
        double paceSecPerKm,
        double adjustedPaceSecPerKm,
        int avgHr,
        int zone,
        double netElevationM,
        Double avgTempC) {
      this.index = index;
      this.paceSecPerKm = paceSecPerKm;
      this.adjustedPaceSecPerKm = adjustedPaceSecPerKm;
      this.avgHr = avgHr;
      this.zone = zone;
      this.netElevationM = netElevationM;
      this.avgTempC = avgTempC;
    }
  }
}
