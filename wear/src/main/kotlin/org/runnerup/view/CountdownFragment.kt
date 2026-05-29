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
import android.widget.TextView
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.service.StateService

class CountdownFragment : Fragment() {

    private var dataUpdateTime: Long = 0
    private val textViews = ArrayList<Pair<String, TextView>>(3)
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
        val view = inflater.inflate(R.layout.countdown, container, false)
        textViews.add(
            Pair(Constants.Wear.RunInfo.COUNTDOWN, view.findViewById(R.id.countdown_txt)),
        )
        return view
    }

    private fun startTimer() {
        if (handlerOutstanding) return
        handlerOutstanding = true
        handler.postDelayed(periodicTick, 500)
    }

    private fun reset() {
        dataUpdateTime = 0
    }

    private fun update() {
        val data = mainActivity.getData(dataUpdateTime)
        if (data != null) {
            dataUpdateTime = data.getLong(StateService.UPDATE_TIME)
            update(data)
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
        reset()
        update()
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        mainActivity = activity as MainActivity
    }
}
