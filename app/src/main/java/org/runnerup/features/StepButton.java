/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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

package org.runnerup.features;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import java.util.Locale;
import org.runnerup.R;
import org.runnerup.data.DBHelper;
import org.runnerup.common.util.Constants.DB.DIMENSION;
import org.runnerup.core.util.Formatter;
import org.runnerup.core.util.SafeParse;
import org.runnerup.ui.common.widget.NumberPicker;
import org.runnerup.ui.common.widget.TitleSpinner;
import org.runnerup.core.workout.Dimension;
import org.runnerup.core.workout.Intensity;
import org.runnerup.core.workout.Range;
import org.runnerup.core.workout.Step;

public class StepButton extends LinearLayout {

  private final Context mContext;
  private final ViewGroup mLayout;
  private final ImageView mIntensityIcon;
  private final TextView mDurationValue;
  private final TextView mGoalValue;
  private final Formatter formatter;

  public Step getStep() {
    return step;
  }

  private Step step;
  private Runnable mOnChangedListener = null;

  private static final boolean editRepeatCount = true;
  private static final boolean editStepButton = true;

  public StepButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    mContext = context;

    LayoutInflater inflater =
        (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.step_button, this);
    formatter = new Formatter(context);
    mLayout = findViewById(R.id.step_button_layout);
    mIntensityIcon = findViewById(R.id.step_icon);
    mDurationValue = findViewById(R.id.step_duration_value);
    mGoalValue = findViewById(R.id.step_goal_value);

    onRepeatClickListener =
        v ->
            StepEditorDialog.showEditRepeatCount(
                mContext,
                step,
                () -> {
                  setStep(step);
                  if (mOnChangedListener != null) {
                    mOnChangedListener.run();
                  }
                });

    onStepClickListener =
        v ->
            StepEditorDialog.showEditStep(
                mContext,
                step,
                () -> {
                  setStep(step);
                  if (mOnChangedListener != null) {
                    mOnChangedListener.run();
                  }
                });
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    mLayout.setEnabled(enabled);
    for (int i = 0, j = mLayout.getChildCount(); i < j; i++) {
      mLayout.getChildAt(i).setEnabled(enabled);
    }
  }

  public void setOnChangedListener(Runnable runnable) {
    mOnChangedListener = runnable;
  }

  public void setStep(Step step) {
    this.step = step;

    mDurationValue.setVisibility(VISIBLE);
    switch (step.getIntensity()) {
      case ACTIVE:
        mIntensityIcon.setImageResource(R.drawable.step_active);
        mGoalValue.setTextColor(
            ContextCompat.getColor(mContext, R.color.stepActive)); // todo check if it works
        break;
      case RESTING:
        mIntensityIcon.setImageResource(R.drawable.step_resting);
        mGoalValue.setTextColor(ContextCompat.getColor(mContext, R.color.stepResting));
        break;
      case REPEAT:
        mIntensityIcon.setImageResource(R.drawable.step_repeat);
        mDurationValue.setVisibility(GONE); // todo better wording in string
        mGoalValue.setText(
            String.format(
                Locale.getDefault(),
                getResources().getString(org.runnerup.common.R.string.repeat_times),
                step.getRepeatCount()));
        mGoalValue.setTextColor(ContextCompat.getColor(mContext, R.color.stepRepeat));
        if (editRepeatCount) mLayout.setOnClickListener(onRepeatClickListener);
        return;
      case WARMUP:
        mIntensityIcon.setImageResource(R.drawable.step_warmup);
        mGoalValue.setTextColor(ContextCompat.getColor(mContext, R.color.stepWarmup));
        break;
      case COOLDOWN:
        mIntensityIcon.setImageResource(R.drawable.step_cooldown);
        mGoalValue.setTextColor(ContextCompat.getColor(mContext, R.color.stepCooldown));
        break;
      case RECOVERY:
        mIntensityIcon.setImageResource(R.drawable.step_recovery);
        mGoalValue.setTextColor(ContextCompat.getColor(mContext, R.color.stepRecovery));
        break;
      default:
        mIntensityIcon.setImageResource(0);
    }

    Dimension durationType = step.getDurationType();
    if (durationType == null) {
      mDurationValue.setText(org.runnerup.common.R.string.Until_press);
    } else {
      mDurationValue.setText(
          formatter.format(Formatter.Format.TXT_LONG, durationType, step.getDurationValue()));
    }

    Dimension goalType = step.getTargetType();
    if (goalType == null) {
      CharSequence base = getResources().getText(step.getIntensity().getTextId());
      mGoalValue.setText(appendCueSummary(base));
    } else {
      String prefix;
      if (goalType == Dimension.HR || goalType == Dimension.HRZ)
        prefix = "HR "; // todo should use a string
      else prefix = "";

      String targetText =
          String.format(
              Locale.getDefault(),
              "%s%s-%s",
              prefix,
              formatter.format(
                  Formatter.Format.TXT_SHORT, goalType, step.getTargetValue().minValue),
              formatter.format(
                  Formatter.Format.TXT_LONG, goalType, step.getTargetValue().maxValue));
      mGoalValue.setText(appendCueSummary(targetText));
    }
    if (editStepButton) {
      mLayout.setOnClickListener(onStepClickListener);
    }
  }

  private CharSequence appendCueSummary(CharSequence base) {
    if (!step.hasPeriodicCues() && step.getAudioCueScheme() == null) {
      return base;
    }
    StringBuilder sb = new StringBuilder(base);
    if (step.getPaceCueIntervalSeconds() > 0) {
      sb.append(" · ").append(step.getPaceCueIntervalSeconds()).append("s pace");
    }
    if (step.getHrCueIntervalSeconds() > 0) {
      sb.append(" · ").append(step.getHrCueIntervalSeconds()).append("s HR");
    }
    if (step.getAudioCueScheme() != null) {
      sb.append(" · ").append(step.getAudioCueScheme());
    }
    return sb;
  }

  private OnClickListener onRepeatClickListener;
  private OnClickListener onStepClickListener;
}
