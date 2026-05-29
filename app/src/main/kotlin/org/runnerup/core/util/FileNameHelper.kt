/*
 * Copyright (C) 2012 - 2020 jonas.oreland@gmail.com
 */

package org.runnerup.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** File name helper for exporting/uploading */
object FileNameHelper {
  @JvmStatic
  fun getExportFileName(activityStartTime: Long, activityType: String): String =
      String.format(
          Locale.getDefault(),
          "RunnerUp_%s_%s.",
          unixTimeToString(activityStartTime),
          activityType,
      )

  @JvmStatic
  fun getExportFileNameWithModel(activityStartTime: Long, activityType: String): String =
      String.format(
          Locale.getDefault(),
          "/RunnerUp_%s_%s_%s.",
          android.os.Build.MODEL.replace("\\s".toRegex(), "_"),
          unixTimeToString(activityStartTime),
          activityType,
      )

  private fun unixTimeToString(timeStamp: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
    return format.format(Date(timeStamp * 1000L))
  }
}
