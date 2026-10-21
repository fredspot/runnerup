package org.runnerup.features

import android.util.Log
import org.runnerup.core.util.BgTasks
import org.runnerup.data.DBHelper
import org.runnerup.tracking.ActivityStepCountService

/** Reconstructs missing step totals from stored cadence on a background thread. */
internal object ActivityStepCountFiller {

  private const val TAG = "ActivitySteps"

  fun fillAsync(activity: DetailActivity, activityId: Long) {
    BgTasks.runDb(
        {
          val db = DBHelper.getWritableDatabase(activity)
          try {
            ActivityStepCountService.ensureFilled(db, activityId)
            ActivityStepCountService.hasSteps(db, activityId)
          } catch (t: Throwable) {
            Log.w(TAG, "Step backfill failed", t)
            false
          } finally {
            DBHelper.closeDB(db)
          }
        },
        { hasSteps ->
          if (hasSteps == true && !activity.isFinishing) {
            activity.fillHeaderData()
          }
          if (!activity.isFinishing) {
            fillMissingAsync(activity)
          }
        },
    )
  }

  fun fillMissingAsync(context: android.content.Context) {
    BgTasks.runDb(
        {
          val db = DBHelper.getWritableDatabase(context)
          try {
            ActivityStepCountService.fillMissing(db)
          } catch (t: Throwable) {
            Log.w(TAG, "Step backfill failed", t)
          } finally {
            DBHelper.closeDB(db)
          }
        },
        null,
    )
  }
}
