/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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
import android.support.wearable.view.DelayedConfirmationView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.runnerup.R
import org.runnerup.common.tracker.TrackerState
import org.runnerup.common.util.ValueModel

/** @todo make this fragment contact phone and start app */
class ConnectToPhoneFragment : Fragment(), ValueModel.ChangeListener<TrackerState> {

    private var mButton: DelayedConfirmationView? = null
    private lateinit var activity: MainActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.connect_to_phone, container, false)
        super.onViewCreated(view, savedInstanceState)

        mButton = view.findViewById(R.id.icon_open_on_phone)
        mButton?.setListener(mListener)

        return view
    }

    private fun updateView(state: TrackerState?) {
        if (state == null) {
            mButton?.setStartTimeMs(0)
            mButton?.setTotalTimeMs(5000)
            mButton?.start()
        }
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

    private val mListener =
        object : DelayedConfirmationView.DelayedConfirmationListener {
            override fun onTimerFinished(view: View) {
                updateView(activity.getTrackerState())
            }

            override fun onTimerSelected(view: View) {}
        }
}
