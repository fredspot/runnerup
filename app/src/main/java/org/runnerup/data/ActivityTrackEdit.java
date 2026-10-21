package org.runnerup.data;

import java.util.List;

/** Pure decisions for trimming a track and parsing a duration field. */
public final class ActivityTrackEdit {

  private ActivityTrackEdit() {}

  public static final class Point {
    public final long id;
    public final double distanceM;
    public final long elapsedMs;
    public final Long epochMs;

    public Point(long id, double distanceM, long elapsedMs, Long epochMs) {
      this.id = id;
      this.distanceM = distanceM;
      this.elapsedMs = elapsedMs;
      this.epochMs = epochMs;
    }
  }

  /** What to keep after a start/end trim. {@code ok} is false when the cut removes the track. */
  public static final class TrimPlan {
    public final boolean ok;
    public final boolean applied;
    public final double keepFromM;
    public final double keepToM;
    public final long minElapsedMs;
    public final long maxElapsedMs;
    public final long startShiftSec;
    public final Long firstEpochMs;

    private TrimPlan(
        boolean ok,
        boolean applied,
        double keepFromM,
        double keepToM,
        long minElapsedMs,
        long maxElapsedMs,
        long startShiftSec,
        Long firstEpochMs) {
      this.ok = ok;
      this.applied = applied;
      this.keepFromM = keepFromM;
      this.keepToM = keepToM;
      this.minElapsedMs = minElapsedMs;
      this.maxElapsedMs = maxElapsedMs;
      this.startShiftSec = startShiftSec;
      this.firstEpochMs = firstEpochMs;
    }

    public static TrimPlan notRequested() {
      return new TrimPlan(true, false, 0, 0, 0, 0, 0, null);
    }

    public static TrimPlan rejected() {
      return new TrimPlan(false, false, 0, 0, 0, 0, 0, null);
    }
  }

  public static TrimPlan plan(List<Point> points, double trimStartM, double trimEndM) {
    if (trimStartM <= 0 && trimEndM <= 0) {
      return TrimPlan.notRequested();
    }
    if (points == null || points.isEmpty()) {
      return TrimPlan.rejected();
    }
    double max = 0;
    for (Point point : points) {
      if (point.distanceM > max) {
        max = point.distanceM;
      }
    }
    double from = Math.max(0, trimStartM);
    double to = max - Math.max(0, trimEndM);
    if (to - from < 1) {
      return TrimPlan.rejected();
    }
    Point first = null;
    Point last = null;
    for (Point point : points) {
      if (point.distanceM < from || point.distanceM > to) {
        continue;
      }
      if (first == null || point.elapsedMs < first.elapsedMs) {
        first = point;
      }
      if (last == null || point.elapsedMs > last.elapsedMs) {
        last = point;
      }
    }
    if (first == null || last == null) {
      return TrimPlan.rejected();
    }
    return new TrimPlan(
        true,
        true,
        from,
        to,
        first.elapsedMs,
        last.elapsedMs,
        first.elapsedMs / 1000L,
        first.epochMs);
  }

  /** Accepts seconds, m:ss, or h:mm:ss. Returns null when the text is not a duration. */
  public static Long parseDurationSeconds(String text) {
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    if (trimmed.isEmpty() || "--".equals(trimmed)) {
      return null;
    }
    String[] parts = trimmed.split(":");
    if (parts.length < 1 || parts.length > 3) {
      return null;
    }
    try {
      long[] nums = new long[parts.length];
      for (int i = 0; i < parts.length; i++) {
        if (parts[i].isEmpty()) {
          return null;
        }
        nums[i] = Long.parseLong(parts[i]);
        if (nums[i] < 0) {
          return null;
        }
      }
      if (parts.length == 1) {
        return nums[0];
      }
      if (parts.length == 2) {
        return nums[0] * 60 + nums[1];
      }
      return nums[0] * 3600 + nums[1] * 60 + nums[2];
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
