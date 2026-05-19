/*
 * Copyright (C) 2012 - 2014 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.core.workout;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Pair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.core.util.Formatter;
import org.runnerup.core.util.HRZones;
import org.runnerup.core.util.SafeParse;
import org.runnerup.features.AudioCueSettingsActivity;
import org.runnerup.core.workout.Workout.StepListEntry;
import org.runnerup.core.workout.feedback.AudioCountdownFeedback;
import org.runnerup.core.workout.feedback.AudioFeedback;
import org.runnerup.core.workout.feedback.CoachFeedback;
import org.runnerup.core.workout.feedback.CurrentPaceAndHrAudioFeedback;
import org.runnerup.core.workout.feedback.LapCompletedStatsAudioFeedback;
import org.runnerup.core.workout.feedback.CountdownFeedback;
import org.runnerup.core.workout.feedback.HRMStateChangeFeedback;
import org.runnerup.core.workout.feedback.IntervalCountAudioFeedback;
import org.runnerup.core.workout.feedback.RecentPaceAudioFeedback;

public class WorkoutBuilder {

  /**
   * Create a basic workout from settings
   *
   * @return workout based on SharedPreferences
   */
  public static Workout createDefaultWorkout(
      Resources res, SharedPreferences prefs, Dimension target) {
    Workout w = new Workout();
    w.sport = prefs.getInt(res.getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING);
    w.setWorkoutType(Constants.WORKOUT_TYPE.BASIC);

    if (prefs.getBoolean(res.getString(R.string.pref_countdown_active), false)) {
      long val;
      String vals = prefs.getString(res.getString(R.string.pref_countdown_time), "0");
      try {
        val = Long.parseLong(vals);
      } catch (NumberFormatException e) {
        val = 0;
      }
      if (val > 0) {
        Step step = Step.createRestStep(Dimension.TIME, val, false);
        w.steps.add(step);
      }
    }

    // Add an active step
    Step step = new Step();
    w.steps.add(step);

    if (target == Dimension.PACE) {
      double unitMeters = Formatter.getUnitMeters(res, prefs);
      double seconds_per_unit =
          (double)
              SafeParse.parseSeconds(
                  prefs.getString(res.getString(R.string.pref_basic_target_pace_max), "00:05:00"),
                  5 * 60);
      int targetPaceRange =
          prefs.getInt(res.getString(R.string.pref_basic_target_pace_min_range), 15);
      double targetPaceMax = seconds_per_unit / unitMeters;
      double targetPaceMin = (targetPaceMax * unitMeters - targetPaceRange) / unitMeters;
      Range range = new Range(targetPaceMin, targetPaceMax);
      step.targetType = Dimension.PACE;
      step.targetValue = range;
    } else if (target == Dimension.HRZ) {
      HRZones hrCalc = new HRZones(res, prefs);
      int zone = prefs.getInt(res.getString(R.string.pref_basic_target_hrz), -1);
      if (zone >= 0) {
        Pair<Integer, Integer> vals = hrCalc.getHRValues(zone + 1);
        if (vals != null) {
          step.targetType = Dimension.HR;
          step.targetValue = new Range(vals.first, vals.second);
        }
      }
    }

    return w;
  }

  private static void addAutoPauseTrigger(Resources res, Step step, SharedPreferences prefs) {
    // Auto-pause is permanently disabled: it was dropping GPS rows during rest intervals in
    // interval workouts (samples weren't being saved while the tracker thought the runner had
    // stopped), which produced under-counted active-lap distances. We never attach the
    // trigger anymore, regardless of pref_autopause_active. The preference and UI rows are
    // left in place to avoid breaking settings screens, but they have no effect.
  }

  /**
   * Create an interval workout, for the Interval tab
   *
   * @param res
   * @param prefs
   * @return
   */
  public static Workout createDefaultIntervalWorkout(Resources res, SharedPreferences prefs) {
    Workout w = new Workout();
    w.sport = prefs.getInt(res.getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING);
    w.setWorkoutType(Constants.WORKOUT_TYPE.INTERVAL);

    final boolean convertRestToRecovery =
        prefs.getBoolean(
            res.getString(R.string.pref_convert_interval_distance_rest_to_recovery), true);

    final int warmupType =
        prefs.getInt(res.getString(R.string.pref_interval_warmup_type), /* until pressed */ -1);
    if (warmupType != -2) {
      Step step = new Step();
      step.intensity = Intensity.WARMUP;
      if (warmupType == -1) {
        step.durationType = null;
      } else if (warmupType == DB.DIMENSION.TIME) {
        step.durationType = Dimension.TIME;
        step.durationValue =
            SafeParse.parseSeconds(
                prefs.getString(res.getString(R.string.pref_interval_warmup_time), "00:02:00"),
                2 * 60);
      } else if (warmupType == DB.DIMENSION.DISTANCE) {
        step.durationType = Dimension.DISTANCE;
        step.durationValue =
            SafeParse.parseDouble(
                prefs.getString(res.getString(R.string.pref_interval_warmup_distance), "2000"),
                2000);
      }
      w.steps.add(step);
    }

    int repetitions =
        (int)
            SafeParse.parseDouble(
                prefs.getString(res.getString(R.string.pref_interval_repetitions), "8"), 1);

    int intervalType = prefs.getInt(res.getString(R.string.pref_interval_type), 1); // Distance
    long intervalTime =
        SafeParse.parseSeconds(
            prefs.getString(res.getString(R.string.pref_interval_time), "00:04:00"), 4 * 60);
    double intervalDistance =
        SafeParse.parseDouble(
            prefs.getString(res.getString(R.string.pref_interval_distance), "1000"), 1000);
    int intervalRestType = prefs.getInt(res.getString(R.string.pref_interval_rest_type), 0); // Time
    long intervalRestTime =
        SafeParse.parseSeconds(
            prefs.getString(res.getString(R.string.pref_interval_rest_time), "00:01:00"), 60);
    double intervalRestDistance =
        SafeParse.parseDouble(
            prefs.getString(res.getString(R.string.pref_interval_rest_distance), "200"), 200);

    RepeatStep repeat = new RepeatStep();
    repeat.repeatCount = repetitions;
    {
      Step step = new Step();
      switch (intervalType) {
        case 0: // Time
          step.durationType = Dimension.TIME;
          step.durationValue = intervalTime;
          break;
        case 1: // Distance
          step.durationType = Dimension.DISTANCE;
          step.durationValue = intervalDistance;
          break;
      }
      // Pace target takes precedence over HR zone target if both are configured. A pace
      // string of "00:00:00" (the default) means no pace target.
      long intervalTargetPaceSec =
          SafeParse.parseSeconds(
              prefs.getString(res.getString(R.string.pref_interval_target_pace), "00:00:00"), 0);
      boolean paceTargetApplied = false;
      if (intervalTargetPaceSec > 0) {
        double unitMeters = Formatter.getUnitMeters(res, prefs);
        int targetPaceRange =
            prefs.getInt(res.getString(R.string.pref_basic_target_pace_min_range), 15);
        double targetPaceMax = intervalTargetPaceSec / unitMeters;
        double targetPaceMin = (targetPaceMax * unitMeters - targetPaceRange) / unitMeters;
        step.targetType = Dimension.PACE;
        step.targetValue = new Range(targetPaceMin, targetPaceMax);
        paceTargetApplied = true;
      }
      if (!paceTargetApplied) {
        int intervalHrzSel =
            prefs.getInt(res.getString(R.string.pref_interval_target_hrz), /* none */ 0);
        if (intervalHrzSel > 0) {
          HRZones hrCalc = new HRZones(res, prefs);
          Pair<Integer, Integer> vals = hrCalc.getHRValues(intervalHrzSel);
          if (vals != null) {
            step.targetType = Dimension.HR;
            step.targetValue = new Range(vals.first, vals.second);
          }
        }
      }
      repeat.steps.add(step);

      Step rest = null;
      switch (intervalRestType) {
        case 0: // Time
          rest = Step.createRestStep(Dimension.TIME, intervalRestTime, convertRestToRecovery);
          break;
        case 1: // Distance
          rest =
              Step.createRestStep(Dimension.DISTANCE, intervalRestDistance, convertRestToRecovery);
          break;
      }
      repeat.steps.add(rest);
    }
    w.steps.add(repeat);

    final int cooldownType =
        prefs.getInt(res.getString(R.string.pref_interval_cooldown_type), /* until pressed */ -1);
    if (cooldownType != -2) {
      Step step = new Step();
      step.intensity = Intensity.COOLDOWN;
      if (cooldownType == -1) {
        step.durationType = null;
      } else if (cooldownType == DB.DIMENSION.TIME) {
        step.durationType = Dimension.TIME;
        step.durationValue =
            SafeParse.parseSeconds(
                prefs.getString(res.getString(R.string.pref_interval_cooldown_time), "00:02:00"),
                2 * 60);
      } else if (cooldownType == DB.DIMENSION.DISTANCE) {
        step.durationType = Dimension.DISTANCE;
        step.durationValue =
            SafeParse.parseDouble(
                prefs.getString(res.getString(R.string.pref_interval_cooldown_distance), "2000"),
                2000);
      }
      w.steps.add(step);
    }

    return w;
  }

  public static boolean validateSeconds(String newValue) {
    // TODO move this somewhere
    long seconds = SafeParse.parseSeconds(newValue, -1);
    long seconds2 = SafeParse.parseSeconds(DateUtils.formatElapsedTime(seconds), -1);
    return seconds == seconds2;
  }

  public static SharedPreferences getAudioCuePreferences(
      Context ctx, SharedPreferences pref, String key) {
    return getSubPreferences(
        ctx,
        pref,
        key,
        ctx.getString(org.runnerup.common.R.string.Default),
        AudioCueSettingsActivity.SUFFIX);
  }

  private static SharedPreferences getSubPreferences(
      Context ctx, SharedPreferences pref, String key, String defaultVal, String suffix) {
    String name = pref.getString(key, null);
    if (name == null || name.contentEquals(defaultVal)) {
      return pref;
    }
    return ctx.getSharedPreferences(name + suffix, Context.MODE_PRIVATE);
  }

  public static void addAudioCuesToWorkout(
      Resources res, Workout w, SharedPreferences audioPrefs, SharedPreferences prefs) {
    final boolean muteMusic = audioPrefs.getBoolean(res.getString(R.string.pref_mute_bool), false);
    w.setMute(muteMusic);
    addAudioCuesToWorkout(res, w.steps, audioPrefs, prefs, w.getWorkoutType());
  }

  /**
   * Add audio cues to the steps Called recursively for Repeat steps
   *
   * @param res
   * @param steps
   * @param audioPrefs
   * @param prefs
   */
  private static void addAudioCuesToWorkout(
      Resources res,
      ArrayList<Step> steps,
      SharedPreferences audioPrefs,
      SharedPreferences prefs,
      int workoutType) {
    final boolean skip_startstop_cue =
        audioPrefs.getBoolean(res.getString(R.string.cueinfo_skip_startstop), false);
    final boolean isLapStartedCue =
        audioPrefs.getBoolean(res.getString(R.string.pref_lap_started), true);

    Step[] stepArr = new Step[steps.size()];
    steps.toArray(stepArr);
    for (int i = 0; i < stepArr.length; i++) {
      Step step = stepArr[i];
      Step next = i + 1 == stepArr.length ? null : stepArr[i + 1];

      if (step.getIntensity() == Intensity.REPEAT) {
        RepeatStep repeat = (RepeatStep) step;
        addAudioCuesToWorkout(res, repeat.steps, audioPrefs, prefs, workoutType);
        // After audio cues are attached to inner steps, optionally announce "interval N of M" at
        // the start of every active inner step. Insert the trigger first so the count is spoken
        // before the existing "lap started" cue.
        boolean announceIteration =
            workoutType == Constants.WORKOUT_TYPE.INTERVAL
                && audioPrefs.getBoolean(
                    res.getString(R.string.pref_interval_announce_iteration), true);
        if (announceIteration && repeat.getRepeatCount() > 1) {
          for (Step inner : repeat.steps) {
            if (inner.getIntensity() != Intensity.ACTIVE) continue;
            EventTrigger countCue = new EventTrigger();
            countCue.event = Event.STARTED;
            countCue.scope = Scope.STEP;
            countCue.maxCounter = 1;
            countCue.triggerAction.add(new IntervalCountAudioFeedback(repeat));
            inner.triggers.add(0, countCue);
          }
        }
        continue;
      }

      if (step.getIntensity() != Intensity.RESTING) {
        // Periodic distance/time and end of lap/step
        // Some suppressions for each intensity for related lap started/completed
        // endOfLap is related to the autolap
        boolean endOfLap = step.getIntensity() == Intensity.ACTIVE || step.getAutolap() > 0;
        ArrayList<Trigger> defaultTriggers = createDefaultTriggers(res, audioPrefs, endOfLap);

        if (workoutType == Constants.WORKOUT_TYPE.INTERVAL
            && step.getIntensity() == Intensity.ACTIVE
            && !defaultTriggers.isEmpty()) {
          sanitizeIntervalWorkPaceFeedback(defaultTriggers.get(0).triggerAction);
        }

        step.triggers.addAll(defaultTriggers);
      }

      if (!skip_startstop_cue) {
        addPauseStopResumeTriggers(step.triggers);
      }

      if (step.durationType != null) {
        // GUI countdown
        IntervalTrigger trigger = new IntervalTrigger();
        trigger.dimension = step.durationType;
        trigger.first = 1;
        trigger.interval = 1;
        trigger.scope = Scope.STEP;
        trigger.triggerAction.add(new CountdownFeedback(Scope.STEP, step.durationType));
        step.triggers.add(trigger);

        // Audio feedback
        createAudioCountdown(step);
      }

      switch (step.getIntensity()) {
        case ACTIVE:
          if (isLapStartedCue) {
            EventTrigger ev = new EventTrigger();
            ev.event = Event.STARTED;
            ev.scope = Scope.STEP;
            ev.maxCounter = 1;
            ev.triggerAction.add(new AudioFeedback(R.string.cue_lap_started));
            step.triggers.add(ev);

            EventTrigger ev2 = new EventTrigger(); // for autolap
            ev2.event = Event.STARTED;
            ev2.scope = Scope.LAP;
            ev2.skipCounter = 1; // skip above
            ev2.triggerAction.add(new AudioFeedback(R.string.cue_lap_started));
            step.triggers.add(ev2);

            if (next == null || next.getIntensity() != step.getIntensity()) {
              EventTrigger ev3 = new EventTrigger();
              ev3.event = Event.COMPLETED;
              ev3.scope = Scope.STEP;
              ev3.triggerAction.add(new AudioFeedback(R.string.cue_lap_completed));

              // Add after "end of lap" default audio cue
              Trigger elt = hasEndOfLapTrigger(step.triggers);
              if (elt != null) {
                ev3.triggerAction.addAll(elt.triggerAction);
                /* suppress empty STEP COMPLETED */
                ev3.triggerSuppression.add(EndOfLapSuppression.EmptyLapSuppression);
                /* And suppress last end of lap trigger */
                elt.triggerSuppression.add(EndOfLapSuppression.EndOfLapSuppression);
              }
              step.triggers.add(ev3);
            }
          }
          break;

        case WARMUP:
        case COOLDOWN:
          if (isLapStartedCue) {
            EventTrigger ev = new EventTrigger();
            ev.event = Event.STARTED;
            ev.scope = Scope.STEP;
            ev.triggerAction.add(
                new AudioFeedback(
                    step.getIntensity() == Intensity.WARMUP
                        ? R.string.cue_warmup_started
                        : R.string.cue_cooldown_started));
            step.triggers.add(ev);
          }
          break;

        case RECOVERY:
        case RESTING:
          // No action
          break;
      }

      if (audioPrefs.getBoolean(res.getString(R.string.pref_cue_hrm_connection), false)) {
        HRMStateTrigger hrmState = new HRMStateTrigger();
        hrmState.triggerAction.add(new HRMStateChangeFeedback(hrmState));
        step.triggers.add(hrmState);
      }

      final boolean coaching =
          audioPrefs.getBoolean(res.getString(R.string.cueinfo_target_coaching), true);
      if (coaching && step.getTargetType() != null) {
        final Range range = step.getTargetValue();
        final int averageSeconds =
            SafeParse.parseInt(
                prefs.getString(
                    res.getString(R.string.pref_target_pace_moving_average_seconds), "20"),
                20);
        final int graceSeconds =
            SafeParse.parseInt(
                prefs.getString(res.getString(R.string.pref_target_pace_grace_seconds), "30"), 30);

        TargetTrigger tr = new TargetTrigger(step.getTargetType(), averageSeconds, graceSeconds);
        tr.scope = Scope.STEP;
        tr.range = range;
        tr.triggerAction.add(new CoachFeedback(tr));
        step.triggers.add(tr);
      }

      if (workoutType == Constants.WORKOUT_TYPE.INTERVAL
          && step.getIntensity() == Intensity.ACTIVE) {
        int hrCueEvery =
            SafeParse.parseInt(
                prefs.getString(
                    res.getString(R.string.pref_interval_hr_cue_interval_seconds), "0"),
                0);
        if (hrCueEvery > 0) {
          hrCueEvery = Math.min(600, Math.max(1, hrCueEvery));
          int announcement =
              prefs.getInt(res.getString(R.string.pref_interval_hr_cue_announcement), 0);
          if (announcement < 0 || announcement > 2) {
            announcement = 0;
          }
          ArrayList<Feedback> hrActions = new ArrayList<>();
          if (announcement == 0 || announcement == 2) {
            hrActions.add(new AudioFeedback(Scope.CURRENT, Dimension.HR));
          }
          if (announcement == 1 || announcement == 2) {
            hrActions.add(new AudioFeedback(Scope.CURRENT, Dimension.HRZ));
          }
          if (!hrActions.isEmpty()) {
            IntervalTrigger hrCue = new IntervalTrigger();
            hrCue.first = hrCueEvery;
            hrCue.interval = hrCueEvery;
            hrCue.scope = Scope.STEP;
            hrCue.dimension = Dimension.TIME;
            hrCue.triggerAction = hrActions;
            hrCue.triggerSuppression.add(EndOfLapSuppression.EmptyLapSuppression);
            step.triggers.add(hrCue);
          }
        }

        int paceCueEvery =
            SafeParse.parseInt(
                prefs.getString(
                    res.getString(R.string.pref_interval_pace_cue_interval_seconds), "0"),
                0);
        if (paceCueEvery > 0) {
          paceCueEvery = Math.min(600, Math.max(1, paceCueEvery));
          IntervalTrigger paceCue = new IntervalTrigger();
          paceCue.first = paceCueEvery;
          paceCue.interval = paceCueEvery;
          paceCue.scope = Scope.STEP;
          paceCue.dimension = Dimension.TIME;
          paceCue.triggerAction.add(new RecentPaceAudioFeedback());
          paceCue.triggerSuppression.add(EndOfLapSuppression.EmptyLapSuppression);
          step.triggers.add(paceCue);
        }
      }

      checkDuplicateTriggers(step);
    }
  }

  /**
   * On interval work steps, replace lap/step/current pace cues with a single recent-pace cue so
   * periodic distance/time triggers do not speak a different pace than the interval pace timer.
   */
  private static void sanitizeIntervalWorkPaceFeedback(ArrayList<Feedback> feedback) {
    boolean hadPace = false;
    for (int i = feedback.size() - 1; i >= 0; i--) {
      Feedback f = feedback.get(i);
      if (f instanceof CurrentPaceAndHrAudioFeedback) {
        hadPace = true;
        feedback.remove(i);
      } else if (f instanceof AudioFeedback af && af.getDimension() == Dimension.PACE) {
        hadPace = true;
        feedback.remove(i);
      }
    }
    if (hadPace) {
      feedback.add(new RecentPaceAudioFeedback());
    }
  }

  interface TriggerFilter {
    boolean match(Trigger trigger);
  }

  private static Trigger hasTrigger(List<Trigger> triggers, TriggerFilter filter) {
    for (Trigger t : triggers) {
      if (filter.match(t)) return t;
    }
    return null;
  }

  private static Trigger hasEndOfLapTrigger(List<Trigger> triggers) {
    return hasTrigger(
        triggers,
        trigger -> {
          if (trigger == null) return false;

          if (!(trigger instanceof EventTrigger et)) return false;
          return (et.event == Event.COMPLETED && et.scope == Scope.LAP);
        });
  }

  private static void checkDuplicateTriggers(Step step) {
    if (hasEndOfLapTrigger(step.triggers) != null) {
      Log.e("WorkoutBuilder", "hasEndOfLapTrigger()");
      /*
       * The end of lap trigger can be a duplicate of a distance based interval trigger
       *  1) in a step with distance duration, that is a multiple of the interval-distance
       * e.g interval-trigger-distance = 100m duration = 1000m, then set max count = 9
       *  2) in a step with autolap 500m and interval-trigger-distance 1000 then remove the
       * trigger
       */
      ArrayList<TriggerSuppression> list = new ArrayList<>();
      if (step.getAutolap() > 0) {
        list.add(new EndOfLapSuppression(step.getAutolap()));
      }

      if (step.getDurationType() == Dimension.DISTANCE) {
        list.add(new EndOfLapSuppression(step.getDurationValue()));
      }
      for (Trigger t : step.triggers) {
        if (!(t instanceof IntervalTrigger it)) continue;
        if (it.dimension != Dimension.DISTANCE) continue;
        it.triggerSuppression.addAll(list);
      }
    }
  }

  /**
   * Add the default triggers, with the configurable feedback (activity/lap etc time/distance etc):
   * * periodic time/distance * end of lap
   *
   * @param res
   * @param prefs
   * @return
   */
  private static ArrayList<Trigger> createDefaultTriggers(
      Resources res, SharedPreferences prefs, boolean endOfLap) {
    ArrayList<Feedback> feedback = new ArrayList<>();
    ArrayList<Trigger> triggers = new ArrayList<>();

    if (prefs.getBoolean(res.getString(R.string.cue_time), false)) {
      long val = 0;
      String vals = prefs.getString(res.getString(R.string.cue_time_intervall), "120");
      try {
        val = Long.parseLong(vals);
      } catch (NumberFormatException e) {
      }
      if (val > 0) {
        IntervalTrigger t = new IntervalTrigger();
        t.first = val;
        t.interval = val;
        t.scope = Scope.STEP;
        t.dimension = Dimension.TIME;
        triggers.add(t);
      }
    }

    if (prefs.getBoolean(res.getString(R.string.cue_distance), false)) {
      long val = 0;
      String vals = prefs.getString(res.getString(R.string.cue_distance_intervall), "1000");
      try {
        val = Long.parseLong(vals);
      } catch (NumberFormatException e) {
      }
      if (val > 0) {
        IntervalTrigger t = new IntervalTrigger();
        t.first = val;
        t.interval = val;
        t.scope = Scope.STEP;
        t.dimension = Dimension.DISTANCE;
        triggers.add(t);
      }
    }

    if (endOfLap && prefs.getBoolean(res.getString(R.string.cue_end_of_lap), false)) {
      EventTrigger ev = new EventTrigger();
      ev.event = Event.COMPLETED;
      ev.scope = Scope.LAP;
      triggers.add(ev);
    }

    addFeedbackFromPreferences(prefs, res, feedback);

    final boolean lapEndSummary =
        prefs.getBoolean(res.getString(R.string.cueinfo_lap_end_summary), false);

    for (Trigger t : triggers) {
      if (lapEndSummary
          && t instanceof EventTrigger ev
          && ev.event == Event.COMPLETED
          && ev.scope == Scope.LAP) {
        ArrayList<Feedback> lapFeedback = new ArrayList<>();
        lapFeedback.add(new LapCompletedStatsAudioFeedback());
        lapFeedback.addAll(feedback);
        t.triggerAction = lapFeedback;
      } else {
        t.triggerAction = feedback;
      }
      /* suppress empty laps */
      t.triggerSuppression.add(EndOfLapSuppression.EmptyLapSuppression);
    }

    return triggers;
  }

  private static void addPauseStopResumeTriggers(ArrayList<Trigger> list) {
    {
      EventTrigger p = new EventTrigger();
      p.event = Event.PAUSED;
      p.scope = Scope.STEP;
      p.triggerAction.add(new AudioFeedback(R.string.cue_activity_paused));
      list.add(p);
    }

    {
      EventTrigger r = new EventTrigger();
      r.event = Event.RESUMED;
      r.scope = Scope.STEP;
      r.triggerAction.add(new AudioFeedback(R.string.cue_activity_resumed));
      list.add(r);
    }

    {
      EventTrigger ev = new EventTrigger();
      ev.event = Event.STOPPED;
      ev.scope = Scope.STEP;
      ev.triggerAction.add(new AudioFeedback(R.string.cue_activity_stopped));
      list.add(ev);
    }
  }

  private static void createAudioCountdown(Step step) {
    if (step.getDurationType() == null) {
      // Only for time/distance
      return;
    }

    ArrayList<Double> list = new ArrayList<>();
    switch (step.getDurationType()) {
      case TIME:
        // seconds
        Double[] tmp0 = {60d, 30d, 10d, 5d, 4d, 3d, 2d, 1d};
        list.addAll(Arrays.asList(tmp0));
        break;
      case DISTANCE:
        // meters
        Double[] tmp1 = {100d, 50d, 20d, 10d};
        list.addAll(Arrays.asList(tmp1));
        break;
      default:
        return;
    }

    // Extent the feedback list with more values for longer countdowns
    while (step.getDurationValue() / 2 > list.get(0)) {
      list.add(0, list.get(0) * 2d);
    }

    // Remove all values in list close to the step
    while (list.size() > 0 && step.getDurationValue() < list.get(0) * 1.1d) {
      list.remove(0);
    }
    list.add(0, step.getDurationValue());

    // create a list trigger for the values
    ListTrigger trigger = new ListTrigger(step.getDurationType(), Scope.STEP, list);
    trigger.triggerAction.add(new AudioCountdownFeedback(Scope.STEP, step.getDurationType()));
    step.triggers.add(trigger);
  }

  /**
   * Add autolap, autopause and countdown to workouts
   *
   * @param res
   * @param prefs
   * @param w
   */
  public static void prepareWorkout(Resources res, SharedPreferences prefs, Workout w) {
    List<StepListEntry> steps = w.getStepList();
    boolean basic = w.getWorkoutType() == Constants.WORKOUT_TYPE.BASIC;

    // autolap
    boolean autolap =
        basic
            ? prefs.getBoolean(res.getString(R.string.pref_autolap_active), false)
            : prefs.getBoolean(res.getString(R.string.pref_step_autolap_active), false);
    if (autolap) {
      double val;
      String vals = prefs.getString(res.getString(R.string.pref_autolap), "1000");
      try {
        val = Double.parseDouble(vals);
      } catch (NumberFormatException e) {
        val = 0;
      }
      Log.d("WorkoutBuilder", "setAutolap(" + val + ")");
      for (StepListEntry s : steps) {
        // Apply autolap to: every step in BASIC workouts; otherwise to ACTIVE work intervals
        // and to WARMUP/COOLDOWN so a long warmup or cooldown is split into 1 km laps with a
        // shorter remainder lap. (Step.checkFinished handles the per-km split + remainder once
        // setAutolap is called; nothing else is needed.)
        Intensity intensity = s.step.getIntensity();
        if (basic
            || intensity == Intensity.ACTIVE
            || intensity == Intensity.WARMUP
            || intensity == Intensity.COOLDOWN) {
          s.step.setAutolap(val);
        }
      }
    }

    // Autopause
    for (StepListEntry s : steps) {
      if (basic) {
        addAutoPauseTrigger(res, s.step, prefs);
        continue;
      }
      switch (s.step.getIntensity()) {
        case WARMUP:
        case COOLDOWN:
          addAutoPauseTrigger(res, s.step, prefs);
          break;
        case ACTIVE:
        case RECOVERY:
        case RESTING:
        case REPEAT:
        default:
          break;
      }
    }

    /*
     * Add countdowns after steps with duration "until pressed"
     * - if next is not a countdown
     * - and current is not rest
     */
    if (prefs.getBoolean(res.getString(R.string.pref_step_countdown_active), true)) {
      final boolean convertRestToRecovery =
          prefs.getBoolean(
              res.getString(R.string.pref_convert_advanced_distance_rest_to_recovery), true);
      long val = 15; // default
      String vals = prefs.getString(res.getString(R.string.pref_step_countdown_time), "15");
      try {
        val = Long.parseLong(vals);
      } catch (NumberFormatException e) {
      }
      if (val > 0) {
        StepListEntry[] stepArr = new StepListEntry[steps.size()];
        steps.toArray(stepArr);
        for (int i = 0; i < stepArr.length; i++) {
          Step step = stepArr[i].step;

          if (step.durationType != null
              || step.intensity == Intensity.REPEAT
              || step.intensity == Intensity.RESTING
              || i + 1 >= stepArr.length) continue;

          Step next = stepArr[i + 1].step;

          if (next.intensity == Intensity.RESTING) continue;

          Step s = Step.createRestStep(Dimension.TIME, val, convertRestToRecovery);
          if (stepArr[i].parent == null) {
            w.steps.add(i + 1, s);
            Log.d("WorkoutBuilder", "Added step at index: " + (i + 1));
          } else {
            RepeatStep rs = (RepeatStep) stepArr[i].parent;
            int idx = rs.steps.indexOf(step);
            rs.steps.add(idx, s);
            Log.d(
                "WorkoutBuilder",
                "Added step at index: " + (i + 1) + " repeat index: " + (idx + 1));
          }
        }
      }
    }
  }

  public static void addFeedbackFromPreferences(
      SharedPreferences prefs, Resources res, ArrayList<Feedback> feedback) {
    int feedbackStart = feedback.size();

    /* TOTAL */
    if (prefs.getBoolean(res.getString(R.string.cueinfo_total_distance), false)) {
      feedback.add(new AudioFeedback(Scope.ACTIVITY, Dimension.DISTANCE));
    }
    if (prefs.getBoolean(res.getString(R.string.cueinfo_total_time), false)) {
      feedback.add(new AudioFeedback(Scope.ACTIVITY, Dimension.TIME));
    }
    if (Dimension.SPEED_CUE_ENABLED
        && prefs.getBoolean(res.getString(R.string.cueinfo_total_speed), false)) {
      feedback.add(new AudioFeedback(Scope.ACTIVITY, Dimension.SPEED));
    }
    if (prefs.getBoolean(res.getString(R.string.cueinfo_total_pace), false)) {
      feedback.add(new AudioFeedback(Scope.ACTIVITY, Dimension.PACE));
    }
    if (prefs.getBoolean(res.getString(R.string.cueinfo_total_hr), false)) {
      feedback.add(new AudioFeedback(Scope.ACTIVITY, Dimension.HR));
    }
    if (prefs.getBoolean(res.getString(R.string.cueinfo_total_hrz), false)) {
      feedback.add(new AudioFeedback(Scope.ACTIVITY, Dimension.HRZ));
    }

    /* STEP */
    if (prefs.getBoolean(res.getString(R.string.cueinfo_step_distance), false)) {
      feedback.add(new AudioFeedback(Scope.STEP, Dimension.DISTANCE));
    }
    if (prefs.getBoolean(res.getString(R.string.cueinfo_step_time), false)) {
      feedback.add(new AudioFeedback(Scope.STEP, Dimension.TIME));
    }
    if (Dimension.SPEED_CUE_ENABLED
        && prefs.getBoolean(res.getString(R.string.cueinfo_step_speed), false)) {
      feedback.add(new AudioFeedback(Scope.STEP, Dimension.SPEED));
    }
    if (prefs.getBoolean(res.getString(R.string.cueinfo_step_pace), false)) {
      feedback.add(new AudioFeedback(Scope.STEP, Dimension.PACE));
    }
    if (prefs.getBoolean(res.getString(R.string.cueinfo_step_hr), false)) {
      feedback.add(new AudioFeedback(Scope.STEP, Dimension.HR));
    }
    if (prefs.getBoolean(res.getString(R.string.cueinfo_step_hrz), false)) {
      feedback.add(new AudioFeedback(Scope.STEP, Dimension.HRZ));
    }

    /* LAP */
    if (prefs.getBoolean(res.getString(R.string.cueinfo_lap_distance), false)) {
      feedback.add(new AudioFeedback(Scope.LAP, Dimension.DISTANCE));
    }
    if (prefs.getBoolean(res.getString(R.string.cueinfo_lap_time), false)) {
      feedback.add(new AudioFeedback(Scope.LAP, Dimension.TIME));
    }
    if (Dimension.SPEED_CUE_ENABLED
        && prefs.getBoolean(res.getString(R.string.cueinfo_lap_speed), false)) {
      feedback.add(new AudioFeedback(Scope.LAP, Dimension.SPEED));
    }
    if (prefs.getBoolean(res.getString(R.string.cueinfo_lap_pace), false)) {
      feedback.add(new AudioFeedback(Scope.LAP, Dimension.PACE));
    }
    if (prefs.getBoolean(res.getString(R.string.cueinfo_lap_hr), false)) {
      feedback.add(new AudioFeedback(Scope.LAP, Dimension.HR));
    }
    if (prefs.getBoolean(res.getString(R.string.cueinfo_lap_hrz), false)) {
      feedback.add(new AudioFeedback(Scope.LAP, Dimension.HRZ));
    }

    /* CURRENT */
    if (prefs.getBoolean(res.getString(R.string.cueinfo_current_pace_and_hr), false)) {
      feedback.add(new CurrentPaceAndHrAudioFeedback());
    } else {
      if (prefs.getBoolean(res.getString(R.string.cueinfo_current_pace), false)) {
        feedback.add(new AudioFeedback(Scope.CURRENT, Dimension.PACE));
      }
      if (prefs.getBoolean(res.getString(R.string.cueinfo_current_hr), false)) {
        feedback.add(new AudioFeedback(Scope.CURRENT, Dimension.HR));
      }
    }
    if (prefs.getBoolean(res.getString(R.string.cueinfo_current_speed), false)) {
      feedback.add(new AudioFeedback(Scope.CURRENT, Dimension.SPEED));
    }
    if (prefs.getBoolean(res.getString(R.string.cueinfo_current_hrz), false)) {
      feedback.add(new AudioFeedback(Scope.CURRENT, Dimension.HRZ));
    }
    if (prefs.getBoolean(res.getString(R.string.cueinfo_current_cad), false)) {
      feedback.add(new AudioFeedback(Scope.CURRENT, Dimension.CAD));
    }

    for (int i = feedbackStart; i < feedback.size(); i++) {
      if (feedback.get(i) instanceof AudioFeedback
          && (i == 0
              || feedback.get(i - 1) == null
              || !(feedback.get(i - 1) instanceof AudioFeedback)
              || ((AudioFeedback) feedback.get(i - 1)).getScope() == null
              || ((AudioFeedback) feedback.get(i - 1)).getScope()
                  != ((AudioFeedback) feedback.get(i)).getScope())) {
        // Insert the Scope (Activity, Lap etc) before the items
        feedback.add(i, new AudioFeedback(((AudioFeedback) feedback.get(i)).getScope()));
        i++;
      }
    }
  }
}
