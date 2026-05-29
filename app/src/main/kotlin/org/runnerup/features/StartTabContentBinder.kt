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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import org.runnerup.R
import org.runnerup.core.workout.Dimension
import org.runnerup.ui.common.widget.ClassicSpinner
import org.runnerup.ui.common.widget.SpinnerInterface.OnCloseDialogListener
import org.runnerup.ui.common.widget.SpinnerInterface.OnSetValueListener
import org.runnerup.ui.common.widget.TitleSpinner

/** Binds Start tab pager content (basic audio/target, interval, advanced) after ViewPager2 inflates. */
internal class StartTabContentBinder(private val fragment: StartFragment) {

  fun scheduleBind(rootView: View) {
    val pager = fragment.startPager ?: return
    pager.viewTreeObserver.addOnGlobalLayoutListener(
        object : ViewTreeObserver.OnGlobalLayoutListener {
          override fun onGlobalLayout() {
            if (fragment.startTabContentBound || !fragment.isAdded) {
              removeListener()
              return
            }
            if (rootView.findViewById<View>(R.id.basic_audio_cue_spinner) != null) {
              removeListener()
              bind(rootView)
            }
          }

          private fun removeListener() {
            val observer = pager.viewTreeObserver
            if (observer.isAlive) {
              observer.removeOnGlobalLayoutListener(this)
            }
          }
        },
    )
  }

  fun bind(view: View) {
    if (fragment.startTabContentBound || !fragment.isAdded) {
      return
    }
    fragment.startTabContentBound = true
    val context = fragment.requireContext()
    val inflater = fragment.layoutInflater
    fragment.simpleAudioListAdapter = AudioSchemeListAdapter(fragment.mDB, inflater, false)
    fragment.simpleAudioListAdapter.reload()
    val simpleAudioSpinner = view.findViewById<TitleSpinner>(R.id.basic_audio_cue_spinner)
    if (simpleAudioSpinner == null) {
      fragment.startTabContentBound = false
      return
    }
    simpleAudioSpinner.setAdapter(fragment.simpleAudioListAdapter)
    simpleAudioSpinner.setOnSetValueListener(
        StartConfigureAudioListener(fragment, fragment.simpleAudioListAdapter),
    )
    fragment.simpleTargetType = view.findViewById(R.id.tab_basic_target_type)
    fragment.simpleTargetPaceValue = view.findViewById(R.id.tab_basic_target_pace_max)
    fragment.hrZonesAdapter = HRZonesListAdapter(context, inflater)
    fragment.simpleTargetHrz = view.findViewById(R.id.tab_basic_target_hrz)
    fragment.simpleTargetHrz.setAdapter(fragment.hrZonesAdapter)
    fragment.simpleTargetType.setOnCloseDialogListener(simpleTargetTypeClick)

    fragment.intervalController.bindIntervalTab(
        view,
        inflater,
        fragment.mDB,
        onSetTimeValidator,
    )
    fragment.advancedController.bindAdvancedTab(view, inflater, fragment.mDB)

    fragment.requireActivity().intent?.let { intent ->
      if (intent.hasExtra("mode") &&
          StartFragment.TAB_ADVANCED == intent.getStringExtra("mode")) {
        fragment.startPager?.setCurrentItem(2, false)
        view.findViewById<ClassicSpinner>(R.id.workout_mode_spinner)?.setViewSelection(2)
        intent.removeExtra("mode")
      }
    }

    updateTargetView()
  }

  fun updateTargetView() {
    val targetType = fragment.simpleTargetType ?: return
    val paceValue = fragment.simpleTargetPaceValue ?: return
    val hrz = fragment.simpleTargetHrz ?: return
    val dim = Dimension.valueOf(targetType.valueInt)
    if (dim == null) {
      paceValue.isEnabled = false
      hrz.isEnabled = false
    } else {
      when (dim) {
        Dimension.PACE -> {
          paceValue.isEnabled = true
          paceValue.visibility = View.VISIBLE
          hrz.visibility = View.GONE
        }
        Dimension.HRZ -> {
          paceValue.visibility = View.GONE
          hrz.isEnabled = true
          hrz.visibility = View.VISIBLE
        }
        else -> {}
      }
    }
  }

  private val simpleTargetTypeClick =
      OnCloseDialogListener { _, ok ->
        if (ok) {
          updateTargetView()
        }
      }

  private val onSetTimeValidator =
      object : OnSetValueListener {
        override fun preSetValue(newValue: String): String {
          if (org.runnerup.core.workout.WorkoutBuilder.validateSeconds(newValue)) {
            return newValue
          }
          throw IllegalArgumentException("Unable to parse time value: $newValue")
        }

        override fun preSetValue(newValue: Int): Int = newValue
      }
}
