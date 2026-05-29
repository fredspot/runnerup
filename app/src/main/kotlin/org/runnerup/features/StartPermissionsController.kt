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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.runnerup.R
import org.runnerup.tracking.component.TrackerCadence

/** Runtime permissions and battery-optimization prompts for [StartFragment]. */
internal class StartPermissionsController(
    private val fragment: StartFragment,
) {
  companion object {
    const val REQUEST_LOCATION = 3000
  }

  fun getPermissions(): List<String> {
    val requiredPerms = ArrayList<String>()
    requiredPerms.add(Manifest.permission.ACCESS_FINE_LOCATION)
    requiredPerms.add(Manifest.permission.ACCESS_COARSE_LOCATION)

    val ctx = fragment.requireContext()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      requiredPerms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
      val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
      val enabled =
          prefs.getBoolean(
              fragment.getString(R.string.pref_use_cadence_step_sensor),
              true,
          )
      if (enabled && TrackerCadence.isAvailable(ctx)) {
        requiredPerms.add(Manifest.permission.ACTIVITY_RECOGNITION)
      }
    }

    val packageManager = fragment.requireContext().packageManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE) ||
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH))) {
      val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
      val btDeviceName = prefs.getString(fragment.getString(R.string.pref_bt_name), null)
      if (!btDeviceName.isNullOrEmpty()) {
        requiredPerms.add(Manifest.permission.BLUETOOTH_CONNECT)
        requiredPerms.add(Manifest.permission.BLUETOOTH_SCAN)
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      requiredPerms.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    return requiredPerms
  }

  /** @return true if essential permissions are still missing */
  fun checkPermissions(popup: Boolean): Boolean {
    var missingEssentialPermission = false
    var missingAnyPermission = false
    val requiredPerms = getPermissions()
    val requestPerms = ArrayList<String>()
    val ctx = fragment.requireContext()

    for (perm in requiredPerms) {
      if (ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED) {
        missingAnyPermission = true
        val nonEssential =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                perm == Manifest.permission.ACTIVITY_RECOGNITION) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (perm == Manifest.permission.BLUETOOTH_CONNECT ||
                        perm == Manifest.permission.BLUETOOTH_SCAN)) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    perm == Manifest.permission.POST_NOTIFICATIONS)
        missingEssentialPermission = missingEssentialPermission || !nonEssential
        if (ActivityCompat.shouldShowRequestPermissionRationale(fragment.requireActivity(), perm)) {
          Log.i(fragment.javaClass.name, "Permission $perm is explicitly denied")
        } else {
          requestPerms.add(perm)
        }
      }
    }

    if (missingAnyPermission) {
      val permissions = requestPerms.toTypedArray()
      if (popup && missingEssentialPermission || requestPerms.isNotEmpty()) {
        val baseMessage =
            when {
              Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                  fragment.getString(org.runnerup.common.R.string.GPS_permission_text_Android12)
              Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                  fragment.getString(org.runnerup.common.R.string.GPS_permission_text)
              else ->
                  fragment.getString(org.runnerup.common.R.string.GPS_permission_text_pre_Android10)
            }

        val builder =
            AlertDialog.Builder(ctx)
                .setTitle(org.runnerup.common.R.string.GPS_permission_required)
                .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ ->
                  dialog.dismiss()
                }
        if (requestPerms.isNotEmpty()) {
          builder
              .setPositiveButton(org.runnerup.common.R.string.OK) { _, _ ->
                ActivityCompat.requestPermissions(
                    fragment.requireActivity(),
                    permissions,
                    REQUEST_LOCATION,
                )
              }
              .setMessage(
                  "$baseMessage\n${fragment.getString(org.runnerup.common.R.string.Request_permission_text)}",
              )
        } else {
          val intent =
              Intent()
                  .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                  .setData(Uri.fromParts("package", ctx.packageName, null))
          builder
              .setPositiveButton(org.runnerup.common.R.string.OK) { _, _ ->
                fragment.startActivity(intent)
              }
              .setMessage(
                  "$baseMessage\n\n${fragment.getString(org.runnerup.common.R.string.Request_permission_text)}",
              )
        }
        builder.show()
      }
    }

    maybeShowBatteryOptimizationDialog(ctx, popup)
    return missingEssentialPermission
  }

  private fun maybeShowBatteryOptimizationDialog(ctx: Context, popup: Boolean) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
    val res = fragment.resources
    val suppressOptimizeBatteryPopup =
        prefs.getBoolean(res.getString(R.string.pref_suppress_battery_optimization_popup), false)
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    if ((popup || fragment.getAutoStartGps()) &&
        !suppressOptimizeBatteryPopup &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
        !pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
      val intent: Intent
      val msgId: Int
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        intent =
            Intent()
                .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", ctx.packageName, null))
        msgId = org.runnerup.common.R.string.Battery_optimization_check_text_Android9
      } else {
        intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        msgId = org.runnerup.common.R.string.Battery_optimization_check_text
      }

      AlertDialog.Builder(ctx)
          .setTitle(org.runnerup.common.R.string.Battery_optimization_check)
          .setMessage(msgId)
          .setPositiveButton(org.runnerup.common.R.string.OK) { _, _ ->
            fragment.startActivity(intent)
          }
          .setNeutralButton(org.runnerup.common.R.string.Do_not_show_again) { _, _ ->
            prefs
                .edit()
                .putBoolean(res.getString(R.string.pref_suppress_battery_optimization_popup), true)
                .apply()
          }
          .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ -> dialog.dismiss() }
          .show()
    }
  }
}
