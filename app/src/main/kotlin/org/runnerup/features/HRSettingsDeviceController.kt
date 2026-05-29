/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import java.util.ArrayList
import org.runnerup.R
import org.runnerup.hr.HRDeviceRef
import org.runnerup.hr.HRManager
import org.runnerup.hr.HRProvider

/** Provider picker and BLE scan device list for [HRSettingsActivity]. */
class HRSettingsDeviceController(
    private val activity: AppCompatActivity,
    private val host: Host,
) {
  interface Host {
    fun getProviders(): List<HRProvider>
    fun getHrProvider(): HRProvider?
    fun setHrProvider(provider: HRProvider?)
    fun setScanning(scanning: Boolean)
    fun isScanning(): Boolean
    fun log(msg: String)
    fun loadAndOpen()
    fun openProvider()
    fun updateView()
    fun checkPermissions(): Boolean
    fun connectFromScan()
    fun getBtName(): String?
    fun getBtAddress(): String?
    fun setBtSelection(name: String?, address: String?)
    fun launchBluetoothSettings()
  }

  val deviceList: ArrayList<HRDeviceRef> = ArrayList()

  private val deviceAdapter = ScanDeviceAdapter(activity, deviceList)

  fun selectProvider() {
    val providers = host.getProviders()
    if (providers.isEmpty()) return

    if (providers.size == 1) {
      host.setHrProvider(HRManager.getHRProvider(activity, providers[0].providerName))
      host.openProvider()
      return
    }

    val items = providers.map { it.providerName }.toTypedArray()
    val itemNames = providers.map { it.name }.toTypedArray<CharSequence>()

    host.setHrProvider(null)
    AlertDialog.Builder(activity)
        .setTitle(org.runnerup.common.R.string.Select_type_of_Bluetooth_device)
        .setPositiveButton(org.runnerup.common.R.string.OK) { _, _ ->
          if (host.getHrProvider() == null && items.isNotEmpty()) {
            host.setHrProvider(HRManager.getHRProvider(activity, items[0]))
          }
          host.log("hrProvider = ${host.getHrProvider()?.providerName ?: "null"}")
          host.openProvider()
        }
        .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ ->
          host.setScanning(false)
          host.setHrProvider(null)
          host.loadAndOpen()
          dialog.dismiss()
        }
        .setSingleChoiceItems(itemNames, 0) { _, which ->
          host.setHrProvider(HRManager.getHRProvider(activity, items[which]))
          host.log("hrProvider = ${host.getHrProvider()?.providerName ?: "null"}")
        }
        .show()
  }

  fun startScan() {
    val hrProvider = host.getHrProvider()
    if (hrProvider == null) {
      host.log("hrProvider null in .startScan(), aborting")
      host.updateView()
      return
    }

    host.log("${hrProvider.providerName}.startScan()")
    host.updateView()
    deviceList.clear()
    deviceAdapter.notifyDataSetChanged()

    if (host.checkPermissions()) {
      return
    }

    hrProvider.startScan()

    val builder =
        AlertDialog.Builder(activity)
            .setTitle(org.runnerup.common.R.string.Scanning)
            .setPositiveButton(org.runnerup.common.R.string.Connect) { dialog, _ ->
              host.log("${hrProvider.providerName}.stopScan()")
              hrProvider.stopScan()
              host.connectFromScan()
              host.updateView()
              dialog.dismiss()
            }
            .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ ->
              host.log("${hrProvider.providerName}.stopScan()")
              hrProvider.stopScan()
              host.loadAndOpen()
              dialog.dismiss()
              host.updateView()
            }
            .setSingleChoiceItems(deviceAdapter, -1) { _, which ->
              val hrDevice = deviceList[which]
              host.setBtSelection(hrDevice.name, hrDevice.address)
            }
    if (hrProvider.includePairingBLE()) {
      builder.setNeutralButton("Pairing") { dialog, _ ->
        dialog.cancel()
        host.launchBluetoothSettings()
      }
    }
    builder.show()
  }

  fun onScanResult(device: HRDeviceRef) {
    val hrProvider = host.getHrProvider() ?: return
    host.log("${hrProvider.providerName}::onScanResult(${device.address}, ${device.name})")
    deviceList.add(device)
    deviceAdapter.notifyDataSetChanged()
  }

  fun clearHRSettings() {
    AlertDialog.Builder(activity)
        .setTitle(org.runnerup.common.R.string.Clear_HR_settings)
        .setMessage(org.runnerup.common.R.string.Are_you_sure)
        .setPositiveButton(org.runnerup.common.R.string.OK) { _, _ -> clearBtPreferences() }
        .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ -> dialog.dismiss() }
        .show()
  }

  private fun clearBtPreferences() {
    val res = activity.resources
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    prefs
        .edit()
        .remove(res.getString(R.string.pref_bt_name))
        .remove(res.getString(R.string.pref_bt_address))
        .remove(res.getString(R.string.pref_bt_provider))
        .apply()
  }

  @SuppressLint("InflateParams")
  private class ScanDeviceAdapter(
      ctx: Context,
      private val deviceList: ArrayList<HRDeviceRef>,
  ) : BaseAdapter() {
    private val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getCount(): Int = deviceList.size

    override fun getItem(position: Int): Any = deviceList[position]

    override fun getItemId(position: Int): Long = 0

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
      val row =
          convertView
              ?: inflater.inflate(android.R.layout.simple_list_item_single_choice, null)
      val tv = row.findViewById<TextView>(android.R.id.text1)
      val btDevice = deviceList[position]
      tv.tag = btDevice
      tv.text = btDevice.name
      return row
    }
  }
}
