/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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

import android.app.NotificationManager
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import org.runnerup.R
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.notification.GpsBoundState
import org.runnerup.core.notification.GpsSearchingState
import org.runnerup.core.notification.NotificationStateManager
import org.runnerup.core.util.Formatter
import org.runnerup.core.util.TickListener
import org.runnerup.core.workout.Sport
import org.runnerup.data.DBHelper
import org.runnerup.tracking.GpsInformation
import org.runnerup.tracking.GpsStatus
import org.runnerup.tracking.Tracker
import org.runnerup.tracking.component.TrackerWear
import org.runnerup.ui.common.widget.TitleSpinner

class StartFragment : Fragment(R.layout.start), TickListener, GpsInformation {

  companion object {
    internal const val TAB_ADVANCED = "advanced"
  }

  internal var statusDetailsShown = false
  internal var runActivityPending = false
  internal var mTracker: Tracker? = null
  internal var mGpsStatus: GpsStatus? = null
  internal lateinit var gpsController: StartGpsController
  internal lateinit var permissionsController: StartPermissionsController
  internal lateinit var statusController: StartStatusController
  internal lateinit var hrController: StartHrController
  internal lateinit var workoutController: StartWorkoutPickerController
  internal lateinit var intervalController: StartIntervalController
  internal lateinit var advancedController: StartAdvancedWorkoutController
  internal lateinit var trackerLifecycle: StartTrackerLifecycle
  internal lateinit var runUiController: StartRunUiController
  internal var uiState: StartUiState? = null
  internal lateinit var startTabContentBinder: StartTabContentBinder
  internal lateinit var startLaunchController: StartLaunchController

  internal var startPager: ViewPager2? = null
  internal var startTabContentBound = false
  internal var startButton: Button? = null
  internal var expandIcon: ImageView? = null
  internal var noDevicesConnected: TextView? = null
  internal lateinit var gpsEnable: Button
  internal lateinit var gpsIndicator: ImageView
  internal lateinit var gpsMessage: TextView
  internal lateinit var gpsDetailRow: LinearLayout
  internal lateinit var gpsDetailIndicator: ImageView
  internal lateinit var gpsDetailMessage: TextView
  internal var hrIndicator: View? = null
  internal var hrMessage: TextView? = null
  internal var wearOsIndicator: View? = null
  internal var wearOsMessage: TextView? = null
  private var mWearNotifier: TrackerWear.WearNotifier? = null

  internal var sportWithoutGps = false
  internal var batteryLevelMessageShown = false

  internal var simpleTargetType: TitleSpinner? = null
  internal var simpleTargetPaceValue: TitleSpinner? = null
  internal var simpleTargetHrz: TitleSpinner? = null
  internal var simpleAudioListAdapter: AudioSchemeListAdapter? = null
  internal var hrZonesAdapter: HRZonesListAdapter? = null

  internal lateinit var mDB: SQLiteDatabase
  internal lateinit var formatter: Formatter
  internal lateinit var notificationStateManager: NotificationStateManager
  internal lateinit var gpsSearchingState: GpsSearchingState
  internal lateinit var gpsBoundState: GpsBoundState

  internal val runActivityLauncher: ActivityResultLauncher<Intent> =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        trackerLifecycle.registerStartEventListener()
        runActivityPending = false
        trackerLifecycle.ensureTrackerBound()
        updateView()
      }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

    val context = requireContext()
    mDB = DBHelper.getWritableDatabase(context)
    formatter = Formatter(context)

    trackerLifecycle = StartTrackerLifecycle(this)
    mGpsStatus = GpsStatus(context)
    gpsController = StartGpsController(this)
    permissionsController = StartPermissionsController(this)
    statusController = StartStatusController(this)
    hrController = StartHrController(this)
    workoutController = StartWorkoutPickerController(this)
    intervalController = StartIntervalController(this)
    advancedController = StartAdvancedWorkoutController(this)
    runUiController = StartRunUiController(this)
    startTabContentBinder = StartTabContentBinder(this)
    startLaunchController = StartLaunchController(this)
    uiState = StartUiState(this, gpsController, runUiController)

    val notificationManager =
        ContextCompat.getSystemService(context, NotificationManager::class.java)
    notificationStateManager =
        NotificationStateManager.forNotificationManager(notificationManager)
    gpsSearchingState = GpsSearchingState(context, this)
    gpsBoundState = GpsBoundState(context)

    runUiController.setupStartTabs(view)
    runUiController.setupModeSpinner(view)

    startButton = view.findViewById(R.id.start_gps_button)
    expandIcon = view.findViewById(R.id.expand_icon)
    noDevicesConnected = view.findViewById(R.id.device_status)
    gpsIndicator = view.findViewById(R.id.gps_indicator)
    gpsMessage = view.findViewById(R.id.gps_message)
    gpsDetailRow = view.findViewById(R.id.gps_detail_row)
    gpsDetailIndicator = view.findViewById(R.id.gps_detail_indicator)
    gpsDetailMessage = view.findViewById(R.id.gps_detail_message)
    gpsEnable = view.findViewById(R.id.gps_enable_button)
    hrMessage = view.findViewById(R.id.hr_message)
    hrIndicator = view.findViewById(R.id.hr_indicator)
    wearOsIndicator = view.findViewById(R.id.wearos_indicator)
    wearOsMessage = view.findViewById(R.id.wearos_message)

    runUiController.wireClickListeners()
    view.findViewById<View>(R.id.status_layout).setOnClickListener {
      statusController.toggleStatusDetails(expandIcon, startButton)
    }

    startTabContentBinder.scheduleBind(view)

    mWearNotifier = TrackerWear.WearNotifier(requireActivity().applicationContext)
    mWearNotifier?.onViewCreated()

    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    prefs.edit().putInt(getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING).apply()
    trackerLifecycle.setGpsNotRequired(Sport.isWithoutGps(DB.ACTIVITY.SPORT_RUNNING))
    trackerLifecycle.ensureTrackerBound()
  }

  override fun onStart() {
    super.onStart()
    trackerLifecycle.registerStartEventListener()
  }

  override fun onResume() {
    super.onResume()
    if (!startTabContentBound) {
      view?.let { startTabContentBinder.scheduleBind(it) }
    }
    if (!startTabContentBound) {
      return
    }
    simpleAudioListAdapter?.reload()
    intervalController.reloadAudioAdapter()
    advancedController.reloadAdapters()
    hrZonesAdapter?.reload()
    simpleTargetHrz?.setAdapter(hrZonesAdapter)
    if (hrZonesAdapter?.hrZones?.isConfigured != true) {
      simpleTargetType?.addDisabledValue(DB.DIMENSION.HRZ)
    } else {
      simpleTargetType?.clearDisabled()
    }

    if (TAB_ADVANCED == startLaunchController.currentWorkoutTabTag()) {
      advancedController.loadAdvanced(null)
    }

    trackerLifecycle.ensureTrackerBound()
    updateView()
    mWearNotifier?.onResume()
  }

  override fun onPause() {
    super.onPause()
    trackerLifecycle.onPause()
    mWearNotifier?.onPause()
  }

  override fun onStop() {
    super.onStop()
    trackerLifecycle.unregisterStartEventListener()
  }

  override fun onDestroy() {
    trackerLifecycle.stopGps()
    trackerLifecycle.unbindTracker()
    mGpsStatus = null
    DBHelper.closeDB(mDB)
    super.onDestroy()
    mWearNotifier?.onDestroy()
  }

  val autoStartGps: Boolean
    get() = trackerLifecycle.getAutoStartGps()

  fun stopGps() {
    trackerLifecycle.stopGps()
  }

  fun isGpsLogging(): Boolean = trackerLifecycle.isGpsLogging()

  fun updateView() {
    uiState?.updateView()
  }

  override fun getGpsAccuracy(): Float {
    val tracker = mTracker ?: return -1f
    val location = tracker.lastKnownLocation ?: return -1f
    return location.accuracy
  }

  fun getGpsAccuracyString(accuracy: Float): String =
      statusController.formatGpsAccuracy(accuracy)

  override fun onTick() {
    updateView()
  }

  override fun getSatellitesAvailable(): Int = mGpsStatus?.satellitesAvailable ?: 0

  override fun getSatellitesFixed(): Int = mGpsStatus?.satellitesFixed ?: 0
}
