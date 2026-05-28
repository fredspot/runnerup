package org.runnerup.features;

import android.content.ContentValues;
import android.view.View;
import android.widget.TextView;
import org.runnerup.common.util.Constants;
import org.runnerup.core.util.ActivitySummaryBinder;
import org.runnerup.core.util.Formatter;
import org.runnerup.ui.common.widget.TitleSpinner;

/** Binds the activity summary header on the detail screen. */
final class DetailHeaderBinder {

  private DetailHeaderBinder() {}

  static void bind(
      Formatter formatter,
      TextView distanceView,
      TextView timeView,
      TextView paceView,
      View paceSeparator,
      TitleSpinner manualDistance,
      TitleSpinner sport,
      ContentValues data,
      boolean fromManualDistance) {
    double distanceMeters = 0;
    if (data.containsKey(Constants.DB.ACTIVITY.DISTANCE)) {
      distanceMeters = data.getAsDouble(Constants.DB.ACTIVITY.DISTANCE);
      if (!fromManualDistance) {
        int distance = (int) distanceMeters;
        manualDistance.setValue(Long.toString(distance));
        manualDistance.setValue(distance);
      }
    } else {
      distanceView.setText("");
    }

    long durationSeconds = 0;
    if (data.containsKey(Constants.DB.ACTIVITY.TIME)) {
      durationSeconds = data.getAsLong(Constants.DB.ACTIVITY.TIME);
    }

    if (data.containsKey(Constants.DB.ACTIVITY.DISTANCE)) {
      ActivitySummaryBinder.bindActivityHeader(
          formatter, distanceView, timeView, paceView, paceSeparator, distanceMeters, durationSeconds);
    } else {
      distanceView.setText("");
      timeView.setText("");
      paceView.setVisibility(android.view.View.GONE);
      if (paceSeparator != null) {
        paceSeparator.setVisibility(android.view.View.GONE);
      }
    }

    if (data.containsKey(Constants.DB.ACTIVITY.SPORT)) {
      sport.setValue(data.getAsInteger(Constants.DB.ACTIVITY.SPORT));
    }
  }
}
