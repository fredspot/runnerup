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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import androidx.wear.ongoing.Status.TextPart
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService
import org.runnerup.BuildConfig
import org.runnerup.R
import org.runnerup.common.tracker.TrackerState
import org.runnerup.common.util.Constants
import org.runnerup.view.MainActivity
import org.runnerup.wear.WearableClient

class ListenerService : WearableListenerService() {

    private val notificationId = 10

    private var mGoogleApiClient: WearableClient? = null
    private var notification: Notification? = null
    private var trackerState: TrackerState? = null
    private var phoneRunning: Boolean? = null // TrackerWear
    private var mainActivityRunning: Boolean? = null
    private var phoneApp: Boolean? = null // StartActivity

    override fun onCreate() {
        super.onCreate()
        System.err.println("ListenerService.onCreate()")
        mGoogleApiClient = WearableClient(applicationContext)
        mGoogleApiClient?.readData(Constants.Wear.Path.WEAR_APP) { dataItem ->
            mainActivityRunning = dataItem != null
            maybeShowNotification()
        }
        mGoogleApiClient?.readData(Constants.Wear.Path.PHONE_NODE_ID) { dataItem ->
            phoneRunning = dataItem != null
            maybeShowNotification()
        }
        mGoogleApiClient?.readData(Constants.Wear.Path.TRACKER_STATE) { dataItem ->
            trackerState =
                if (dataItem != null) {
                    StateService.getTrackerStateFromDataItem(dataItem)
                } else {
                    null
                }
            maybeShowNotification()
        }
        mGoogleApiClient?.readData(Constants.Wear.Path.PHONE_APP) { dataItem ->
            phoneApp = dataItem != null
            maybeShowNotification()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        System.err.println("ListenerService.onDestroy()")
        mGoogleApiClient = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        System.err.println("ListenerService.onStart()")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (ev in dataEvents) {
            System.err.println("onDataChanged: " + ev.dataItem.uri)
            val type = ev.type
            val path = ev.dataItem.uri.path
            if (type != DataEvent.TYPE_DELETED && type != DataEvent.TYPE_CHANGED) {
                continue
            }
            val deleted = type == DataEvent.TYPE_DELETED
            when {
                Constants.Wear.Path.PHONE_NODE_ID.contentEquals(path) -> phoneRunning = !deleted
                Constants.Wear.Path.WEAR_APP.contentEquals(path) -> mainActivityRunning = !deleted
                Constants.Wear.Path.TRACKER_STATE.contentEquals(path) -> {
                    if (deleted) {
                        trackerState = null
                        phoneRunning = false
                    } else {
                        trackerState = StateService.getTrackerStateFromDataItem(ev.dataItem)
                        phoneRunning = true
                    }
                }
                Constants.Wear.Path.PHONE_APP.contentEquals(path) -> phoneApp = !deleted
                else -> continue
            }
            maybeShowNotification()
        }
    }

    override fun onPeerConnected(peer: Node) {
        if (BuildConfig.DEBUG) {
            System.err.println("ListenerService.onPeerConnected: " + peer.id)
        }
    }

    override fun onPeerDisconnected(peer: Node) {
        if (BuildConfig.DEBUG) {
            System.err.println("ListenerService.onPeerDisconnected: " + peer.id)
        }
    }

    private fun maybeShowNotification() {
        System.err.println(
            "mainActivityRunning=$mainActivityRunning" +
                ", phoneApp=$phoneApp" +
                " ,phoneRunning=$phoneRunning" +
                " ,trackerState=$trackerState",
        )
        if (mainActivityRunning == true) {
            dismissNotification()
            return
        }
        if (phoneRunning == false && phoneApp == false) {
            dismissNotification()
            return
        }

        if (phoneApp == true) {
            showNotification()
            return
        }

        if (mainActivityRunning == null || phoneRunning == null || trackerState == null) {
            System.err.println("wait for read")
            return
        }
        showNotification()
    }

    private fun showNotification() {
        if (notification != null) {
            updateNotification()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        System.out.println("create notification")

        val viewIntent = Intent(this, MainActivity::class.java)
        val pendingViewIntent =
            PendingIntent.getActivity(
                this,
                0,
                viewIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val chanId = getChannelId(this)
        val builder =
            NotificationCompat.Builder(this, chanId)
                .setSmallIcon(R.drawable.ic_ongoing_notification)
                .setContentTitle(getString(org.runnerup.common.R.string.app_name))
                .setContentText(getString(org.runnerup.common.R.string.Start))
                .setContentIntent(pendingViewIntent)
                .setOngoing(true)
                .setLocalOnly(true)

        val ongoingActivity =
            OngoingActivity.Builder(this, notificationId, builder)
                .setStaticIcon(R.drawable.ic_ongoing_notification)
                .setTouchIntent(pendingViewIntent)
                .setStatus(
                    Status.Builder()
                        .addPart("Status", TextPart(getStatusString()))
                        .build(),
                )
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                .build()
        ongoingActivity.apply(this)

        notification = builder.build()
        NotificationManagerCompat.from(this).notify(notificationId, notification!!)
    }

    private fun updateNotification() {
        val ongoingActivity = OngoingActivity.recoverOngoingActivity(this)
        System.out.println("update ongoingActivity: $ongoingActivity")
        if (ongoingActivity == null) {
            return
        }
        ongoingActivity.update(
            this,
            Status.Builder().addPart("Status", TextPart(getStatusString())).build(),
        )
    }

    private fun dismissNotification() {
        System.out.println("dismissNotification")
        notification = null
        NotificationManagerCompat.from(this).cancel(notificationId)
    }

    private fun getStatusString(): String {
        if (trackerState != null) {
            when (trackerState) {
                TrackerState.INIT,
                TrackerState.INITIALIZING,
                TrackerState.CLEANUP,
                TrackerState.ERROR -> return getString(org.runnerup.common.R.string.Waiting_for_phone)
                TrackerState.INITIALIZED,
                TrackerState.CONNECTED -> return getString(org.runnerup.common.R.string.Start_activity)
                TrackerState.CONNECTING -> return getString(org.runnerup.common.R.string.Waiting_for_GPS)
                TrackerState.STARTED -> return getString(org.runnerup.common.R.string.Activity_ongoing)
                TrackerState.PAUSED -> return getString(org.runnerup.common.R.string.Activity_paused)
                TrackerState.STOPPED -> return getString(org.runnerup.common.R.string.Activity_stopped)
                else -> {}
            }
        }
        return getString(org.runnerup.common.R.string.Waiting_for_phone)
    }

    companion object {
        private var mChannel: NotificationChannel? = null

        /** Android 8.0 notification channel */
        @JvmStatic
        fun getChannelId(context: Context): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (mChannel == null) {
                    val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val id = "runnerup_ongoing"
                    val name: CharSequence = context.getString(org.runnerup.common.R.string.app_name)
                    val description =
                        context.getString(org.runnerup.common.R.string.channel_notification_ongoing)
                    val importance = NotificationManager.IMPORTANCE_HIGH
                    mChannel = NotificationChannel(id, name, importance)
                    mChannel?.description = description
                    mChannel?.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    mChannel?.setBypassDnd(true)
                    notificationManager.createNotificationChannel(mChannel!!)
                }
                return mChannel!!.id
            }
            return "unused prior to Oreo"
        }
    }
}
