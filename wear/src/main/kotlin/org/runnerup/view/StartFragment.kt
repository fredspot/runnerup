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
import android.support.wearable.view.CircledImageView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.runnerup.R
import org.runnerup.common.tracker.TrackerState
import org.runnerup.common.util.ValueModel

class StartFragment : Fragment(), ValueModel.ChangeListener<TrackerState> {

    private var mTxt: TextView? = null
    private lateinit var activity: MainActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.start, container, false)
        super.onViewCreated(view, savedInstanceState)

        val button = view.findViewById<CircledImageView>(R.id.icon_start)
        button.setOnClickListener(startButtonClick)
        mTxt = view.findViewById(R.id.txt_start)

        return view
    }

    private fun updateView(state: TrackerState?) {
        if (state == null) return
        when (state) {
            TrackerState.INIT,
            TrackerState.INITIALIZING,
            TrackerState.INITIALIZED -> mTxt?.setText(org.runnerup.common.R.string.Start_GPS)
            TrackerState.CONNECTING -> {}
            TrackerState.CONNECTED -> mTxt?.setText(org.runnerup.common.R.string.Start_activity)
            TrackerState.STARTED,
            TrackerState.PAUSED,
            TrackerState.STOPPED,
            TrackerState.CLEANUP,
            TrackerState.ERROR -> {}
        }
    }

    private val startButtonClick =
        View.OnClickListener { activity.getStateService()?.sendStart() }

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
        oldValue: TrackerState?,
        newValue: TrackerState?,
    ) {
        if (isAdded) updateView(newValue)
    }
}
