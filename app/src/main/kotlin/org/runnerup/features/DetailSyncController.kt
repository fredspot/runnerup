/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.app.AlertDialog
import org.runnerup.sync.SyncManager

/** Upload/sync account state for [DetailActivity]. */
internal class DetailSyncController {

  @JvmField val pendingSynchronizers = HashSet<String>()
  @JvmField val alreadySynched = HashSet<String>()
  @JvmField val synchedExternalId = HashMap<String, String>()

  fun openAccountUrl(activity: DetailActivity, syncManager: SyncManager, name: String) {
    if (synchedExternalId.containsKey(name) && !TextUtils.isEmpty(synchedExternalId[name])) {
      val url = syncManager.getSynchronizerByName(name).getActivityUrl(synchedExternalId[name])
      if (url != null) {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
      }
    }
  }

  fun createClearUploadListener(
      activity: DetailActivity,
      syncManager: SyncManager,
      activityId: Long,
      onCleared: Runnable,
  ): View.OnLongClickListener =
      View.OnLongClickListener { view ->
        val name = view.tag as String
        AlertDialog.Builder(activity)
            .setTitle("Clear upload for $name")
            .setMessage(org.runnerup.common.R.string.Are_you_sure)
            .setPositiveButton(org.runnerup.common.R.string.Yes) { dialog, _ ->
              dialog.dismiss()
              syncManager.clearUpload(name, activityId)
              onCleared.run()
            }
            .setNegativeButton(org.runnerup.common.R.string.No) { dialog, _ -> dialog.dismiss() }
            .show()
        false
      }

  fun createUploadListener(
      syncManager: SyncManager,
      activityId: Long,
      uploading: UploadingState,
      onComplete: Runnable,
  ): View.OnClickListener =
      View.OnClickListener {
        uploading.set(true)
        syncManager.startUploading(
            { _, _ ->
              uploading.set(false)
              onComplete.run()
            },
            pendingSynchronizers,
            activityId,
        )
      }

  fun createSendCheckedListener(updateUploadVisibility: Runnable): CompoundButton.OnCheckedChangeListener =
      CompoundButton.OnCheckedChangeListener { button, checked ->
        val name = button.tag as String
        if (alreadySynched.contains(name)) {
          button.isChecked = true
        } else {
          if (checked) {
            pendingSynchronizers.add(name)
          } else {
            pendingSynchronizers.remove(name)
          }
          updateUploadVisibility.run()
        }
      }

  fun interface UploadingState {
    fun set(uploading: Boolean)
  }
}
