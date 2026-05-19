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
import org.runnerup.core.util.Formatter;
import org.runnerup.core.workout.Dimension;
import org.runnerup.core.workout.Feedback;
import org.runnerup.core.workout.Workout;

/** Speaks rolling recent pace during interval work (see {@link Workout#getIntervalRecentPace()}). */
public final class RecentPaceAudioFeedback extends Feedback {

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
    return other instanceof RecentPaceAudioFeedback;
  }

  @Override
  public void emit(Workout w, Context ctx) {
    if (textToSpeech == null || formatter == null) {
      return;
    }
    double pace = w.getIntervalRecentPace();
    if (pace <= 0) {
      return;
    }
    String msg = formatter.format(Formatter.Format.CUE_LONG, Dimension.PACE, pace);
    textToSpeech.speak(msg, UtterancePrio.PRIO_CUE, /* flush= */ false, null);
    if (w != null) {
      w.logEvent(org.runnerup.common.util.Constants.DB.EVENT_TYPE.CUE_FIRED, msg);
    }
  }
}
