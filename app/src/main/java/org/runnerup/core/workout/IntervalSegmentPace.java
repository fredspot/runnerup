/*
 * Copyright (C) 2026
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.core.workout;

/**
 * Pace over the step segment since the last interval pace cue (or since the work rep started).
 * Aligns announced pace with the user's pace-cue interval (e.g. every 40 s → pace for those 40 s).
 */
public final class IntervalSegmentPace {

  private static final double MIN_TIME_SEC = 3.0;
  private static final double MIN_DISTANCE_M = 5.0;

  private double anchorTime = 0;
  private double anchorDistance = 0;

  public void reset() {
    anchorTime = 0;
    anchorDistance = 0;
  }

  /**
   * @param stepTimeSec cumulative step time (seconds)
   * @param stepDistanceM cumulative step distance (meters)
   * @return pace in SI units (seconds per meter), or 0 if not enough data
   */
  public double getPace(double stepTimeSec, double stepDistanceM) {
    double dt = stepTimeSec - anchorTime;
    double dd = stepDistanceM - anchorDistance;
    if (dt < MIN_TIME_SEC || dd < MIN_DISTANCE_M) {
      return 0;
    }
    return dt / dd;
  }

  public void markCueEmitted(double stepTimeSec, double stepDistanceM) {
    anchorTime = stepTimeSec;
    anchorDistance = stepDistanceM;
  }
}
