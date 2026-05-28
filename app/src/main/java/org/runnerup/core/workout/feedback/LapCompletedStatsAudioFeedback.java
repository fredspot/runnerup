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
import java.util.HashMap;
import org.runnerup.R;
import org.runnerup.core.util.Formatter;
import org.runnerup.core.workout.Dimension;
import org.runnerup.core.workout.Feedback;
import org.runnerup.core.workout.Scope;
import org.runnerup.core.workout.Workout;

/** Spoken only when a lap completes: activity time, lap time, and average lap heart rate. */
public final class LapCompletedStatsAudioFeedback extends Feedback {

  private RUTextToSpeech textToSpeech;
  private Formatter formatter;

  @Override
  public void onBind(Workout s, HashMap<String, Object> bindValues) {
    super.onBind(s, bindValues);
    if (bindValues.containsKey(Workout.KEY_TTS)) {
      textToSpeech = (RUTextToSpeech) bindValues.get(Workout.KEY_TTS);
    }
    if (bindValues.containsKey(Workout.KEY_FORMATTER)) {
      formatter = (Formatter) bindValues.get(Workout.KEY_FORMATTER);
    }
  }

  @Override
  public boolean equals(Feedback other) {
    return other instanceof LapCompletedStatsAudioFeedback;
  }

  @Override
  public void emit(Workout w, Context ctx) {
    if (textToSpeech == null || formatter == null) {
      return;
    }
    double totalSec = w.get(Scope.ACTIVITY, Dimension.TIME);
    double lapSec = w.get(Scope.LAP, Dimension.TIME);
    String total = formatter.format(Formatter.Format.CUE_LONG, Dimension.TIME, totalSec);
    String lapTime = formatter.format(Formatter.Format.CUE_LONG, Dimension.TIME, lapSec);

    String msg;
    if (w.isEnabled(Dimension.HR, Scope.LAP)) {
      double hr = w.get(Scope.LAP, Dimension.HR);
      String hrStr = formatter.format(Formatter.Format.CUE_LONG, Dimension.HR, hr);
      msg = ctx.getString(R.string.cue_lap_end_summary_with_hr, total, lapTime, hrStr);
    } else {
      msg = ctx.getString(R.string.cue_lap_end_summary_no_hr, total, lapTime);
    }
    textToSpeech.speak(msg, UtterancePrio.PRIO_CUE, false, null);
  }
}
