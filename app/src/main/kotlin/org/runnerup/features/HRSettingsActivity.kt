/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.features

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.jjoe64.graphview.DefaultLabelFormatter
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import org.runnerup.R
import org.runnerup.core.util.Formatter
import org.runnerup.core.util.ViewUtil
import org.runnerup.hr.HRData
import org.runnerup.hr.HRDeviceRef
import org.runnerup.hr.HRManager
import org.runnerup.hr.HRProvider
import org.runnerup.hr.HRProvider.HRClient
import org.runnerup.ui.common.widget.WidgetUtil

class HRSettingsActivity : AppCompatActivity(), HRClient {

  private val handler = Handler()
  private val logBuffer = StringBuffer()

  private var providers: List<HRProvider>? = null
  private var btName: String? = null
  private var btAddress: String? = null
  private var btProviderName: String? = null
  private var hrProvider: HRProvider? = null

  private lateinit var connectButton: Button
  private lateinit var scanButton: Button
  private lateinit var tvBTName: TextView
  private lateinit var tvHR: TextView
  private lateinit var tvLog: TextView
  private lateinit var tvBatteryLevel: TextView

  private lateinit var formatter: Formatter
  private lateinit var graphView: GraphView
  private lateinit var graphViewSeries: LineGraphSeries<DataPoint>

  private var lineNo = 0
  private var mIsScanning = false
  private var hrReader: Timer? = null
  private var lastTimestamp: Long = 0
  private var timerStartTime: Long = 0

  private lateinit var deviceController: HRSettingsDeviceController

  private val bluetoothEnableLauncher =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        if (hrProvider != null && !hrProvider!!.isEnabled) {
          log("Bluetooth not enabled!")
          scanButton.isEnabled = false
          connectButton.isEnabled = false
          return@registerForActivityResult
        }
        load()
        open()
      }

  private val bluetoothSettingsLauncher =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        startScan()
      }

  private val scanButtonClick =
      View.OnClickListener {
        clear()
        stopTimer()

        close()
        mIsScanning = true
        log("select HR-provider")
        deviceController.selectProvider()
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.hr_settings)
    WidgetUtil.addLegacyOverflowButton(window)

    supportActionBar?.let {
      it.setDisplayHomeAsUpEnabled(true)
      it.setBackgroundDrawable(ContextCompat.getDrawable(this, R.color.backgroundPrimary))
    }

    providers = HRManager.getHRProviderList(this)
    deviceController =
        HRSettingsDeviceController(
            this,
            object : HRSettingsDeviceController.Host {
              override fun getProviders(): List<HRProvider> = providers!!

              override fun getHrProvider(): HRProvider? = hrProvider

              override fun setHrProvider(provider: HRProvider?) {
                hrProvider = provider
              }

              override fun setScanning(scanning: Boolean) {
                mIsScanning = scanning
              }

              override fun isScanning(): Boolean = mIsScanning

              override fun log(msg: String) {
                this@HRSettingsActivity.log(msg)
              }

              override fun loadAndOpen() {
                load()
                open()
              }

              override fun openProvider() {
                open()
              }

              override fun updateView() {
                this@HRSettingsActivity.updateView()
              }

              override fun checkPermissions(): Boolean = this@HRSettingsActivity.checkPermissions()

              override fun connectFromScan() {
                connect()
              }

              override fun getBtName(): String? = btName

              override fun getBtAddress(): String? = btAddress

              override fun setBtSelection(name: String?, address: String?) {
                btName = name
                btAddress = address
              }

              override fun launchBluetoothSettings() {
                bluetoothSettingsLauncher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
              }
            })

    if (providers!!.isEmpty()) {
      notSupported()
    }

    tvLog = findViewById(R.id.hr_log)
    tvLog.movementMethod = ScrollingMovementMethod()
    tvBTName = findViewById(R.id.hr_device)
    tvHR = findViewById(R.id.hr_value)
    tvBatteryLevel = findViewById(R.id.hr_battery)
    tvBatteryLevel.visibility = View.GONE
    scanButton = findViewById(R.id.scan_button)
    scanButton.setOnClickListener(scanButtonClick)
    connectButton = findViewById(R.id.connect_button)
    connectButton.setOnClickListener { connect() }
    ViewUtil.Insets(findViewById(R.id.hr_settings_view), true)

    formatter = Formatter(this)
    graphView = GraphView(this)
    graphView.title = getString(org.runnerup.common.R.string.Heart_rate)
    val empty = arrayOf<DataPoint>()
    graphViewSeries = LineGraphSeries(empty)
    graphView.addSeries(graphViewSeries)
    graphView.viewport.isXAxisBoundsManual = true
    graphView.viewport.setMinX(0.0)
    graphView.viewport.setMaxX(X_INTERVAL)
    graphView.viewport.isYAxisBoundsManual = true
    graphView.viewport.setMinY(40.0)
    graphView.viewport.setMaxY(200.0)
    graphView.gridLabelRenderer.labelFormatter =
        object : DefaultLabelFormatter() {
          override fun formatLabel(value: Double, isValueX: Boolean): String {
            return if (isValueX) {
              formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, value.toLong())
            } else {
              formatter.formatHeartRate(Formatter.Format.TXT_SHORT, value)
            }
          }
        }
    val graphLayout = findViewById<LinearLayout>(R.id.hr_graph_layout)
    graphLayout.addView(graphView)

    load()
    open()
  }

  override fun onDestroy() {
    super.onDestroy()

    close()
    stopTimer()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.hrsettings_menu, menu)
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    val res = resources

    var isChecked = prefs.getBoolean(res.getString(R.string.pref_bt_paired_ble), false)
    menu.findItem(R.id.menu_hrdevice_paired_ble).isChecked = isChecked

    isChecked = prefs.getBoolean(res.getString(R.string.pref_bt_experimental), false)
    menu.findItem(R.id.menu_hrdevice_experimental).isChecked = isChecked

    isChecked = prefs.getBoolean(res.getString(R.string.pref_bt_mock), false)
    menu.findItem(R.id.menu_hrdevice_mock).isChecked = isChecked

    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    val id = item.itemId
    if (id == R.id.menu_hrsettings_clear) {
      deviceController.clearHRSettings()
      return true
    } else if (id == R.id.menu_hrzones) {
      startActivity(Intent(this, HRZonesActivity::class.java))
      return true
    } else if (
        id == R.id.menu_hrdevice_paired_ble ||
            id == R.id.menu_hrdevice_experimental ||
            id == R.id.menu_hrdevice_mock
    ) {
      val isChecked = !item.isChecked
      item.isChecked = isChecked
      val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
      val res = resources
      val editor = prefs.edit()
      val key =
          when (id) {
            R.id.menu_hrdevice_paired_ble -> R.string.pref_bt_paired_ble
            R.id.menu_hrdevice_experimental -> R.string.pref_bt_experimental
            else -> R.string.pref_bt_mock
          }
      editor.putBoolean(res.getString(key), isChecked)
      editor.apply()
      providers = HRManager.getHRProviderList(this)
      return true
    } else if (id == android.R.id.home) {
      finish()
      return true
    }

    return super.onOptionsItemSelected(item)
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    super.onBackPressed()
  }

  private fun startBluetoothEnableIntent(): Boolean {
    val provider = hrProvider ?: return false
    if (provider.isEnabled) {
      return false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
    ) {
      System.err.println("No BLUETOOTH_CONNECT permission in startBluetoothEnableIntent")
      return false
    }
    bluetoothEnableLauncher.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
    return true
  }

  private fun log(msg: String) {
    logBuffer.insert(0, "${++lineNo}: $msg\n")
    if (logBuffer.length > 5000) {
      logBuffer.setLength(5000)
    }
    tvLog.text = logBuffer.toString()
  }

  private fun load() {
    val res = resources
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    btName = prefs.getString(res.getString(R.string.pref_bt_name), null)
    btAddress = prefs.getString(res.getString(R.string.pref_bt_address), null)
    btProviderName = prefs.getString(res.getString(R.string.pref_bt_provider), null)
    Log.i(
        javaClass.name,
        "btName: $btName" + "btAddress: $btAddress" + "btProviderName: $btProviderName")

    if (btProviderName != null) {
      log("HRManager.get($btProviderName)")
      hrProvider = HRManager.getHRProvider(this, btProviderName)
    }
  }

  private fun open() {
    if (hrProvider != null && !hrProvider!!.isEnabled) {
      if (checkPermissions()) {
        return
      }

      if (startBluetoothEnableIntent()) {
        return
      }
      hrProvider = null
    }
    if (hrProvider != null) {
      log(hrProvider!!.providerName + ".open(this)")
      hrProvider!!.open(handler, this)
    } else {
      updateView()
    }
  }

  private fun close() {
    if (hrProvider != null) {
      log(hrProvider!!.providerName + ".close()")
      hrProvider!!.disconnect()
      hrProvider!!.close()
      hrProvider = null
    }
  }

  private fun notSupported() {
    val listener = { dialog: android.content.DialogInterface, _: Int -> dialog.dismiss() }

    AlertDialog.Builder(this)
        .setTitle(org.runnerup.common.R.string.Heart_rate_monitor_is_not_supported_for_your_device)
        .setNegativeButton(org.runnerup.common.R.string.Cancel, listener)
        .show()
  }

  private fun clear() {
    btAddress = null
    btName = null
    btProviderName = null
    clearGraph()
  }

  private fun clearGraph() {
    val empty = arrayOf<DataPoint>()
    graphViewSeries.resetData(empty)
    timerStartTime = 0
  }

  private fun updateView() {
    if (hrProvider == null) {
      scanButton.isEnabled = true
      connectButton.isEnabled = false
      connectButton.setText(org.runnerup.common.R.string.Connect)
      tvBTName.text = ""
      tvHR.text = ""
      return
    }

    if (btName != null) {
      tvBTName.text = btName
    } else {
      tvBTName.text = ""
      tvHR.text = ""
    }

    if (hrProvider!!.isConnected) {
      connectButton.setText(org.runnerup.common.R.string.Disconnect)
      connectButton.isEnabled = true
    } else if (hrProvider!!.isConnecting) {
      connectButton.isEnabled = false
      connectButton.setText(org.runnerup.common.R.string.Connecting)
    } else {
      connectButton.isEnabled = btName != null
      connectButton.setText(org.runnerup.common.R.string.Connect)
    }
  }

  private fun checkPermissions(): Boolean =
      HRSettingsBluetoothPermissions.checkPermissions(this)

  private fun startScan() {
    deviceController.startScan()
  }

  private fun connect() {
    stopTimer()
    if (hrProvider == null || btName == null || btAddress == null) {
      updateView()
      return
    }
    if (hrProvider!!.isConnecting || hrProvider!!.isConnected) {
      log(hrProvider!!.providerName + ".disconnect()")
      hrProvider!!.disconnect()
      hrProvider!!.close()
      updateView()
      return
    }

    if (checkPermissions()) {
      return
    }

    tvBTName.text = getName()
    tvHR.text = "?"
    var name = btName
    if (name == null || name.isEmpty()) {
      name = btAddress
    }
    log(hrProvider!!.providerName + ".connect(" + name + ")")
    hrProvider!!.connect(HRDeviceRef.create(btProviderName, btName, btAddress))
    updateView()
  }

  private fun save() {
    val res = resources
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    prefs
        .edit()
        .putString(res.getString(R.string.pref_bt_name), btName)
        .putString(res.getString(R.string.pref_bt_address), btAddress)
        .putString(res.getString(R.string.pref_bt_provider), hrProvider!!.providerName)
        .apply()
  }

  private fun getName(): CharSequence? {
    if (btName != null && btName!!.isNotEmpty()) return btName
    return btAddress
  }

  private fun startTimer() {
    hrReader = Timer()
    hrReader!!.schedule(
        object : TimerTask() {
          override fun run() {
            handler.post { readHR() }
          }
        },
        0,
        500)
  }

  private fun stopTimer() {
    if (hrReader == null) return

    hrReader!!.cancel()
    hrReader!!.purge()
    hrReader = null
  }

  private fun readHR() {
    if (hrProvider == null) {
      return
    }

    val data: HRData = hrProvider!!.hrData ?: return
    if (!data.hasHeartRate) {
      return
    }

    val age = data.timestamp
    val hrValue = data.hrValue
    if (timerStartTime == 0L) {
      timerStartTime = age
      val empty = arrayOf<DataPoint>()
      graphViewSeries.resetData(empty)
    }

    tvHR.text = String.format(Locale.getDefault(), "%d", hrValue)
    if (age != lastTimestamp) {
      val x = (age - timerStartTime) / 1000.0
      graphViewSeries.appendData(DataPoint(x, hrValue.toDouble()), true, GRAPH_HISTORY_SIZE)
      lastTimestamp = age

      graphView.viewport.setMinY(graphViewSeries.lowestValueY)
      graphView.viewport.setMaxY(graphViewSeries.highestValueY)
      if (x > X_INTERVAL) {
        graphView.viewport.setMinX(x - X_INTERVAL)
        graphView.viewport.setMaxX(x)
      }
    }
  }

  override fun onOpenResult(ok: Boolean) {
    log(hrProvider!!.providerName + "::onOpenResult(" + ok + ")")
    if (mIsScanning) {
      mIsScanning = false
      startScan()
      return
    }

    updateView()
  }

  override fun onScanResult(device: HRDeviceRef) {
    deviceController.onScanResult(device)
  }

  override fun onConnectResult(connectOK: Boolean) {
    log(hrProvider!!.providerName + "::onConnectResult(" + connectOK + ")")
    if (connectOK) {
      save()
      if (hrProvider!!.batteryLevel > 0) {
        tvBatteryLevel.visibility = View.VISIBLE
        tvBatteryLevel.text =
            String.format(
                Locale.getDefault(),
                "%s: %d%%",
                resources.getText(org.runnerup.common.R.string.Battery_level),
                hrProvider!!.batteryLevel)
      }
      startTimer()
    }
    updateView()
  }

  override fun onDisconnectResult(disconnectOK: Boolean) {
    log(hrProvider!!.providerName + "::onDisconnectResult(" + disconnectOK + ")")
  }

  override fun onCloseResult(closeOK: Boolean) {
    log(hrProvider!!.providerName + "::onCloseResult(" + closeOK + ")")
  }

  override fun log(src: HRProvider, msg: String) {
    log(src.providerName + ": " + msg)
  }

  companion object {
    private const val GRAPH_HISTORY_SIZE = 180
    private const val X_INTERVAL = 60.0
  }
}
