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
import android.app.FragmentManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.support.wearable.view.DotsPageIndicator
import android.support.wearable.view.FragmentGridPagerAdapter
import android.support.wearable.view.GridViewPager
import android.widget.LinearLayout
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.PutDataRequest.WEAR_URI_SCHEME
import com.google.android.gms.wearable.Wearable
import org.runnerup.R
import org.runnerup.common.tracker.TrackerState
import org.runnerup.common.util.Constants
import org.runnerup.common.util.ValueModel
import org.runnerup.service.StateService
import org.runnerup.widget.MyDotsPageIndicator

class MainActivity : Activity(), Constants, ValueModel.ChangeListener<TrackerState> {

    private val handler = Handler()
    private lateinit var pager: GridViewPager
    private var mGoogleApiClient: DataClient? = null
    private var mStateService: StateService? = null
    private val trackerState = ValueModel<TrackerState>()
    private val headers = ValueModel<Bundle>()
    private var pauseStep = false
    private var scroll = 0
    private var postScrollRightRunning = false
    private var mIsBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pager = findViewById(R.id.pager)
        val pageAdapter = PagerAdapter(fragmentManager)
        pager.adapter = pageAdapter

        val verticalDotsPageIndicator = findViewById<LinearLayout>(R.id.vert_page_indicator)
        val dot2 = MyDotsPageIndicator(verticalDotsPageIndicator)

        val dotsPageIndicator = findViewById<DotsPageIndicator>(R.id.page_indicator)
        dotsPageIndicator.setPager(pager)
        dotsPageIndicator.setDotFadeWhenIdle(false)
        dotsPageIndicator.setDotFadeOutDelay(1000 * 3600 * 24)
        dotsPageIndicator.setOnPageChangeListener(dot2)
        dotsPageIndicator.setOnAdapterChangeListener(dot2)
        dot2.setPager(pager)
        mGoogleApiClient = Wearable.getDataClient(this)
    }

    override fun onResume() {
        super.onResume()
        mIsBound =
            applicationContext.bindService(
                Intent(this, StateService::class.java),
                mStateServiceConnection,
                Context.BIND_AUTO_CREATE,
            )
        putDataItem(Constants.Wear.Path.WEAR_APP, true)
    }

    fun putDataItem(path: String, value: Boolean) {
        if (value) {
            mGoogleApiClient?.putDataItem(PutDataRequest.create(path))
        } else {
            mGoogleApiClient?.deleteDataItems(
                Uri.Builder().scheme(WEAR_URI_SCHEME).path(path).build(),
            )
        }
    }

    override fun onPause() {
        super.onPause()
        putDataItem(Constants.Wear.Path.WEAR_APP, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        mStateService?.unregisterTrackerStateListener(this)
        mStateService?.unregisterHeadersListener(this)
        if (mIsBound) {
            applicationContext.unbindService(mStateServiceConnection)
            mIsBound = false
        }
        mStateService = null
    }

    private inner class PagerAdapter(fm: FragmentManager) :
        FragmentGridPagerAdapter(fm), ValueModel.ChangeListener<TrackerState> {
        var rows = 1
        var cols = 1

        init {
            update(trackerState.get())
            trackerState.registerChangeListener(this)
        }

        override fun getFragment(row: Int, col: Int): Fragment {
            if (trackerState.get() == null) return ConnectToPhoneFragment()
            if (mStateService == null) return ConnectToPhoneFragment()

            when (trackerState.get()) {
                TrackerState.INIT,
                TrackerState.INITIALIZING,
                TrackerState.CLEANUP,
                TrackerState.ERROR -> return ConnectToPhoneFragment()
                TrackerState.INITIALIZED -> return StartFragment()
                TrackerState.CONNECTING -> return SearchingFragment()
                TrackerState.CONNECTED -> return StartFragment()
                TrackerState.STARTED,
                TrackerState.PAUSED,
                TrackerState.STOPPED -> {
                    if (row == RUN_INFO_ROW) {
                        var adjustedCol = col
                        if (pauseStep) {
                            if (col == 0) return CountdownFragment()
                            adjustedCol-- // during pause step col=0 is CountDown
                        }
                        return RunInfoFragment.createForScreen(
                            adjustedCol,
                            getRowsForScreen(adjustedCol),
                        )
                    } else if (row == PAUSE_RESUME_ROW) {
                        if (trackerState.get() == TrackerState.STOPPED) return StoppedFragment()
                        return PauseResumeFragment()
                    }
                }
                else -> {}
            }
            return ConnectToPhoneFragment()
        }

        override fun getRowCount(): Int = rows

        override fun getColumnCount(i: Int): Int = if (pauseStep) cols + 1 else cols

        override fun onValueChanged(
            obj: ValueModel<TrackerState>?,
            oldValue: TrackerState?,
            newValue: TrackerState?,
        ) {
            notifyDataSetChanged()
        }

        override fun notifyDataSetChanged() {
            update(trackerState.get())
            super.notifyDataSetChanged()
        }

        private fun update(newValue: TrackerState?) {
            if (newValue == null || mStateService == null) {
                cols = 1
                rows = 1
                return
            }
            when (newValue) {
                TrackerState.INIT,
                TrackerState.INITIALIZING,
                TrackerState.CLEANUP,
                TrackerState.ERROR,
                TrackerState.INITIALIZED,
                TrackerState.CONNECTING,
                TrackerState.CONNECTED -> {
                    cols = 1
                    rows = 1
                }
                TrackerState.STARTED,
                TrackerState.PAUSED,
                TrackerState.STOPPED -> {
                    cols = getScreensCount()
                    rows = 2
                }
                else -> {}
            }
        }
    }

    private fun getRowsForScreen(col: Int): Int {
        val b = headers.get()
        if (b == null) {
            System.err.println("getRowsForScreen(): headers == null")
            return 1
        }
        val screens = b.getIntegerArrayList(Constants.Wear.RunInfo.SCREENS)
        if (screens == null) {
            System.err.println("getRowsForScreen(): screens == null")
            return 1
        }
        if (col > screens.size) return 1
        return screens[col]
    }

    private fun getScreensCount(): Int {
        val b = headers.get()
        if (b == null) {
            System.err.println("getScreensCount(): headers == null")
            return 1
        }
        val screens = b.getIntegerArrayList(Constants.Wear.RunInfo.SCREENS)
        if (screens == null) {
            System.err.println("getScreensCount(): screens == null")
            return 1
        }
        return screens.size
    }

    fun getData(lastUpdateTime: Long): Bundle? {
        return mStateService?.getData(lastUpdateTime)
    }

    fun getHeaders(lastUpdateTime: Long): Bundle? {
        return mStateService?.getHeaders(lastUpdateTime)
    }

    fun getStateService(): StateService? = mStateService

    fun scrollToRunInfo() {
        val curr = pager.currentItem
        pager.setCurrentItem(RUN_INFO_ROW, curr.x, true)
    }

    private fun scrollRight() {
        val curr = pager.currentItem
        if (curr.y != RUN_INFO_ROW) return
        if (getScreensCount() <= 1) return
        val newx = (curr.x + 1) % getScreensCount()
        pager.setCurrentItem(curr.y, newx, true)
    }

    private val mStateServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (mStateService == null) {
                    mStateService = (service as StateService.LocalBinder).service
                    mStateService?.registerTrackerStateListener(this@MainActivity)
                    mStateService?.registerHeadersListener(this@MainActivity)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                mStateService = null
            }
        }

    fun getTrackerState(): TrackerState? {
        if (mStateService == null) return null
        synchronized(trackerState) {
            return mStateService?.getTrackerState()
        }
    }

    override fun onValueChanged(
        obj: ValueModel<TrackerState>?,
        oldState: TrackerState?,
        newState: TrackerState?,
    ) {
        synchronized(trackerState) {
            runOnUiThread {
                synchronized(trackerState) {
                    trackerState.set(newState)
                }
            }
        }
    }

    fun registerTrackerStateListener(listener: ValueModel.ChangeListener<TrackerState>) {
        synchronized(trackerState) {
            trackerState.registerChangeListener(listener)
        }
    }

    fun unregisterTrackerStateListener(listener: ValueModel.ChangeListener<TrackerState>) {
        synchronized(trackerState) {
            trackerState.unregisterChangeListener(listener)
        }
    }

    private fun postScrollRight() {
        if (postScrollRightRunning) return
        if (scroll > 0 && getScreensCount() > 1) {
            postScrollRightRunning = true
            handler.postDelayed(
                {
                    runOnUiThread {
                        postScrollRightRunning = false
                        postScrollRight()
                        scrollRight()
                    }
                },
                scroll * 1000L,
            )
        }
    }

    fun onValueChanged(newValue: Bundle?) {
        synchronized(trackerState) {
            runOnUiThread {
                synchronized(trackerState) {
                    pauseStep = false
                    if (newValue != null) {
                        pauseStep = newValue.getBoolean(Constants.Wear.RunInfo.PAUSE_STEP)
                        scroll = newValue.getInt(Constants.Wear.RunInfo.SCROLL)
                    }
                    headers.set(newValue)
                    pager.adapter.notifyDataSetChanged()
                    postScrollRight()
                }
            }
        }
    }

    companion object {
        private const val RUN_INFO_ROW = 0
        private const val PAUSE_RESUME_ROW = 1
    }
}
