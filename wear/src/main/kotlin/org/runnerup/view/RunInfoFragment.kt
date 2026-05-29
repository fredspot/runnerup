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
import android.os.Handler
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView
import org.runnerup.R
import org.runnerup.common.tracker.TrackerState
import org.runnerup.common.util.Constants
import org.runnerup.common.util.ValueModel
import org.runnerup.service.StateService

class RunInfoFragment : Fragment(), ValueModel.ChangeListener<TrackerState> {

    private val textViews = ArrayList<Pair<String, TextView>>(3)
    private var screen = 0
    private var rowsOnScreen = 0
    private var dataUpdateTime: Long = 0
    private var headersTimestamp: Long = 0
    private val handler = Handler()
    private var handlerOutstanding = false
    private lateinit var mainActivity: MainActivity

    private val periodicTick =
        Runnable {
            update()
            handlerOutstanding = false
            if (isResumed) {
                startTimer()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ids: IntArray
        val card: Int
        when (rowsOnScreen) {
            3 -> {
                ids = card3ids
                card = R.layout.card3
            }
            2 -> {
                ids = card2ids
                card = R.layout.card2
            }
            else -> {
                ids = card1ids
                card = R.layout.card1
            }
        }
        val view = inflater.inflate(card, container, false)
        for (i in 0 until rowsOnScreen) {
            textViews.add(
                Pair(Constants.Wear.RunInfo.DATA + screen + "." + i, view.findViewById(ids[i])),
            )
        }
        for (i in 0 until rowsOnScreen) {
            textViews.add(
                Pair(
                    Constants.Wear.RunInfo.HEADER + screen + "." + i,
                    view.findViewById(ids[rowsOnScreen + i]),
                ),
            )
        }
        return view
    }

    private fun startTimer() {
        if (handlerOutstanding) return
        handlerOutstanding = true
        handler.postDelayed(periodicTick, 1000)
    }

    private fun reset() {
        dataUpdateTime = 0
        headersTimestamp = 0
    }

    private fun update() {
        val data = mainActivity.getData(dataUpdateTime)
        if (data != null) {
            dataUpdateTime = data.getLong(StateService.UPDATE_TIME)
            update(data)
        }

        val headers = mainActivity.getHeaders(headersTimestamp)
        if (headers != null) {
            headersTimestamp = headers.getLong(StateService.UPDATE_TIME)
            update(headers)
        }
    }

    private fun update(b: Bundle) {
        for (tv in textViews) {
            if (b.containsKey(tv.first)) {
                tv.second.text = b.getString(tv.first)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startTimer()
        mainActivity.registerTrackerStateListener(this)
        reset()
        update()
        onValueChanged(null, null, mainActivity.getTrackerState())
    }

    override fun onPause() {
        mainActivity.unregisterTrackerStateListener(this)
        super.onPause()
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        mainActivity = activity as MainActivity
    }

    override fun onValueChanged(
        obj: ValueModel<TrackerState>?,
        oldValue: TrackerState?,
        newValue: TrackerState?,
    ) {
        if (!isAdded) return
        if (newValue == null) return
        if (textViews.isEmpty()) return

        if (newValue == TrackerState.PAUSED || newValue == TrackerState.STOPPED) {
            val anim = AlphaAnimation(0f, 1f)
            anim.duration = 500
            anim.startOffset = 20
            anim.repeatMode = Animation.REVERSE
            anim.repeatCount = Animation.INFINITE
            textViews[0].second.startAnimation(anim)
        } else {
            textViews[0].second.clearAnimation()
        }
    }

    companion object {
        private val card1ids =
            intArrayOf(
                R.id.textView11,
                R.id.textViewHeader11,
            )
        private val card2ids =
            intArrayOf(
                R.id.textView21,
                R.id.textView22,
                R.id.textViewHeader21,
                R.id.textViewHeader22,
            )
        private val card3ids =
            intArrayOf(
                R.id.textView31,
                R.id.textView32,
                R.id.textView33,
                R.id.textViewHeader31,
                R.id.textViewHeader32,
                R.id.textViewHeader33,
            )

        fun createForScreen(screen: Int, rowsOnScreen: Int): RunInfoFragment {
            val frag = RunInfoFragment()
            frag.screen = screen
            frag.rowsOnScreen = rowsOnScreen
            return frag
        }
    }
}
