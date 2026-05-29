/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.Intent
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.workout.Sport

/** Post-save upload flow for [DetailActivity]. */
internal class DetailSaveFlowController(private val activity: DetailActivity) {

  fun startUploadAfterSave() {
    activity.uploading = true
    activity.syncManager.startUploading(
        { _, _ ->
          activity.uploading = false
          val returnIntent = Intent()
          val sportValue = activity.sport!!.valueInt
          if (Sport.hasManualDistance(sportValue)) {
            returnIntent.putExtra("MANUAL_DISTANCE", activity.headerData.getAsDouble(DB.ACTIVITY.DISTANCE))
          }
          activity.setResult(android.app.Activity.RESULT_OK, returnIntent)
          activity.finish()
        },
        activity.syncController.pendingSynchronizers,
        activity.mID,
    )
  }
}
