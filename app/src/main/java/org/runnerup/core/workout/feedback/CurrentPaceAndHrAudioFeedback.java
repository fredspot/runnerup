/*
 * Copyright (C) 2026
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.core.workout.feedback;

import android.content.Context;
import org.runnerup.core.util.Formatter;
import org.runnerup.core.workout.Dimension;
import org.runnerup.core.workout.Feedback;
import org.runnerup.core.workout.Scope;
import org.runnerup.core.workout.Workout;

public final class CurrentPaceAndHrAudioFeedback extends AudioFeedback {

  public CurrentPaceAndHrAudioFeedback() {
    super(Scope.CURRENT, Dimension.PACE);
  }

  @Override
  String getCue(Workout w, Context ctx) {
    boolean paceOk = w.isEnabled(Dimension.PACE, Scope.CURRENT);
    boolean hrOk = w.isEnabled(Dimension.HR, Scope.CURRENT);
    if (!paceOk && !hrOk) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    if (paceOk) {
      double val = w.get(Scope.CURRENT, Dimension.PACE);
      sb.append(formatter.format(Formatter.Format.CUE_LONG, Dimension.PACE, val));
    }
    if (hrOk) {
      if (sb.length() > 0) {
        sb.append(". ");
      }
      double val = w.get(Scope.CURRENT, Dimension.HR);
      sb.append(formatter.format(Formatter.Format.CUE_LONG, Dimension.HR, val));
    }
    return sb.length() == 0 ? null : sb.toString();
  }

  @Override
  public boolean equals(Feedback _other) {
    return _other instanceof CurrentPaceAndHrAudioFeedback;
  }
}
