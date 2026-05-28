/*
 * Copyright (C) 2026 RunnerUp authors
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

package org.runnerup.data;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.runnerup.common.util.Constants;

/**
 * Phase 4: thin writer for the {@code activity_event} table. All callers go through
 * {@link #log} so we can change the schema or batching strategy in one place.
 *
 * <p>Writes are fire-and-forget — failures are logged but never thrown, because losing an
 * event row should never crash a running workout.
 */
public final class ActivityEventLogger implements Constants {

  private static final String TAG = "ActivityEventLogger";

  private ActivityEventLogger() {}

  /**
   * Insert a single event row. {@code db} and {@code activityId} are required; other arguments
   * may be {@code 0}/{@code null} to mean "unknown" and will be persisted as SQL NULL.
   *
   * @param db database handle (no-op if {@code null})
   * @param activityId target activity (no-op if {@code <= 0})
   * @param tsElapsedMs tracker's monotonic active milliseconds at fire time, or {@code -1} if
   *     unknown (e.g. event fired before the tracker started)
   * @param eventType one of {@link DB.EVENT_TYPE}
   * @param stepId persisted step id from {@link DB.STEP}, or {@code 0} if no current step
   * @param lap current lap number, or {@code -1} if unknown
   * @param payload optional human-readable payload (e.g. cue voice text), or {@code null}
   */
  public static void log(
      SQLiteDatabase db,
      long activityId,
      long tsElapsedMs,
      int eventType,
      long stepId,
      long lap,
      String payload) {
    if (db == null || activityId <= 0) return;
    try {
      ContentValues row = new ContentValues();
      row.put(DB.ACTIVITY_EVENT.ACTIVITY, activityId);
      if (tsElapsedMs >= 0) row.put(DB.ACTIVITY_EVENT.TS_ELAPSED_MS, tsElapsedMs);
      row.put(DB.ACTIVITY_EVENT.TS_WALLCLOCK_MS, System.currentTimeMillis());
      row.put(DB.ACTIVITY_EVENT.EVENT_TYPE, eventType);
      if (stepId > 0) row.put(DB.ACTIVITY_EVENT.STEP_ID, stepId);
      if (lap >= 0) row.put(DB.ACTIVITY_EVENT.LAP, lap);
      if (payload != null) row.put(DB.ACTIVITY_EVENT.PAYLOAD, payload);
      db.insert(DB.ACTIVITY_EVENT.TABLE, null, row);
    } catch (Exception ex) {
      Log.w(TAG, "log: " + ex);
    }
  }
}
