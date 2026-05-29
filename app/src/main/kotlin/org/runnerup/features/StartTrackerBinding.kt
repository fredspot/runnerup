package org.runnerup.features

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import org.runnerup.tracking.Tracker

/** Manages binding to the GPS [Tracker] service from [StartFragment]. */
internal class StartTrackerBinding(context: Context, private val callback: Callback) {

  interface Callback {
    fun onTrackerBound(tracker: Tracker)

    fun onTrackerUnbound()
  }

  private val appContext = context.applicationContext
  private var bound = false
  var tracker: Tracker? = null
    private set

  private val connection: ServiceConnection =
      object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
          tracker = (service as Tracker.LocalBinder).service
          callback.onTrackerBound(tracker!!)
        }

        override fun onServiceDisconnected(className: ComponentName) {
          if (tracker != null) {
            callback.onTrackerUnbound()
          }
          tracker = null
        }
      }

  fun isBound(): Boolean = bound

  fun bind() {
    bound =
        appContext.bindService(
            Intent(appContext, Tracker::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
  }

  fun unbind() {
    if (bound) {
      appContext.unbindService(connection)
      bound = false
    }
    if (tracker != null) {
      callback.onTrackerUnbound()
    }
    tracker = null
  }
}
