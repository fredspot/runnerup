package org.runnerup.features;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import org.runnerup.tracking.Tracker;

/** Manages binding to the GPS {@link Tracker} service from {@link StartFragment}. */
final class StartTrackerBinding {

  interface Callback {
    void onTrackerBound(Tracker tracker);

    void onTrackerUnbound();
  }

  private final Context appContext;
  private final Callback callback;
  private boolean bound;
  private Tracker tracker;

  private final ServiceConnection connection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
          tracker = ((Tracker.LocalBinder) service).getService();
          callback.onTrackerBound(tracker);
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
          if (tracker != null) {
            callback.onTrackerUnbound();
          }
          tracker = null;
        }
      };

  StartTrackerBinding(Context context, Callback callback) {
    this.appContext = context.getApplicationContext();
    this.callback = callback;
  }

  Tracker getTracker() {
    return tracker;
  }

  boolean isBound() {
    return bound;
  }

  void bind() {
    bound =
        appContext.bindService(
            new Intent(appContext, Tracker.class), connection, Context.BIND_AUTO_CREATE);
  }

  void unbind() {
    if (bound) {
      appContext.unbindService(connection);
      bound = false;
    }
    if (tracker != null) {
      callback.onTrackerUnbound();
    }
    tracker = null;
  }
}
