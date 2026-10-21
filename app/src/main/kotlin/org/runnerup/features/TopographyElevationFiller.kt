package org.runnerup.features

import android.util.Log
import org.runnerup.core.util.BgTasks
import org.runnerup.data.DBHelper
import org.runnerup.tracking.elevation.OpenTopoDataClient
import org.runnerup.tracking.elevation.TopographyElevationService

/** Fills DEM elevation after save or when opening a run that has no gain yet. */
internal object TopographyElevationFiller {

  private const val TAG = "TopoElevation"

  fun fillAsync(activity: DetailActivity, activityId: Long, force: Boolean) {
    BgTasks.runNetwork(
        {
          val db = DBHelper.getWritableDatabase(activity)
          try {
            TopographyElevationService.ensureFilled(
                db,
                activityId,
                OpenTopoDataClient(),
                force,
            )
          } catch (t: Throwable) {
            Log.w(TAG, "Elevation fill failed for activity $activityId", t)
            false
          } finally {
            DBHelper.closeDB(db)
          }
        },
        { filled ->
          if (filled == true && !activity.isFinishing) {
            activity.fillHeaderData()
            activity.graphController.reload()
          }
        },
    )
  }
}
