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
package org.runnerup.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.DataApi
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import org.runnerup.common.tracker.TrackerState
import org.runnerup.common.util.Constants
import org.runnerup.common.util.ValueModel
import org.runnerup.view.MainActivity
import org.runnerup.wear.WearableClient

class StateService :
    Service(), MessageApi.MessageListener, DataApi.DataListener, ValueModel.ChangeListener<Bundle> {

    private val mBinder: IBinder = LocalBinder()
    private var mGoogleApiClient: GoogleApiClient? = null
    private lateinit var mDataClient: WearableClient
    private var phoneNode: String? = null

    private var data: Bundle? = null
    private val trackerStateModel = ValueModel<TrackerState>()
    private val headers = ValueModel<Bundle>()
    private var headersListener: MainActivity? = null

    override fun onCreate() {
        super.onCreate()

        mDataClient = WearableClient(applicationContext)
        mGoogleApiClient =
            GoogleApiClient.Builder(applicationContext)
                .addConnectionCallbacks(
                    object : GoogleApiClient.ConnectionCallbacks {
                        override fun onConnected(connectionHint: Bundle?) {
                            setData()

                            val client = mGoogleApiClient!!
                            Wearable.MessageApi.addListener(client, this@StateService)
                            Wearable.DataApi.addListener(client, this@StateService)

                            readData()
                        }

                        override fun onConnectionSuspended(cause: Int) {}
                    },
                )
                .addOnConnectionFailedListener { _: ConnectionResult -> }
                .addApi(Wearable.API)
                .build()
        mGoogleApiClient?.connect()
        headers.registerChangeListener(this)

        System.err.println("StateService.onCreate()")
    }

    private fun checkConnection(): Boolean =
        mGoogleApiClient != null && mGoogleApiClient!!.isConnected && phoneNode != null

    private fun readData() {
        mDataClient.readData(Constants.Wear.Path.PHONE_NODE_ID) { dataItem ->
            if (dataItem != null) {
                phoneNode = dataItem.uri.host
                System.err.println("getDataItem => phoneNode:$phoneNode")
            }
        }
        mDataClient.readData(Constants.Wear.Path.TRACKER_STATE) { dataItem ->
            if (dataItem != null) {
                val newState = getTrackerStateFromDataItem(dataItem)
                if (newState != null) {
                    setTrackerState(newState)
                }
            }
        }
        mDataClient.readData(Constants.Wear.Path.HEADERS) { dataItem ->
            if (dataItem != null) {
                val b = DataMapItem.fromDataItem(dataItem).dataMap.toBundle()
                b.putLong(UPDATE_TIME, System.currentTimeMillis())
                headers.set(b)
            }
        }
    }

    private fun setData() {
        mDataClient.putData(Constants.Wear.Path.WEAR_NODE_ID)
    }

    private fun clearData() {
        mDataClient.deleteData(Constants.Wear.Path.WEAR_NODE_ID)
    }

    override fun onDestroy() {
        System.err.println("StateService.onDestroy()")
        trackerStateModel.clearListeners()
        mGoogleApiClient?.let { client ->
            if (client.isConnected) {
                phoneNode = null

                clearData()
                Wearable.MessageApi.removeListener(client, this)
                Wearable.DataApi.removeListener(client, this)
            }
            client.disconnect()
        }
        mGoogleApiClient = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder = mBinder

    override fun onValueChanged(instance: ValueModel<Bundle>?, oldValue: Bundle?, newValue: Bundle?) {
        headersListener?.onValueChanged(newValue)
    }

    inner class LocalBinder : android.os.Binder() {
        val service: StateService
            get() = this@StateService
    }

    private fun getBundle(src: Bundle?, lastUpdateTime: Long): Bundle? {
        if (src == null) return null

        val updateTime = src.getLong(UPDATE_TIME, 0)
        if (lastUpdateTime >= updateTime) return null

        val b = Bundle()
        b.putAll(src)
        return b
    }

    fun getHeaders(lastUpdateTime: Long): Bundle? = getBundle(headers.get(), lastUpdateTime)

    fun getData(lastUpdateTime: Long): Bundle? = getBundle(data, lastUpdateTime)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (Constants.Wear.Path.MSG_WORKOUT_EVENT.contentEquals(messageEvent.path)) {
            data = DataMap.fromByteArray(messageEvent.data).toBundle()
            data?.putLong(UPDATE_TIME, System.currentTimeMillis())
        } else {
            System.err.println("onMessageReceived: $messageEvent")
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (ev in dataEvents) {
            System.err.println("onDataChanged: " + ev.dataItem.uri)
            val path = ev.dataItem.uri.path
            when {
                Constants.Wear.Path.PHONE_NODE_ID.contentEquals(path) -> setPhoneNode(ev)
                Constants.Wear.Path.HEADERS.contentEquals(path) -> setHeaders(ev)
                Constants.Wear.Path.TRACKER_STATE.contentEquals(path) -> setTrackerState(ev)
            }
        }
    }

    private fun setPhoneNode(ev: DataEvent) {
        when (ev.type) {
            DataEvent.TYPE_CHANGED -> phoneNode = String(ev.dataItem.data ?: byteArrayOf())
            DataEvent.TYPE_DELETED -> {
                phoneNode = null
                resetState()
            }
        }
    }

    private fun setHeaders(ev: DataEvent) {
        if (ev.type == DataEvent.TYPE_CHANGED) {
            val b = DataMapItem.fromDataItem(ev.dataItem).dataMap.toBundle()
            b.putLong(UPDATE_TIME, System.currentTimeMillis())
            System.err.println("setHeaders(): b=$b")
            headers.set(b)
        } else {
            headers.set(null)
            resetState()
        }
    }

    private fun setTrackerState(ev: DataEvent) {
        var newVal: TrackerState? = null
        when (ev.type) {
            DataEvent.TYPE_CHANGED -> {
                newVal = getTrackerStateFromDataItem(ev.dataItem)
                if (newVal == null) {
                    return
                }
            }
            DataEvent.TYPE_DELETED -> {
                newVal = null
                resetState()
            }
        }
        setTrackerState(newVal)
    }

    private fun resetState() {
        data = null
        headers.set(null)
    }

    private fun setTrackerState(newVal: TrackerState?) {
        trackerStateModel.set(newVal)
    }

    fun getTrackerState(): TrackerState? = trackerStateModel.get()

    fun registerTrackerStateListener(listener: ValueModel.ChangeListener<TrackerState>) {
        trackerStateModel.registerChangeListener(listener)
    }

    fun unregisterTrackerStateListener(listener: ValueModel.ChangeListener<TrackerState>) {
        trackerStateModel.unregisterChangeListener(listener)
    }

    fun registerHeadersListener(listener: MainActivity) {
        headersListener = listener
    }

    fun unregisterHeadersListener(listener: MainActivity) {
        headersListener = null
    }

    fun sendStart() {
        sendMessage(Constants.Wear.Path.MSG_CMD_WORKOUT_START)
    }

    fun sendPauseResume() {
        if (!checkConnection()) return

        when (getTrackerState()) {
            TrackerState.STARTED -> sendMessage(Constants.Wear.Path.MSG_CMD_WORKOUT_PAUSE)
            TrackerState.PAUSED -> sendMessage(Constants.Wear.Path.MSG_CMD_WORKOUT_RESUME)
            else -> {}
        }
    }

    fun sendNewLap() {
        sendMessage(Constants.Wear.Path.MSG_CMD_WORKOUT_NEW_LAP)
    }

    private fun sendMessage(path: String) {
        if (!checkConnection()) return
        val client = mGoogleApiClient ?: return
        val node = phoneNode ?: return
        Wearable.MessageApi.sendMessage(client, node, path, byteArrayOf())
    }

    companion object {
        const val UPDATE_TIME = "UPDATE_TIME"

        @JvmStatic
        fun getTrackerStateFromDataItem(dataItem: DataItem): TrackerState? {
            if (!dataItem.isDataValid) return null

            val data = dataItem.data ?: return null
            val b = DataMap.fromByteArray(data).toBundle()
            if (b.containsKey(Constants.Wear.TrackerState.STATE)) {
                return TrackerState.valueOf(b.getInt(Constants.Wear.TrackerState.STATE))
            }
            return null
        }
    }
}
