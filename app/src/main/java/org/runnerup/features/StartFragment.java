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

package org.runnerup.features;

import android.Manifest;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.viewpager2.widget.ViewPager2;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.common.util.ValueModel;
import org.runnerup.data.DBHelper;
import org.runnerup.hr.HRProvider;
import org.runnerup.hr.MockHRProvider;
import org.runnerup.core.notification.GpsBoundState;
import org.runnerup.core.notification.GpsSearchingState;
import org.runnerup.core.notification.NotificationManagerDisplayStrategy;
import org.runnerup.core.notification.NotificationStateManager;
import org.runnerup.tracking.GpsInformation;
import org.runnerup.tracking.Tracker;
import org.runnerup.tracking.component.TrackerCadence;
import org.runnerup.tracking.component.TrackerHRM;
import org.runnerup.tracking.component.TrackerWear;
import org.runnerup.core.util.Formatter;
import org.runnerup.core.util.SafeParse;
import org.runnerup.core.util.TickListener;
import org.runnerup.ui.common.widget.ClassicSpinner;
import org.runnerup.ui.common.widget.SpinnerInterface.OnCloseDialogListener;
import org.runnerup.ui.common.widget.SpinnerInterface.OnSetValueListener;
import org.runnerup.ui.common.widget.TitleSpinner;
import org.runnerup.core.workout.Dimension;
import org.runnerup.core.workout.Sport;
import org.runnerup.core.workout.Workout;
import org.runnerup.core.workout.WorkoutBuilder;

public class StartFragment extends Fragment implements TickListener, GpsInformation {

  private static final String TAB_BASIC = "basic";
  private static final String TAB_INTERVAL = "interval";
  static final String TAB_ADVANCED = "advanced";

  boolean statusDetailsShown = false;

  // StartFragment normally stop GPS in onDestroy (or onStop)
  // but if the fragment stop as it has started a RunActivity
  // it should not!
  // TODO Figure out a way to do this prettier
  boolean runActivityPending = false;
  Tracker mTracker = null;
  private StartTrackerBinding trackerBinding;
  org.runnerup.tracking.GpsStatus mGpsStatus = null;
  private StartGpsController gpsController;
  private StartPermissionsController permissionsController;
  private StartStatusController statusController;
  private StartHrController hrController;
  private StartWorkoutPickerController workoutController;
  StartIntervalController intervalController;
  StartAdvancedWorkoutController advancedController;
  private StartUiState uiState;
  StartTabContentBinder startTabContentBinder;
  StartLaunchController startLaunchController;

  ViewPager2 startPager = null;
  boolean startTabContentBound = false;
  private Button startButton = null;

  private ImageView expandIcon = null;
  private TextView noDevicesConnected = null;

  private Button gpsEnable = null;
  ImageView gpsIndicator = null;
  TextView gpsMessage = null;
  LinearLayout gpsDetailRow = null;
  ImageView gpsDetailIndicator = null;
  TextView gpsDetailMessage = null;

  private View hrIndicator = null;
  private TextView hrMessage = null;

  private View wearOsIndicator = null;
  private TextView wearOsMessage = null;
  private TrackerWear.WearNotifier mWearNotifier = null;

  boolean sportWithoutGps = false;
  boolean batteryLevelMessageShown = false;

  TitleSpinner simpleTargetType = null;
  TitleSpinner simpleTargetPaceValue = null;
  TitleSpinner simpleTargetHrz = null;
  AudioSchemeListAdapter simpleAudioListAdapter = null;
  HRZonesListAdapter hrZonesAdapter = null;

  SQLiteDatabase mDB = null;

  Formatter formatter = null;
  NotificationStateManager notificationStateManager;
  GpsSearchingState gpsSearchingState;
  GpsBoundState gpsBoundState;
  final ActivityResultLauncher<Intent> runActivityLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            registerStartEventListener();
            runActivityPending = false;
            ensureTrackerBound();
            updateView();
          });

  public StartFragment() {
    super(R.layout.start);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    android.util.Log.d("StartFragment", "onViewCreated() called");

    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

    Context context = requireContext();
    mDB = DBHelper.getWritableDatabase(context);
    formatter = new Formatter(context);

    ensureTrackerBound();
    mGpsStatus = new org.runnerup.tracking.GpsStatus(context);
    gpsController = new StartGpsController(this);
    permissionsController = new StartPermissionsController(this);
    statusController = new StartStatusController(this);
    hrController = new StartHrController(this);
    workoutController = new StartWorkoutPickerController(this);
    intervalController = new StartIntervalController(this);
    advancedController = new StartAdvancedWorkoutController(this);
    uiState = new StartUiState(this, gpsController, hrController);
    startTabContentBinder = new StartTabContentBinder(this);
    startLaunchController = new StartLaunchController(this);
    NotificationManager notificationManager =
        ContextCompat.getSystemService(context, NotificationManager.class);
    notificationStateManager =
        new NotificationStateManager(new NotificationManagerDisplayStrategy(notificationManager));
    gpsSearchingState = new GpsSearchingState(context, this);
    gpsBoundState = new GpsBoundState(context);

    setupStartTabs(view);

    // Workout mode selector (replaces sport spinner - only running now)
    ClassicSpinner modeSpinner = view.findViewById(R.id.workout_mode_spinner);
    String[] modeArray = {
        getString(org.runnerup.common.R.string.Basic),
        getString(org.runnerup.common.R.string.Interval),
        getString(org.runnerup.common.R.string.Advanced)
    };
    ArrayAdapter<String> modeAdapter =
        new ArrayAdapter<>(context, R.layout.actionbar_spinner, modeArray);
    modeAdapter.setDropDownViewResource(R.layout.actionbar_dropdown_spinner);
    modeSpinner.setAdapter(modeAdapter);
    modeSpinner.setViewSelection(0); // Default to Basic
    
    // Set up mode spinner listener to switch tabs
    modeSpinner.setViewOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (startPager != null) {
              startPager.setCurrentItem(position, false);
            }
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {
          }
        });

    startButton = view.findViewById(R.id.start_gps_button);
    startButton.setOnClickListener(startButtonClick);

    expandIcon = view.findViewById(R.id.expand_icon);
    noDevicesConnected = view.findViewById(R.id.device_status);

    gpsIndicator = view.findViewById(R.id.gps_indicator);
    gpsMessage = view.findViewById(R.id.gps_message);
    gpsDetailRow = view.findViewById(R.id.gps_detail_row);
    gpsDetailIndicator = view.findViewById(R.id.gps_detail_indicator);
    gpsDetailMessage = view.findViewById(R.id.gps_detail_message);

    gpsEnable = view.findViewById(R.id.gps_enable_button);
    gpsEnable.setOnClickListener(gpsEnableClick);

    hrMessage = view.findViewById(R.id.hr_message);
    hrIndicator = view.findViewById(R.id.hr_indicator);

    wearOsIndicator = view.findViewById(R.id.wearos_indicator);
    wearOsMessage = view.findViewById(R.id.wearos_message);

    view.findViewById(R.id.status_layout)
        .setOnClickListener(v -> statusController.toggleStatusDetails(expandIcon, startButton));

    // ViewPager2 tab pages are inflated after layout; bind tab widgets once pages exist.
    startTabContentBinder.scheduleBind(view);

    mWearNotifier = new TrackerWear.WearNotifier(requireActivity().getApplicationContext());
    mWearNotifier.onViewCreated();

    // Set sport to running only
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putInt(getResources().getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING).apply();
    setGpsNotRequired(Sport.isWithoutGps(DB.ACTIVITY.SPORT_RUNNING));
  }

  private void setGpsNotRequired(boolean val) {
    if (sportWithoutGps == val) {
      return;
    }

    sportWithoutGps = val;
    if (sportWithoutGps) {
      // Turning GPS off
      if (mTracker != null) {
        mTracker.setWithoutGps(true);
      }
    } else {
      // Toggling GPS on
      Log.e(getClass().getName(), "mTracker.reset()");
      if (mTracker != null) {
        mTracker.setWithoutGps(false);
        mTracker.reset();
        if (mGpsStatus.isStarted()) {
          mTracker.setup();
          gpsController.startGps();
        }
      }
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    registerStartEventListener();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (!startTabContentBound) {
      View fragmentView = getView();
      if (fragmentView != null) {
        startTabContentBinder.scheduleBind(fragmentView);
      }
    }
    if (!startTabContentBound) {
      return;
    }
    simpleAudioListAdapter.reload();
    intervalController.reloadAudioAdapter();
    advancedController.reloadAdapters();
    hrZonesAdapter.reload();
    simpleTargetHrz.setAdapter(hrZonesAdapter);
    if (!hrZonesAdapter.hrZones.isConfigured()) {
      simpleTargetType.addDisabledValue(DB.DIMENSION.HRZ);
    } else {
      simpleTargetType.clearDisabled();
    }

    if (TAB_ADVANCED.contentEquals(startLaunchController.currentWorkoutTabTag())) {
      advancedController.loadAdvanced(null);
    }

    ensureTrackerBound();
    this.updateView();
    mWearNotifier.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();

    if (getAutoStartGps()) {
      // If autoStartGps, then stop it during pause
      stopGps();
    } else {
      if (mTracker != null
          && ((mTracker.getState() == TrackerState.INITIALIZED)
              || (mTracker.getState() == TrackerState.INITIALIZING))) {
        Log.e(getClass().getName(), "mTracker.reset()");
        mTracker.reset();
      }
    }
    mWearNotifier.onPause();
  }

  @Override
  public void onStop() {
    super.onStop();
    unregisterStartEventListener();
  }

  @Override
  public void onDestroy() {
    stopGps();
    if (trackerBinding != null) {
      trackerBinding.unbind();
    }
    mGpsStatus = null;

    DBHelper.closeDB(mDB);
    super.onDestroy();
    mWearNotifier.onDestroy();
  }

  private final BroadcastReceiver startEventBroadcastReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          requireActivity()
              .runOnUiThread(
                  () -> {
                    if (mTracker == null || startButton.getVisibility() != View.VISIBLE) {
                      return;
                    }

                    if (mTracker.getState() == TrackerState.INIT /* this will start gps */
                        || mTracker.getState() == TrackerState.INITIALIZED /* ...start a workout*/
                        || mTracker.getState() == TrackerState.CONNECTED) {
                      startButton.performClick();
                    }
                  });
        }
      };

  private void registerStartEventListener() {
    IntentFilter intentFilter = new IntentFilter();
    // START_WORKOUT is used by Wear/Pebble when GPS is captured
    // START_ACTIVITY should also start GPS if not done
    intentFilter.addAction(Constants.Intents.START_ACTIVITY);
    intentFilter.addAction(Constants.Intents.START_WORKOUT);
    ContextCompat.registerReceiver(
        requireActivity(),
        startEventBroadcastReceiver,
        intentFilter,
        ContextCompat.RECEIVER_NOT_EXPORTED);
  }

  void unregisterStartEventListener() {
    try {
      requireActivity().unregisterReceiver(startEventBroadcastReceiver);
    } catch (Exception ignored) {
    }
  }

  private void onGpsTrackerBound() {
    // check and request permissions at startup
    boolean missingEssentialPermission = permissionsController.checkPermissions(false);
    mTracker.setWithoutGps(sportWithoutGps);
    if (!missingEssentialPermission && getAutoStartGps()) {
      gpsController.startGps();
    } else {
      Log.e(getClass().getName(), "onGpsTrackerBound state: " + mTracker.getState());
      switch (mTracker.getState()) {
        case INIT:
        case CLEANUP:
          mTracker.setup();
          break;
        case INITIALIZING:
        case INITIALIZED:
          break;
        case CONNECTING:
        case CONNECTED:
        case STARTED:
        case PAUSED:
          break;
        case ERROR:
          break;
      }
    }
    updateView();
  }

  public boolean getAutoStartGps() {
    Context ctx = requireActivity().getApplicationContext();
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
    return pref.getBoolean(getString(R.string.pref_startgps), false);
  }

  public void stopGps() {
    if (gpsController != null) {
      gpsController.stopGps();
    }
  }

  public boolean isGpsLogging() {
    if (mGpsStatus == null) {
      // If mGpsStatus is null, GPS logging is not possible.
      return false;
    }
    return mGpsStatus.isLogging();
  }

  void performNotificationBatteryLevel(int batteryLevel) {
    if ((batteryLevel < 0) || (batteryLevel > 100)) {
      return;
    }

    Context context = requireContext();
    final String pref_key = getString(R.string.pref_battery_level_low_notification_discard);
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

    int batteryLevelHighThreshold =
        SafeParse.parseInt(
            prefs.getString(getString(R.string.pref_battery_level_high_threshold), "75"), 75);
    if ((batteryLevel > batteryLevelHighThreshold) && (prefs.contains(pref_key))) {
      prefs.edit().remove(pref_key).apply();
      return;
    }

    int batteryLevelLowThreshold =
        SafeParse.parseInt(
            prefs.getString(getString(R.string.pref_battery_level_low_threshold), "15"), 15);
    if (batteryLevel > batteryLevelLowThreshold) {
      return;
    }

    if (prefs.getBoolean(pref_key, false)) {
      return;
    }

    final CheckBox dontShowAgain = new CheckBox(context);
    dontShowAgain.setText(getResources().getText(org.runnerup.common.R.string.Do_not_show_again));

    new AlertDialog.Builder(context)
        .setView(dontShowAgain)
        .setCancelable(false)
        .setTitle(org.runnerup.common.R.string.Warning)
        .setMessage(
            getResources().getText(org.runnerup.common.R.string.Low_HRM_battery_level)
                + "\n"
                + getResources().getText(org.runnerup.common.R.string.Battery_level)
                + ": "
                + batteryLevel
                + "%")
        .setPositiveButton(
            org.runnerup.common.R.string.OK,
            (dialog, which) -> {
              if (dontShowAgain.isChecked()) {
                prefs.edit().putBoolean(pref_key, true).apply();
              }
            })
        .show();
  }

  private void setupStartTabs(View root) {
    startPager = root.findViewById(R.id.start_pager);
    int[] layouts = {
      R.layout.start_basic, R.layout.start_interval, R.layout.start_advanced
    };
    startPager.setAdapter(new StartTabAdapter(layouts));
    startPager.setOffscreenPageLimit(layouts.length);
    // Workout mode is selected via workout_mode_spinner only (no duplicate tab bar).
    startPager.setUserInputEnabled(false);
    startPager.registerOnPageChangeCallback(
        new ViewPager2.OnPageChangeCallback() {
          @Override
          public void onPageSelected(int position) {
            if (position == 2) {
              advancedController.loadAdvanced(null);
            }
            View fragmentView = getView();
            if (fragmentView != null) {
              ClassicSpinner modeSpinner = fragmentView.findViewById(R.id.workout_mode_spinner);
              if (modeSpinner != null) {
                modeSpinner.setViewSelection(position);
              }
            }
            updateView();
          }
        });
  }

  private final OnClickListener startButtonClick =
      v -> {
        if (mTracker == null) return;
        
        if (mTracker.getState() == TrackerState.CONNECTED) {
          // GPS is ready, start the workout
          workoutController.startWorkout();
        } else {
          // GPS not ready, start GPS search
          if (permissionsController.checkPermissions(true)) {
            return;
          }
          gpsController.startGps();
        }
        updateView();
      };

  private final OnClickListener gpsEnableClick =
      v -> {
        if (permissionsController.checkPermissions(true)) {
          return;
        }

        if (mTracker.getState() != TrackerState.CONNECTED) {
          gpsController.startGps();
        }
        updateView();
      };

  public void updateView() {
    if (uiState != null) {
      uiState.updateView();
    }
  }

  void performUpdateNoDevicesConnected(boolean show) {
    if (show) {
      if (noDevicesConnected != null) {
        noDevicesConnected.setVisibility(View.VISIBLE);
      }
    } else if (noDevicesConnected != null) {
      noDevicesConnected.setVisibility(View.GONE);
    }
  }

  private void updateStartButtonView() {
    do {
      if (!mGpsStatus.isStarted()) {
        break;
      }

      if (!sportWithoutGps) {
        if (!mGpsStatus.isLogging()) {
          break;
        }

        if (!mGpsStatus.isFixed()) {
          break;
        }
      }

      if (mTracker == null || trackerBinding == null || !trackerBinding.isBound()) {
        break;
      }

      if (mTracker.getState() != TrackerState.CONNECTED) {
        break;
      }

      if (TAB_ADVANCED.contentEquals(startLaunchController.currentWorkoutTabTag())
          && advancedController.advancedWorkout == null) {
        break;
      }

      startButton.setVisibility(View.VISIBLE);
      return;
    } while (false);

    startButton.setVisibility(View.GONE);
  }

  private void updateStartGpsButtonView() {
    do {
      if (mGpsStatus.isStarted()) {
        break;
      }

      //
      if (sportWithoutGps) {
        gpsEnable.setText(org.runnerup.common.R.string.Start_tracker);
      } else if (mGpsStatus.isEnabled()) {
        gpsEnable.setText(org.runnerup.common.R.string.Start_GPS);
      } else {
        gpsEnable.setText(org.runnerup.common.R.string.Enable_GPS);
      }
      gpsEnable.setVisibility(View.VISIBLE);
      return;
    } while (false);

    gpsEnable.setVisibility(View.GONE);
  }

  boolean performUpdateHrView() {
    if (mTracker == null || !mTracker.isComponentConfigured(TrackerHRM.NAME)) {
      hrIndicator.setVisibility(View.GONE);
      hrMessage.setVisibility(View.GONE);
      return false;
    }
    Integer hrVal = null;
    if (mTracker.isComponentConnected(TrackerHRM.NAME)) {
      hrVal = mTracker.getCurrentHRValue();
    }
    if (hrVal != null) {
      if (!batteryLevelMessageShown) {
        batteryLevelMessageShown = true;
        performNotificationBatteryLevel(mTracker.getCurrentBatteryLevel());
      }
    }

    hrMessage.setText(statusController.buildHrDetailString());
    hrIndicator.setVisibility(View.VISIBLE);
    if (statusDetailsShown) {
      hrMessage.setVisibility(View.VISIBLE);
    } else {
      hrMessage.setVisibility(View.GONE);
    }

    return true;
  }

  boolean performUpdateWearOsView() {
    if (mTracker == null || !mTracker.isComponentConfigured(TrackerWear.NAME)) {
      wearOsIndicator.setVisibility(View.GONE);
      wearOsMessage.setVisibility(View.GONE);
      return false;
    }

    wearOsIndicator.setVisibility(View.VISIBLE);

    if (!mTracker.isComponentConnected(TrackerWear.NAME)) {
      wearOsMessage.setVisibility(View.VISIBLE);
      wearOsMessage.setText("?");
    } else if (statusDetailsShown) {
      // wearOsMessage.setText(""); //todo show device name
      wearOsMessage.setVisibility(View.GONE);
    } else {
      wearOsMessage.setVisibility(View.GONE);
    }

    return true;
  }

  void performUpdateNewStartButton() {
    if (startButton == null) return;
    
    boolean gpsStarted = mGpsStatus != null && mGpsStatus.isStarted();
    boolean gpsFixed = mGpsStatus != null && mGpsStatus.isFixed();
    boolean trackerConnected = mTracker != null && mTracker.getState() == TrackerState.CONNECTED;
    
    if (gpsStarted && gpsFixed && trackerConnected) {
      // GPS is ready, show green "Start Activity" button
      startButton.setText("Start Activity");
      startButton.setBackgroundResource(R.drawable.button_start_activity);
      startButton.setEnabled(true);
    } else if (gpsStarted) {
      // GPS is searching, show greyed out "Start Activity" button
      startButton.setText("Start Activity");
      startButton.setBackgroundResource(R.drawable.button_start_gps_disabled);
      startButton.setEnabled(false);
    } else {
      // Not started, show blue "Start GPS" button
      startButton.setText("Start GPS");
      startButton.setBackgroundResource(R.drawable.button_start_gps);
      startButton.setEnabled(true);
    }
    startButton.setVisibility(View.VISIBLE);
  }
  
  void performUpdateHrIndicator() {
    View view = getView();
    if (view == null) return;
    ImageView hrIndicator = view.findViewById(R.id.new_hr_indicator);
    if (hrIndicator == null) return;
    
    if (mTracker != null && mTracker.isComponentConnected(TrackerHRM.NAME)) {
      // Bright white when connected
      hrIndicator.setColorFilter(getResources().getColor(android.R.color.white));
      hrIndicator.setAlpha(1.0f);
    } else {
      // Barely visible grey when not connected
      hrIndicator.setColorFilter(0xFF808080);  // Medium grey
      hrIndicator.setAlpha(0.2f);
    }
  }
  
  @Override
  public float getGpsAccuracy() {
    if (mTracker != null) {
      Location l = mTracker.getLastKnownLocation();

      if (l != null) {
        return l.getAccuracy();
      }
    }
    return -1;
  }

  public String getGpsAccuracyString(float accuracy) {
    return statusController.formatGpsAccuracy(accuracy);
  }

  private void ensureTrackerBound() {
    if (trackerBinding != null && trackerBinding.isBound() && mTracker != null) {
      onGpsTrackerBound();
      return;
    }
    if (trackerBinding == null) {
      trackerBinding =
          new StartTrackerBinding(
              requireContext(),
              new StartTrackerBinding.Callback() {
                @Override
                public void onTrackerBound(Tracker tracker) {
                  mTracker = tracker;
                  StartFragment.this.onGpsTrackerBound();
                  if (mTracker != null) {
                    mTracker.registerTrackerStateListener(trackerStateListener);
                  }
                }

                @Override
                public void onTrackerUnbound() {
                  if (mTracker != null) {
                    mTracker.unregisterTrackerStateListener(trackerStateListener);
                  }
                  mTracker = null;
                }
              });
    }
    trackerBinding.bind();
  }

  @Override
  public void onTick() {
    updateView();
  }

  @Override
  public int getSatellitesAvailable() {
    return mGpsStatus.getSatellitesAvailable();
  }

  @Override
  public int getSatellitesFixed() {
    return mGpsStatus.getSatellitesFixed();
  }

  private final ValueModel.ChangeListener<TrackerState> trackerStateListener =
      new ValueModel.ChangeListener<>() {
        @Override
        public void onValueChanged(
            ValueModel<TrackerState> instance, TrackerState oldValue, TrackerState newValue) {
          onTick();
        }
      };
}
