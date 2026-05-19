/*
 * Copyright (C) 2026
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.core.workout;

import java.util.ArrayList;

/** Rolling pace over a fixed time window from cumulative step time/distance samples. */
public final class PaceRollingAverage {

  public static final int INTERVAL_RECENT_PACE_WINDOW_SEC = 20;

  private static final double MIN_TIME_SEC = 3.0;
  private static final double MIN_DISTANCE_M = 5.0;

  private final int windowSec;
  private final ArrayList<Double> times = new ArrayList<>();
  private final ArrayList<Double> distances = new ArrayList<>();

  public PaceRollingAverage(int windowSec) {
    this.windowSec = windowSec;
  }

  public PaceRollingAverage() {
    this(INTERVAL_RECENT_PACE_WINDOW_SEC);
  }

  public void reset() {
    times.clear();
    distances.clear();
  }

  /** @param stepTimeSec cumulative step time (seconds) */
  public void addSample(double stepTimeSec, double stepDistanceM) {
    if (times.isEmpty() || stepTimeSec > times.get(times.size() - 1)) {
      times.add(stepTimeSec);
      distances.add(stepDistanceM);
    } else {
      int last = times.size() - 1;
      times.set(last, stepTimeSec);
      distances.set(last, stepDistanceM);
    }
    prune(stepTimeSec);
  }

  private void prune(double now) {
    double cutoff = now - windowSec;
    while (!times.isEmpty() && times.get(0) < cutoff) {
      times.remove(0);
      distances.remove(0);
    }
  }

  /**
   * @return pace in SI units (seconds per meter), or 0 if there is not enough data yet
   */
  public double getPace() {
    if (times.size() < 2) {
      return 0;
    }
    int last = times.size() - 1;
    double dt = times.get(last) - times.get(0);
    double dd = distances.get(last) - distances.get(0);
    if (dt < MIN_TIME_SEC || dd < MIN_DISTANCE_M) {
      return 0;
    }
    return dt / dd;
  }
}
