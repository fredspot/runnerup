package org.runnerup.features;

import android.widget.LinearLayout;
import org.runnerup.core.util.Formatter;
import org.runnerup.core.util.GraphWrapper;
import org.runnerup.core.workout.Sport;
import android.database.sqlite.SQLiteDatabase;

/** Graph tab lifecycle for {@link DetailActivity}. */
final class DetailGraphController {

  private GraphWrapper graphWrapper;

  void attach(
      DetailActivity activity,
      LinearLayout graphTabLayout,
      LinearLayout hrzonesBarLayout,
      Formatter formatter,
      SQLiteDatabase db,
      long activityId,
      int sportValue) {
    boolean useDistanceAsX = !Sport.isWithoutGps(sportValue);
    graphWrapper =
        new GraphWrapper(
            activity, graphTabLayout, hrzonesBarLayout, formatter, db, activityId, useDistanceAsX);
  }

  void updateForSport(int sportValue) {
    if (graphWrapper != null) {
      graphWrapper.setUseDistanceAsX(!Sport.isWithoutGps(sportValue));
    }
  }

  GraphWrapper getGraphWrapper() {
    return graphWrapper;
  }
}
