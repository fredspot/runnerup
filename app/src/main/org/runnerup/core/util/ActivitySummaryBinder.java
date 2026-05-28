package org.runnerup.core.util;

import android.view.View;
import android.widget.TextView;

/** Binds distance, elapsed time, and pace/speed for activity list rows. */
public final class ActivitySummaryBinder {

  private ActivitySummaryBinder() {}

  public static void bind(
      Formatter formatter,
      TextView distanceView,
      TextView timeView,
      TextView paceView,
      double distanceMeters,
      long durationSeconds) {
    bind(
        formatter,
        distanceView,
        timeView,
        paceView,
        Formatter.Format.TXT_SHORT,
        Formatter.Format.TXT_SHORT,
        distanceMeters,
        durationSeconds);
  }

  public static void bind(
      Formatter formatter,
      TextView distanceView,
      TextView timeView,
      TextView paceView,
      Formatter.Format distanceFormat,
      Formatter.Format paceFormat,
      double distanceMeters,
      long durationSeconds) {
    distanceView.setText(
        formatter.formatDistance(distanceFormat, Math.round(distanceMeters)));
    timeView.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, durationSeconds));
    if (durationSeconds > 0) {
      paceView.setText(
          formatter.formatVelocityByPreferredUnit(
              paceFormat, distanceMeters / durationSeconds));
      paceView.setVisibility(View.VISIBLE);
    } else {
      paceView.setText("");
    }
  }

  /** Activity detail header: hides pace row when duration is zero. */
  public static void bindActivityHeader(
      Formatter formatter,
      TextView distanceView,
      TextView timeView,
      TextView paceView,
      View paceSeparator,
      double distanceMeters,
      long durationSeconds) {
    distanceView.setText(
        formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(distanceMeters)));
    if (durationSeconds > 0) {
      timeView.setText(
          formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, durationSeconds));
      paceView.setVisibility(View.VISIBLE);
      if (paceSeparator != null) {
        paceSeparator.setVisibility(View.VISIBLE);
      }
      paceView.setText(
          formatter.formatVelocityByPreferredUnit(
              Formatter.Format.TXT_LONG, distanceMeters / durationSeconds));
    } else {
      timeView.setText(
          durationSeconds == 0 && distanceMeters > 0
              ? ""
              : formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, durationSeconds));
      paceView.setVisibility(View.GONE);
      if (paceSeparator != null) {
        paceSeparator.setVisibility(View.GONE);
      }
    }
  }
}
