/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Bluetooth runtime permissions for [HRSettingsActivity]. */
internal object HRSettingsBluetoothPermissions {
  const val REQUEST_BLUETOOTH_PERM = 3001

  /** @return true if a permission dialog was shown (caller should abort action) */
  @JvmStatic
  fun checkPermissions(activity: AppCompatActivity): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      return false
    }

    val requiredPerms =
        listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
        )
    val requestPerms = ArrayList<String>()
    var isDeniedPermission = false

    for (perm in requiredPerms) {
      if (ContextCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)) {
          Log.i(activity.javaClass.name, "Permission $perm is explicitly denied")
          isDeniedPermission = true
        } else {
          requestPerms.add(perm)
        }
      }
    }

    if (requestPerms.isEmpty() && !isDeniedPermission) {
      return false
    }

    val permissions = requestPerms.toTypedArray()
    val builder =
        AlertDialog.Builder(activity)
            .setTitle(org.runnerup.common.R.string.Bluetooth_permission_required)
            .setMessage(activity.getString(org.runnerup.common.R.string.Request_permission_text))
            .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ -> dialog.dismiss() }
    if (requestPerms.isNotEmpty()) {
      builder.setPositiveButton(org.runnerup.common.R.string.OK) { _, _ ->
        ActivityCompat.requestPermissions(activity, permissions, REQUEST_BLUETOOTH_PERM)
      }
    } else if (isDeniedPermission) {
      val intent =
          Intent()
              .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
              .setData(Uri.fromParts("package", activity.packageName, null))
      builder.setPositiveButton(org.runnerup.common.R.string.OK) { _, _ -> activity.startActivity(intent) }
    }
    builder.show()
    return true
  }
}
