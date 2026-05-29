/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Arrays
import java.util.Timer
import java.util.TimerTask
import org.runnerup.BuildConfig
import org.runnerup.R
import org.runnerup.common.tracker.TrackerState
import org.runnerup.core.util.Formatter
import org.runnerup.core.util.TickListener
import org.runnerup.core.util.ViewUtil
import org.runnerup.core.workout.Scope
import org.runnerup.core.workout.Workout
import org.runnerup.tracking.Tracker

class RunActivity : AppCompatActivity(), TickListener {

  private var workout: Workout? = null
  private var mTracker: Tracker? = null
  private val handler = Handler()

  private var pauseButton: Button? = null
  private var newLapButton: Button? = null
  private lateinit var activityTime: TextView
  private lateinit var activityDistance: TextView
  private lateinit var activityPace: TextView
  private lateinit var lapTime: TextView
  private lateinit var lapDistance: TextView
  private lateinit var lapPace: TextView
  private lateinit var intervalTime: TextView
  private lateinit var intervalDistance: TextView
  private lateinit var intervalPace: TextView
  private lateinit var currentPace: TextView
  private lateinit var countdownView: TextView
  private var workoutListController: RunWorkoutListController? = null
  private lateinit var tableRowInterval: android.view.View
  private lateinit var formatter: Formatter
  private lateinit var metricsController: RunMetricsController
  private lateinit var activityHr: TextView
  private lateinit var lapHr: TextView
  private lateinit var intervalHr: TextView
  private lateinit var currentHr: TextView
  private lateinit var activityHeaderHr: TextView

  private val mTapArray = longArrayOf(0, 0, 0, 0)
  private var mTapIndex = 0
  private val workoutRows = ArrayList<RunWorkoutListController.WorkoutRow>()
  private lateinit var saveDetailLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
  private var saveDetailPauseCode = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    setContentView(R.layout.run)
    formatter = Formatter(this)
    saveDetailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
          handleSaveDetailResult(result)
        }

    findViewById<TextView>(R.id.velocity_label).text = formatter.formatVelocityLabel()

    val stopButton = findViewById<Button>(R.id.stop_button)
    stopButton.setOnClickListener { doStop() }
    pauseButton = findViewById(R.id.pause_button)
    pauseButton?.setOnClickListener {
      val w = workout ?: return@setOnClickListener
      if (w.isPaused) {
        w.onResume(w)
      } else {
        w.onPause(w)
      }
      setPauseButtonEnabled(!w.isPaused)
    }
    newLapButton = findViewById(R.id.new_lap_button)
    activityHeaderHr = findViewById(R.id.activity_header_hr)
    activityTime = findViewById(R.id.run_activity_time)
    activityDistance = findViewById(R.id.run_activity_distance)
    activityPace = findViewById(R.id.run_activity_pace)
    activityHr = findViewById(R.id.activity_hr)
    lapTime = findViewById(R.id.lap_time)
    lapDistance = findViewById(R.id.lap_distance)
    lapPace = findViewById(R.id.lap_pace)
    lapHr = findViewById(R.id.lap_hr)
    intervalTime = findViewById(R.id.run_interval_time)
    intervalDistance = findViewById(R.id.intervall_distance)
    tableRowInterval = findViewById(R.id.table_row_interval)
    intervalPace = findViewById(R.id.interval_pace)
    intervalHr = findViewById(R.id.interval_hr)
    currentPace = findViewById(R.id.current_pace)
    currentHr = findViewById(R.id.current_hr)
    countdownView = findViewById(R.id.countdown_text_view)
    val workoutList = findViewById<RecyclerView>(R.id.workout_list)
    workoutListController = RunWorkoutListController(this, formatter, workoutRows, workoutList)
    metricsController =
        RunMetricsController(
            formatter,
            activityTime,
            activityDistance,
            activityPace,
            lapTime,
            lapDistance,
            lapPace,
            intervalTime,
            intervalDistance,
            intervalPace,
            currentPace,
            tableRowInterval,
            activityHr,
            lapHr,
            intervalHr,
            currentHr,
            activityHeaderHr,
            workoutListController!!,
        )

    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val res = resources
    val active = prefs.getBoolean(res.getString(R.string.pref_lock_run), false)
    findViewById<TableLayout>(R.id.table_layout1).setOnTouchListener { _, event ->
      if (active && event.action == MotionEvent.ACTION_DOWN) {
        val maxTapTime = 1000
        val time = event.eventTime
        if (mTapArray[mTapIndex] != 0L && time - mTapArray[mTapIndex] < maxTapTime) {
          val enabled = pauseButton?.isEnabled != true
          pauseButton?.isEnabled = enabled
          stopButton.isEnabled = enabled
          Arrays.fill(mTapArray, 0)
        } else {
          if (mTapIndex == 0) {
            Toast.makeText(
                    applicationContext,
                    res.getString(org.runnerup.common.R.string.Lock_activity_buttons_message),
                    Toast.LENGTH_SHORT,
                )
                .show()
          }
          mTapArray[mTapIndex] = time
          mTapIndex = (mTapIndex + 1) % mTapArray.size
        }
      }
      false
    }
    bindGpsTracker()
    onBackPressedDispatcher.addCallback(
        this,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            // ignore back while in an activity
          }
        },
    )
    ViewUtil.Insets(findViewById(R.id.start_view), true)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    Log.e(javaClass.name, "onConfigurationChange => do NOTHING!!")
  }

  override fun onDestroy() {
    super.onDestroy()
    unbindGpsTracker()
    stopTimer()
  }

  private fun onGpsTrackerBound() {
    val tracker = mTracker ?: return
    val w = tracker.workout ?: return
    workout = w
    val bindValues = HashMap<String, Any>()
    bindValues[Workout.KEY_COUNTER_VIEW] = countdownView
    w.onBind(w, bindValues)
    startTimer()
    workoutListController?.populateFromWorkout(w)
    newLapButton?.setOnClickListener { w.onNewLapOrNextStep() }
    newLapButton?.setText(org.runnerup.common.R.string.Lap)
    tracker.displayNotificationState()
  }

  private var timer: Timer? = null
  private var lastLocation: Location? = null

  private fun startTimer() {
    timer =
        Timer().also {
          it.schedule(
              object : TimerTask() {
                override fun run() {
                  handler.post { onTick() }
                }
              },
              0,
              500,
          )
        }
  }

  private fun stopTimer() {
    timer?.cancel()
    timer?.purge()
    timer = null
  }

  override fun onTick() {
    val w = workout ?: return
    w.onTick()
    updateView()
    mTracker?.lastKnownLocation?.let { l2 ->
      if (lastLocation == null || l2 != lastLocation) {
        lastLocation = l2
      }
    }
  }

  private fun doStop() {
    val w = workout ?: return
    if (timer == null) {
      return
    }
    w.onStop(w)
    stopTimer()
    mTracker?.stopForeground(true)
    val intent =
        Intent(this, DetailActivity::class.java).apply {
          putExtra("mode", "save")
          putExtra("ID", mTracker!!.activityId)
        }
    saveDetailPauseCode = if (w.isPaused) 1 else 0
    saveDetailLauncher.launch(intent)
  }

  private fun handleSaveDetailResult(result: androidx.activity.result.ActivityResult) {
    val w = workout ?: run {
      finish()
      return
    }
    when (result.resultCode) {
      RESULT_OK -> {
        var manualDistance: Double? = null
        result.data?.let { data ->
          if (data.hasExtra("MANUAL_DISTANCE")) {
            manualDistance = data.getDoubleExtra("MANUAL_DISTANCE", 0.0)
          }
        }
        w.onComplete(Scope.ACTIVITY, w)
        mTracker?.completeActivity(true, manualDistance)
        mTracker = null
        finish()
      }
      RESULT_CANCELED -> {
        w.onComplete(Scope.ACTIVITY, w)
        mTracker?.completeActivity(false, null)
        mTracker = null
        finish()
      }
      RESULT_FIRST_USER -> {
        startTimer()
        if (saveDetailPauseCode == 0) {
          w.onResume(w)
        }
      }
      else -> {
        if (BuildConfig.DEBUG) {
          throw AssertionError()
        }
      }
    }
  }

  private fun setPauseButtonEnabled(enabled: Boolean) {
    val btn = pauseButton ?: return
    if (enabled) {
      btn.setText(org.runnerup.common.R.string.Pause)
      ViewCompat.setBackground(btn, AppCompatResources.getDrawable(this, R.drawable.button_modern_pause))
    } else {
      btn.setText(org.runnerup.common.R.string.Resume)
      ViewCompat.setBackground(btn, AppCompatResources.getDrawable(this, R.drawable.button_modern_pause))
    }
  }

  private fun updateView() {
    val w = workout ?: return
    val tracker = mTracker ?: return
    val isPaused = w.isPaused
    if (tracker.state == TrackerState.STOPPED && !isPaused) {
      doStop()
    } else {
      setPauseButtonEnabled(!w.isPaused)
      metricsController.updateMetrics(w, tracker)
    }
  }

  private var mIsBound = false

  private val mConnection =
      object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
          if (mTracker == null) {
            mTracker = (service as Tracker.LocalBinder).service
            onGpsTrackerBound()
          }
        }

        override fun onServiceDisconnected(className: ComponentName) {
          mIsBound = false
          mTracker = null
        }
      }

  private fun bindGpsTracker() {
    mIsBound =
        applicationContext.bindService(
            Intent(this, Tracker::class.java),
            mConnection,
            Context.BIND_AUTO_CREATE,
        )
  }

  private fun unbindGpsTracker() {
    if (mIsBound) {
      applicationContext.unbindService(mConnection)
      mIsBound = false
    }
  }
}
