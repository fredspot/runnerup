/*
 * Copyright (C) 2014 weides@gmail.com
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
package org.runnerup.view

import android.app.Activity
import android.app.Fragment
import android.os.Bundle
import android.os.Handler
import android.support.wearable.view.CircledImageView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.runnerup.R
import org.runnerup.common.tracker.TrackerState
import org.runnerup.common.util.ValueModel

class PauseResumeFragment : Fragment(), ValueModel.ChangeListener<TrackerState> {

    private val handler = Handler()
    private var mButtonPauseResumeTxt: TextView? = null
    private var mButtonPauseResume: CircledImageView? = null
    private var mButtonNewLap: CircledImageView? = null
    private lateinit var activity: MainActivity
    private var clickCount: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.newlap_pause_resume, container, false)
        super.onViewCreated(view, savedInstanceState)

        mButtonPauseResume = view.findViewById(R.id.icon_resume)
        mButtonPauseResume?.setOnClickListener(pauseButtonClick)
        mButtonPauseResumeTxt = view.findViewById(R.id.txt_resume)
        mButtonNewLap = view.findViewById(R.id.icon_newlap)
        mButtonNewLap?.setOnClickListener(newLapButtonClick)

        return view
    }

    private fun updateView(state: TrackerState?) {
        if (state != null) {
            when (state) {
                TrackerState.INIT,
                TrackerState.INITIALIZING,
                TrackerState.INITIALIZED,
                TrackerState.CLEANUP,
                TrackerState.ERROR,
                TrackerState.CONNECTING,
                TrackerState.CONNECTED -> {}
                TrackerState.STARTED -> {
                    mButtonNewLap?.isEnabled = true
                    mButtonPauseResume?.isEnabled = true
                    mButtonPauseResume?.setImageResource(org.runnerup.common.R.drawable.ic_av_pause)
                    mButtonPauseResumeTxt?.text = getText(org.runnerup.common.R.string.Pause)
                    return
                }
                TrackerState.PAUSED -> {
                    mButtonNewLap?.isEnabled = true
                    mButtonPauseResume?.isEnabled = true
                    mButtonPauseResume?.setImageResource(
                        org.runnerup.common.R.drawable.ic_av_play_arrow,
                    )
                    mButtonPauseResumeTxt?.text = getText(org.runnerup.common.R.string.Resume)
                    return
                }
                TrackerState.STOPPED -> {}
            }
        }
        mButtonNewLap?.isEnabled = false
        mButtonPauseResume?.isEnabled = false
    }

    private val pauseButtonClick =
        View.OnClickListener {
            clickCount++
            activity.getStateService()?.sendPauseResume()
            when (activity.getTrackerState()) {
                TrackerState.STARTED -> updateView(TrackerState.PAUSED)
                TrackerState.PAUSED -> {
                    updateView(TrackerState.STARTED)
                    val saveClickCount = clickCount
                    handler.postDelayed(
                        {
                            if (saveClickCount != clickCount) return@postDelayed
                            activity.scrollToRunInfo()
                        },
                        SCROLL_DELAY,
                    )
                }
                else -> {}
            }
        }

    private val newLapButtonClick =
        View.OnClickListener {
            clickCount++
            activity.getStateService()?.sendNewLap()
        }

    override fun onResume() {
        super.onResume()
        activity.registerTrackerStateListener(this)
        updateView(activity.getTrackerState())
    }

    override fun onPause() {
        activity.unregisterTrackerStateListener(this)
        super.onPause()
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        this.activity = activity as MainActivity
    }

    override fun onValueChanged(
        obj: ValueModel<TrackerState>?,
        oldState: TrackerState?,
        newState: TrackerState?,
    ) {
        if (isAdded) updateView(newState)
    }

    companion object {
        private const val SCROLL_DELAY = 1500L // 1.5s
    }
}
