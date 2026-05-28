/*
 * Copyright (C) 2026 RunnerUp
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
import org.runnerup.core.workout.Feedback;
import org.runnerup.core.workout.RepeatStep;
import org.runnerup.core.workout.Workout;

/**
 * Spoken at the start of each work interval inside a {@link RepeatStep}: "interval N of M". The
 * iteration index is read lazily on emit because {@link RepeatStep#onNextStep} increments {@code
 * currentRepeat} before the new active step's STARTED trigger fires.
 */
public final class IntervalCountAudioFeedback extends Feedback {

  private final RepeatStep repeat;
  private RUTextToSpeech textToSpeech;

  public IntervalCountAudioFeedback(RepeatStep repeat) {
    this.repeat = repeat;
  }

  @Override
  public void onBind(Workout s, HashMap<String, Object> bindValues) {
    super.onBind(s, bindValues);
    if (bindValues.containsKey(Workout.KEY_TTS)) {
      textToSpeech = (RUTextToSpeech) bindValues.get(Workout.KEY_TTS);
    }
  }

  @Override
  public boolean equals(Feedback other) {
    return other instanceof IntervalCountAudioFeedback;
  }

  @Override
  public void emit(Workout w, Context ctx) {
    if (textToSpeech == null || repeat == null) {
      return;
    }
    int total = repeat.getRepeatCount();
    if (total <= 0) {
      return;
    }
    int current = repeat.getCurrentRepeat() + 1;
    if (current < 1) current = 1;
    if (current > total) current = total;
    String msg = ctx.getString(R.string.cue_interval_n_of_m, current, total);
    textToSpeech.speak(msg, UtterancePrio.PRIO_CUE, false, null);
  }
}
