package org.runnerup.core.workout.feedback;

import android.content.Context;
import org.runnerup.core.workout.HRMStateTrigger;
import org.runnerup.core.workout.Scope;
import org.runnerup.core.workout.Workout;

public class HRMStateChangeFeedback extends AudioFeedback {
  public HRMStateChangeFeedback(HRMStateTrigger trigger) {
    // Set temporary id, overridden in getCue()
    super(org.runnerup.common.R.string.cue_hrm_connection_lost);
  }

  String getCue(Workout w, Context ctx) {
    return (formatter.getCueString(
        (w.getHeartRate(Scope.CURRENT) == 0)
            ? org.runnerup.common.R.string.cue_hrm_connection_lost
            : org.runnerup.common.R.string.cue_hrm_connection_restored));
  }
}
