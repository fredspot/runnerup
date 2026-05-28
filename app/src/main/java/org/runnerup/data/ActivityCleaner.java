/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.text.TextUtils;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import org.runnerup.common.util.Constants;

public class ActivityCleaner implements Constants {
  private long _totalSumHr = 0;
  private int _totalCount = 0;
  private int _totalMaxHr = 0;
  private long _totalTime = 0;
  private double _totalDistance = 0;
  private Location _lastLocation = null;
  private boolean _isActive = false;

  /** recompute laps aggregates based on locations */
  private void recomputeLaps(SQLiteDatabase db, long activityId) {
    recomputeLaps(db, activityId, false);
  }

  private void recomputeLaps(SQLiteDatabase db, long activityId, boolean force) {
    final String[] cols = new String[] {DB.LAP.LAP};

    ArrayList<Long> laps = new ArrayList<>();
    Cursor c =
        db.query(
            DB.LAP.TABLE,
            cols,
            DB.LAP.ACTIVITY + " = " + activityId,
            null,
            null,
            null,
            "_id",
            null);
    if (c.moveToFirst()) {
      do {
        laps.add(c.getLong(0));
      } while (c.moveToNext());
    }
    c.close();

    for (long lap : laps) {
      recomputeLap(db, activityId, lap, force);
    }
  }

  /**
   * Recompute a lap aggregate from GPS rows.
   *
   * <p>Workout-saved values are authoritative and must not be wiped:
   *
   * <ul>
   *   <li>For non-ACTIVE laps (warmup, cooldown, rest, recovery), {@code Step.onComplete(LAP)}
   *       already wrote correct DISTANCE/TIME (a paused-rest lap has no GPS rows but a real time).
   *       Never overwrite those.
   *   <li>For ACTIVE laps with non-zero saved DISTANCE/TIME, keep the saved values; GPS sums are
   *       only used when the saved values are NULL/0 (e.g. older data, or a partial last lap).
   * </ul>
   *
   * <p>HR aggregates are always (re)derived from GPS samples for this lap when present.
   */
  void recomputeLap(SQLiteDatabase db, long activityId, long lap) {
    recomputeLap(db, activityId, lap, false);
  }

  /**
   * Force-mode variant: when {@code force} is true, GPS-derived sums overwrite saved
   * DISTANCE/TIME for ACTIVE laps. For non-ACTIVE laps, distance prefers live-tracker GPS when
   * present so autolapped warm-up / cool-down (multiple 1 km laps sharing one planned 2 km step)
   * keep per-lap distances; {@code PLANNED_DISTANCE} is only used when GPS has no distance on
   * that lap. Timed rest/recovery still prefers {@code PLANNED_TIME} when set.
   */
  void recomputeLap(SQLiteDatabase db, long activityId, long lap, boolean force) {
    Location savedLastLocation = _lastLocation;
    boolean savedIsActive = _isActive;
    _lastLocation = null;
    _isActive = false;

    int intensity = DB.INTENSITY.ACTIVE;
    double originalDistance = 0;
    long originalTime = 0;
    boolean haveOriginalDistance = false;
    boolean haveOriginalTime = false;
    long plannedTime = 0;
    double plannedDistance = 0;
    boolean havePlannedTime = false;
    boolean havePlannedDistance = false;
    String[] lapCols =
        new String[] {
          DB.LAP.INTENSITY,
          DB.LAP.DISTANCE,
          DB.LAP.TIME,
          DB.LAP.PLANNED_TIME,
          DB.LAP.PLANNED_DISTANCE
        };
    try (Cursor lapCursor =
        db.query(
            DB.LAP.TABLE,
            lapCols,
            DB.LAP.ACTIVITY + " = " + activityId + " and " + DB.LAP.LAP + " = " + lap,
            null,
            null,
            null,
            null)) {
      if (lapCursor.moveToFirst()) {
        if (!lapCursor.isNull(0)) intensity = lapCursor.getInt(0);
        if (!lapCursor.isNull(1)) {
          originalDistance = lapCursor.getDouble(1);
          haveOriginalDistance = true;
        }
        if (!lapCursor.isNull(2)) {
          originalTime = lapCursor.getLong(2);
          haveOriginalTime = true;
        }
        if (!lapCursor.isNull(3)) {
          plannedTime = lapCursor.getLong(3);
          havePlannedTime = plannedTime > 0;
        }
        if (!lapCursor.isNull(4)) {
          plannedDistance = lapCursor.getDouble(4);
          havePlannedDistance = plannedDistance > 0;
        }
      }
    }

    // First, try to derive sum_time / sum_distance from the live tracker values that were
    // persisted on each LOCATION row: ELAPSED (monotonic active milliseconds since workout
    // start, paused-aware) and DISTANCE (cumulative active distance). These are exactly what
    // the live tracker measured, so when present they make recompute near-lossless.
    //
    // We walk active sub-segments (between START/RESUME and PAUSE/END) and sum the deltas of
    // ELAPSED/DISTANCE within each segment. This handles intra-lap pauses correctly: time and
    // distance only advance while STARTED, so consecutive deltas across a pause are zero.
    long sum_time_elapsed_ms = 0;
    double sum_distance_elapsed = 0;
    boolean haveElapsedSamples = false;
    boolean haveDistanceSamples = false;
    long sum_time = 0;
    long sum_hr = 0;
    double sum_distance = 0;
    int count = 0;
    int max_hr = 0;
    final String[] cols =
        new String[] {
          DB.LOCATION.TIME,
          DB.LOCATION.LATITUDE,
          DB.LOCATION.LONGITUDE,
          DB.LOCATION.TYPE,
          DB.LOCATION.HR,
          DB.LOCATION.ELAPSED,
          DB.LOCATION.DISTANCE,
          "_id"
        };

    // Seed the running cumulative ELAPSED / DISTANCE from the previous lap's last row, when
    // available, so the inter-lap edge (typically 0.5-2 s and 3-5 m of GPS movement that
    // happened between the last sample of the previous lap and the first sample of this one)
    // is credited to *this* lap. Without this seeding, recompute would systematically lose
    // ~1 s and ~3 m per lap compared to the live tracker, because the live tracker accumulates
    // mElapsedDistance/Time continuously across lap boundaries (lap distance is computed as
    // mElapsedDistance(now) - lapStartDistance, where lapStartDistance was sampled at lap
    // start), whereas the recompute cursor only sees this lap's rows.
    //
    // Only seed from a TYPE_GPS row in the previous lap: if the previous row was a PAUSE/END
    // (workout was stopped), the workout was inactive across that boundary and there is no
    // real edge to credit.
    Long seedElapsed = null;
    Double seedDistanceCum = null;
    try (Cursor pc =
        db.query(
            DB.LOCATION.TABLE,
            new String[] {DB.LOCATION.ELAPSED, DB.LOCATION.DISTANCE, DB.LOCATION.TYPE},
            DB.LOCATION.ACTIVITY + " = " + activityId + " and " + DB.LOCATION.LAP + " < " + lap,
            null,
            null,
            null,
            "_id desc",
            "1")) {
      if (pc.moveToFirst()) {
        int prevType = pc.getInt(2);
        boolean prevWasActiveRow =
            prevType == DB.LOCATION.TYPE_GPS
                || prevType == DB.LOCATION.TYPE_START
                || prevType == DB.LOCATION.TYPE_RESUME
                || prevType == DB.LOCATION.TYPE_AUTO_RESUME;
        if (prevWasActiveRow) {
          if (!pc.isNull(0)) seedElapsed = pc.getLong(0);
          if (!pc.isNull(1)) seedDistanceCum = pc.getDouble(1);
        }
      }
    }

    try (Cursor c =
        db.query(
            DB.LOCATION.TABLE,
            cols,
            DB.LOCATION.ACTIVITY + " = " + activityId + " and " + DB.LOCATION.LAP + " = " + lap,
            null,
            null,
            null,
            "_id",
            null)) {
      Long prevElapsed = seedElapsed;
      Double prevDistanceCum = seedDistanceCum;
      if (c.moveToFirst()) {
        do {
          Location l = new Location("Dill poh");
          l.setTime(c.getLong(0));
          l.setLatitude(c.getDouble(1));
          l.setLongitude(c.getDouble(2));
          l.setProvider("" + c.getLong(3));

          int type = c.getInt(3);
          Long elapsedCum = c.isNull(5) ? null : c.getLong(5);
          Double distanceCum = c.isNull(6) ? null : c.getDouble(6);

          // ELAPSED / DISTANCE accumulation: deltas between consecutive rows in the same
          // active segment. Reset on START/RESUME (lap may also start with a GPS row if the
          // logger didn't emit an explicit START). Auto-pause/resume behave the same as the
          // manual variants — the segment boundaries are identical.
          boolean isResumeOrStart =
              type == DB.LOCATION.TYPE_START
                  || type == DB.LOCATION.TYPE_RESUME
                  || type == DB.LOCATION.TYPE_AUTO_RESUME;
          boolean isPauseOrEnd =
              type == DB.LOCATION.TYPE_PAUSE
                  || type == DB.LOCATION.TYPE_AUTO_PAUSE
                  || type == DB.LOCATION.TYPE_END;
          boolean segmentBoundary = isResumeOrStart || isPauseOrEnd;
          if (segmentBoundary) {
            // Include the boundary row itself in the previous segment's delta when it's a
            // PAUSE/END (it carries the final ELAPSED/DISTANCE just before stopping).
            if (isPauseOrEnd && prevElapsed != null && elapsedCum != null) {
              long dt = elapsedCum - prevElapsed;
              if (dt > 0) {
                sum_time_elapsed_ms += dt;
                haveElapsedSamples = true;
              }
            }
            if (isPauseOrEnd && prevDistanceCum != null && distanceCum != null) {
              double dd = distanceCum - prevDistanceCum;
              if (dd > 0) {
                sum_distance_elapsed += dd;
                haveDistanceSamples = true;
              }
            }
            // For START/RESUME, just seed the segment without crediting any delta.
            // For PAUSE/END, clear the segment so the next active row doesn't add the gap.
            prevElapsed = isResumeOrStart ? elapsedCum : null;
            prevDistanceCum = isResumeOrStart ? distanceCum : null;
          } else if (type == DB.LOCATION.TYPE_GPS) {
            if (prevElapsed != null && elapsedCum != null) {
              long dt = elapsedCum - prevElapsed;
              if (dt > 0) {
                sum_time_elapsed_ms += dt;
                haveElapsedSamples = true;
              }
            }
            if (prevDistanceCum != null && distanceCum != null) {
              double dd = distanceCum - prevDistanceCum;
              if (dd > 0) {
                sum_distance_elapsed += dd;
                haveDistanceSamples = true;
              }
            }
            if (elapsedCum != null) prevElapsed = elapsedCum;
            if (distanceCum != null) prevDistanceCum = distanceCum;
          }

          switch (type) {
            case DB.LOCATION.TYPE_START:
            case DB.LOCATION.TYPE_RESUME:
            case DB.LOCATION.TYPE_AUTO_RESUME:
              _lastLocation = l;
              _isActive = true;
              break;
            case DB.LOCATION.TYPE_END:
            case DB.LOCATION.TYPE_PAUSE:
            case DB.LOCATION.TYPE_AUTO_PAUSE:
            case DB.LOCATION.TYPE_GPS:
              if (_lastLocation == null) {
                _lastLocation = l;
                _isActive = true;
                break;
              }

              if (_isActive) {
                double diffDist = l.distanceTo(_lastLocation);
                sum_distance += diffDist;
                _totalDistance += diffDist;

                long diffTime = l.getTime() - _lastLocation.getTime();
                sum_time += diffTime;
                _totalTime += diffTime;

                int hr = c.getInt(4);
                sum_hr += hr;
                max_hr = Math.max(max_hr, hr);
                _totalMaxHr = Math.max(_totalMaxHr, hr);
                count++;
                _totalCount++;
                _totalSumHr += hr;
              }
              _lastLocation = l;
              if (isPauseOrEnd) {
                _isActive = false;
              }
              break;
          }
        } while (c.moveToNext());
      }
    }

    // Prefer live-tracker derived values when this lap had ELAPSED/DISTANCE samples.
    // Fallback chain: ELAPSED/DISTANCE deltas -> lat/lon + wall-clock sums (legacy rows).
    if (haveElapsedSamples) {
      sum_time = sum_time_elapsed_ms;
    }
    if (haveDistanceSamples) {
      sum_distance = sum_distance_elapsed;
    }

    // Pick the values to write for this lap.
    //
    // Default (non-force) mode: workout-saved values are authoritative; only fill in when
    // missing (NULL/0) and only for ACTIVE laps.
    //
    // Force mode (user-initiated "rebuild from raw data"):
    //   ACTIVE laps   -> trust GPS sums (a 1.00 km work interval becomes the actual ~0.99 km).
    //                    Only fall back to the saved value if GPS yielded nothing.
    //   non-ACTIVE   -> distance: prefer GPS when the lap has any measured distance (correct for
    //                    autolap splits where PLANNED_DISTANCE is often the *whole* step, not
    //                    each 1 km slice). If GPS distance is zero, use PLANNED_DISTANCE, then
    //                    saved. Time: timed REST/RECOVERY prefer PLANNED_TIME when set; warm-up /
    //                    cool-down prefer measured active time when present so split laps keep
    //                    sensible durations.
    Long writeTime = null;
    Double writeDistance = null;
    if (force) {
      double gpsSeconds = sum_time / 1000.0d;
      if (intensity == DB.INTENSITY.ACTIVE) {
        if (sum_distance > 0) writeDistance = sum_distance;
        else if (haveOriginalDistance && originalDistance > 0) writeDistance = originalDistance;
        if (sum_time > 0) writeTime = Math.round(gpsSeconds);
        else if (haveOriginalTime && originalTime > 0) writeTime = originalTime;
      } else {
        if (sum_distance > 0) writeDistance = sum_distance;
        else if (havePlannedDistance) writeDistance = plannedDistance;
        else if (haveOriginalDistance && originalDistance > 0) writeDistance = originalDistance;
        if (intensity == DB.INTENSITY.RESTING || intensity == DB.INTENSITY.RECOVERY) {
          if (havePlannedTime) writeTime = plannedTime;
          else if (sum_time > 0) writeTime = Math.round(gpsSeconds);
          else if (haveOriginalTime && originalTime > 0) writeTime = originalTime;
        } else {
          if (sum_time > 0) writeTime = Math.round(gpsSeconds);
          else if (havePlannedTime) writeTime = plannedTime;
          else if (haveOriginalTime && originalTime > 0) writeTime = originalTime;
        }
      }
    } else {
      boolean preserveDistance =
          intensity != DB.INTENSITY.ACTIVE
              || (haveOriginalDistance && originalDistance > 0);
      boolean preserveTime =
          intensity != DB.INTENSITY.ACTIVE || (haveOriginalTime && originalTime > 0);
      if (!preserveDistance) writeDistance = sum_distance;
      if (!preserveTime) writeTime = Math.round(sum_time / 1000.0d);
    }

    ContentValues tmp = new ContentValues();
    if (writeDistance != null) {
      tmp.put(DB.LAP.DISTANCE, writeDistance);
    }
    if (writeTime != null) {
      tmp.put(DB.LAP.TIME, writeTime);
    }
    if (sum_hr > 0) {
      long hr = Math.round(sum_hr / (double) count);
      tmp.put(DB.LAP.AVG_HR, hr);
      tmp.put(DB.LAP.MAX_HR, max_hr);
    }
    if (tmp.size() > 0) {
      db.update(
          DB.LAP.TABLE,
          tmp,
          DB.LAP.ACTIVITY + " = " + activityId + " and " + DB.LAP.LAP + " = " + lap,
          null);
    }

    _lastLocation = savedLastLocation;
    _isActive = savedIsActive;
  }

  /**
   * Highest BPM recorded on any location row for this activity (per GPS/tracker sample). This is
   * the true run-wide peak when HR was logged to {@code location.hr}; lap {@code avg_hr} is only an
   * average over the lap.
   */
  private static int queryMaxHrFromLocations(SQLiteDatabase db, long activityId) {
    String sql =
        "SELECT MAX("
            + DB.LOCATION.HR
            + ") FROM "
            + DB.LOCATION.TABLE
            + " WHERE "
            + DB.LOCATION.ACTIVITY
            + " = ? AND "
            + DB.LOCATION.HR
            + " > 0";
    try (Cursor c = db.rawQuery(sql, new String[] {String.valueOf(activityId)})) {
      if (c.moveToFirst() && !c.isNull(0)) {
        return c.getInt(0);
      }
    }
    return 0;
  }

  /**
   * Fix activity rows where {@code avg_hr} holds peak BPM and {@code max_hr} holds average BPM
   * (legacy swapped-column bug). Safe to run repeatedly: only updates rows with {@code avg_hr >
   * max_hr}.
   */
  public static void repairSwappedActivityHeartRates(SQLiteDatabase db) {
    ActivityHrRepair.repairSwappedActivityHeartRates(db);
  }

  /** recompute an activity summary based on laps */
  void recomputeSummary(SQLiteDatabase db, long activityId) {
    // Recalculate total distance and time from all lap distances/times to ensure accuracy
    // (since we preserve original autolap distances, the sum of laps is more accurate)
    double totalDistanceFromLaps = 0;
    long totalTimeFromLaps = 0;
    long weightedHrSum = 0;
    long timeForHrWeighted = 0;
    int activityPeakFromLaps = 0;
    String[] lapCols =
        new String[] {DB.LAP.DISTANCE, DB.LAP.TIME, DB.LAP.AVG_HR, DB.LAP.MAX_HR};
    Cursor lapCursor = db.query(
        DB.LAP.TABLE,
        lapCols,
        DB.LAP.ACTIVITY + " = " + activityId,
        null, null, null, null);
    int lapCount = 0;
    if (lapCursor.moveToFirst()) {
      do {
        lapCount++;
        long lapTime = 0;
        if (!lapCursor.isNull(0)) {
          double lapDist = lapCursor.getDouble(0);
          totalDistanceFromLaps += lapDist;
          Log.d("ActivityCleaner", "Lap " + lapCount + ": distance = " + lapDist + "m");
        }
        if (!lapCursor.isNull(1)) {
          lapTime = lapCursor.getLong(1);
          totalTimeFromLaps += lapTime;
          Log.d("ActivityCleaner", "Lap " + lapCount + ": time = " + lapTime + "s");
        } else {
          Log.w("ActivityCleaner", "Lap " + lapCount + ": time is NULL");
        }
        int avgHr = lapCursor.isNull(2) ? 0 : lapCursor.getInt(2);
        int maxHrLap = lapCursor.isNull(3) ? 0 : lapCursor.getInt(3);
        if (avgHr > 0 && lapTime > 0) {
          weightedHrSum += (long) avgHr * lapTime;
          timeForHrWeighted += lapTime;
        }
        int lapPeak = Math.max(maxHrLap, avgHr);
        if (lapPeak > 0) {
          activityPeakFromLaps = Math.max(activityPeakFromLaps, lapPeak);
        }
      } while (lapCursor.moveToNext());
    }
    lapCursor.close();

    Log.i("ActivityCleaner", "Activity " + activityId + ": totalDistanceFromLaps = " + totalDistanceFromLaps + "m, totalTimeFromLaps = " + totalTimeFromLaps + "s, _totalTime = " + _totalTime + "ms");

    ContentValues tmp = new ContentValues();
    // Average HR: time-weighted from lap averages (works even when many autolaps skip GPS
    // recomputation in recomputeLap()).
    if (timeForHrWeighted > 0) {
      long hr = Math.round(weightedHrSum / (double) timeForHrWeighted);
      tmp.put(DB.ACTIVITY.AVG_HR, hr);
    }
    // Peak HR: prefer MAX(location.hr) — each row is an actual sample — not lap averages.
    int peakFromLocations = queryMaxHrFromLocations(db, activityId);
    int activityPeak = Math.max(peakFromLocations, activityPeakFromLaps);
    if (activityPeak > 0) {
      tmp.put(DB.ACTIVITY.MAX_HR, activityPeak);
    }
    // Use sum of lap distances (which preserves original autolap distances)
    tmp.put(DB.ACTIVITY.DISTANCE, totalDistanceFromLaps > 0 ? totalDistanceFromLaps : _totalDistance);
    // Use sum of lap times (which includes all laps, even those skipped during recomputation)
    long finalTime = totalTimeFromLaps > 0 ? totalTimeFromLaps : Math.round(_totalTime / 1000.0d);
    tmp.put(DB.ACTIVITY.TIME, finalTime); // also used as a flag for conditionalRecompute
    Log.i("ActivityCleaner", "Activity " + activityId + ": Setting TIME = " + finalTime + "s");

    db.update(DB.ACTIVITY.TABLE, tmp, "_id = " + activityId, null);
  }

  /**
   * Fix corrupted lap distances by rounding laps in 800-1200m range to 1000m.
   * This fixes laps that were incorrectly recalculated from GPS points.
   */
  public static void fixCorruptedLapDistances(SQLiteDatabase db) {
    try {
      String[] cols = new String[] {DB.PRIMARY_KEY, DB.LAP.ACTIVITY, DB.LAP.LAP, DB.LAP.DISTANCE};
      Cursor c = db.query(DB.LAP.TABLE, cols, null, null, null, null, null);
      int fixedCount = 0;
      
      if (c.moveToFirst()) {
        do {
          if (!c.isNull(3)) { // DISTANCE column
            double distance = c.getDouble(3);
            // Round laps in 800-1200m range to 1000m (these are likely corrupted autolap distances)
            if (distance >= 800 && distance <= 1200 && Math.abs(distance - 1000) > 10) {
              long lapId = c.getLong(0);
              ContentValues tmp = new ContentValues();
              tmp.put(DB.LAP.DISTANCE, 1000.0);
              db.update(DB.LAP.TABLE, tmp, "_id = ?", new String[] {Long.toString(lapId)});
              fixedCount++;
            }
          }
        } while (c.moveToNext());
      }
      c.close();
      
      if (fixedCount > 0) {
        Log.i("ActivityCleaner", "Fixed " + fixedCount + " corrupted lap distances to 1000m");
        
        // Recalculate activity total distances from fixed lap distances
        String[] activityCols = new String[] {DB.PRIMARY_KEY};
        Cursor activityCursor = db.query(DB.ACTIVITY.TABLE, activityCols, null, null, null, null, null);
        if (activityCursor.moveToFirst()) {
          do {
            long activityId = activityCursor.getLong(0);
            // Recalculate total distance from lap distances
            String[] lapDistCols = new String[] {DB.LAP.DISTANCE};
            Cursor lapDistCursor = db.query(
                DB.LAP.TABLE,
                lapDistCols,
                DB.LAP.ACTIVITY + " = " + activityId,
                null, null, null, null);
            double totalDistance = 0;
            if (lapDistCursor.moveToFirst()) {
              do {
                if (!lapDistCursor.isNull(0)) {
                  totalDistance += lapDistCursor.getDouble(0);
                }
              } while (lapDistCursor.moveToNext());
            }
            lapDistCursor.close();
            
            if (totalDistance > 0) {
              ContentValues tmp = new ContentValues();
              tmp.put(DB.ACTIVITY.DISTANCE, totalDistance);
              db.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", new String[] {Long.toString(activityId)});
            }
          } while (activityCursor.moveToNext());
        }
        activityCursor.close();
      }
    } catch (Exception e) {
      Log.e("ActivityCleaner", "Error fixing corrupted lap distances: " + e.getMessage(), e);
    }
  }


  public void conditionalRecompute(SQLiteDatabase db) {
    try {
      long id =
          db.compileStatement("SELECT MAX(_id) FROM " + DB.ACTIVITY.TABLE).simpleQueryForLong();

      String[] cols = new String[] {DB.ACTIVITY.TIME};
      try (Cursor c = db.query(DB.ACTIVITY.TABLE, cols, "_id = " + id, null, null, null, null)) {
        if (c.moveToFirst()) {
          if (c.isNull(0) || c.getLong(0) == 0) {
            Log.i(
                "ActivityCleaner",
                "Activity "
                    + id
                    + " has TIME = "
                    + (c.isNull(0) ? "NULL" : c.getLong(0))
                    + ", recomputing...");
            recompute(db, id);
          } else if (lastLapNeedsRecompute(db, id)) {
            Log.i("ActivityCleaner", "Activity " + id + " last lap incomplete, recomputing...");
            recompute(db, id);
          }
        }
      }
    } catch (IllegalStateException e) {
      Log.e(getClass().getName(), "conditionalRecompute: " + e.getMessage());
    }
  }

  /**
   * Returns true only when the last lap clearly looks unfinished (DISTANCE NULL/0 AND TIME NULL/0).
   * The previous heuristic also treated any 800-1200 m last lap as suspect, which forced a
   * recompute on every interval workout opening and corrupted rest laps.
   */
  private static boolean lastLapNeedsRecompute(SQLiteDatabase db, long activityId) {
    long maxLap;
    try {
      maxLap =
          db.compileStatement(
                  "SELECT MAX("
                      + DB.LAP.LAP
                      + ") FROM "
                      + DB.LAP.TABLE
                      + " WHERE "
                      + DB.LAP.ACTIVITY
                      + " = "
                      + activityId)
              .simpleQueryForLong();
    } catch (IllegalStateException e) {
      return false;
    }
    if (maxLap < 0) return false;
    String[] lapCols = new String[] {DB.LAP.DISTANCE, DB.LAP.TIME};
    try (Cursor lapCursor =
        db.query(
            DB.LAP.TABLE,
            lapCols,
            DB.LAP.ACTIVITY + " = " + activityId + " AND " + DB.LAP.LAP + " = " + maxLap,
            null,
            null,
            null,
            null)) {
      if (!lapCursor.moveToFirst()) return false;
      boolean distMissing = lapCursor.isNull(0) || lapCursor.getDouble(0) <= 0;
      boolean timeMissing = lapCursor.isNull(1) || lapCursor.getLong(1) <= 0;
      return distMissing && timeMissing;
    }
  }

  public void recompute(SQLiteDatabase db, long activityId) {
    recompute(db, activityId, false);
  }

  /**
   * Rebuild lap aggregates from raw GPS rows.
   *
   * @param force when true, GPS-derived sums overwrite ACTIVE laps; non-ACTIVE laps use GPS
   *     distance/time when samples exist (so autolapped phases stay per-lap correct), fall back to
   *     planned values when a lap has no GPS distance (e.g. paused rest). After a force pass,
   *     tiny straggler laps are merged into the previous lap when they share the same intensity.
   */
  public void recompute(SQLiteDatabase db, long activityId, boolean force) {
    _totalTime = 0;
    _totalDistance = 0;
    _totalSumHr = 0;
    _totalCount = 0;
    _totalMaxHr = 0;
    _lastLocation = null;
    _isActive = false;

    recomputeLaps(db, activityId, force);
    if (force) {
      mergeDustLapsAfterForceRecompute(db, activityId);
    }
    recomputeSummary(db, activityId);
    invalidateAggregateCaches(db);
  }

  /**
   * After a force-recompute, fold tiny "straggler" laps (a few metres and seconds of GPS residue
   * between autolap boundaries) into the previous lap so interval counts match the workout. Only
   * merges when the two laps share the same intensity; re-assigns LOCATION rows and decrements
   * higher lap indices. Then re-runs {@link #recomputeLap} on the receiving lap from raw samples.
   */
  private static void mergeDustLapsAfterForceRecompute(SQLiteDatabase db, long activityId) {
    final double maxDustDistanceM = 100.0;
    final long maxDustTimeSec = 45L;
    final int lapOffset = 1_000_000;
    ActivityCleaner cleaner = new ActivityCleaner();
    boolean mergedOne;
    do {
      mergedOne = false;
      List<LapRow> laps = loadLapsOrdered(db, activityId);
      for (int i = 1; i < laps.size(); i++) {
        LapRow dust = laps.get(i);
        LapRow prev = laps.get(i - 1);
        if (dust.type != prev.type) {
          continue;
        }
        if (!isDustLap(dust, maxDustDistanceM, maxDustTimeSec)) {
          continue;
        }
        db.beginTransaction();
        try {
          double newDist = prev.distance + dust.distance;
          long newTime = prev.time + dust.time;
          ContentValues merged = new ContentValues();
          merged.put(DB.LAP.DISTANCE, newDist);
          merged.put(DB.LAP.TIME, newTime);
          db.update(
              DB.LAP.TABLE,
              merged,
              DB.PRIMARY_KEY + " = ?",
              new String[] {String.valueOf(prev.rowId)});

          ContentValues loc = new ContentValues();
          loc.put(DB.LOCATION.LAP, prev.lapIndex);
          db.update(
              DB.LOCATION.TABLE,
              loc,
              DB.LOCATION.ACTIVITY + " = ? AND " + DB.LOCATION.LAP + " = ?",
              new String[] {String.valueOf(activityId), String.valueOf(dust.lapIndex)});

          db.delete(DB.LAP.TABLE, DB.PRIMARY_KEY + " = ?", new String[] {String.valueOf(dust.rowId)});

          LapIndexRenumberer.renumberLapsAfterDeletingIndex(db, activityId, dust.lapIndex, lapOffset);

          db.setTransactionSuccessful();
        } finally {
          db.endTransaction();
        }

        cleaner.recomputeLap(db, activityId, prev.lapIndex, true);
        mergedOne = true;
        break;
      }
    } while (mergedOne);
  }

  private static boolean isDustLap(LapRow lap, double maxDistM, long maxTimeSec) {
    if (lap.distance < 0 || lap.time < 0) {
      return false;
    }
    if (lap.distance == 0 && lap.time == 0) {
      return false;
    }
    return lap.distance < maxDistM && lap.time < maxTimeSec;
  }

  private static List<LapRow> loadLapsOrdered(SQLiteDatabase db, long activityId) {
    List<LapRow> out = new ArrayList<>();
    String[] cols =
        new String[] {
          DB.PRIMARY_KEY, DB.LAP.LAP, DB.LAP.INTENSITY, DB.LAP.DISTANCE, DB.LAP.TIME,
        };
    try (Cursor c =
        db.query(
            DB.LAP.TABLE,
            cols,
            DB.LAP.ACTIVITY + " = ?",
            new String[] {String.valueOf(activityId)},
            null,
            null,
            DB.LAP.LAP + " ASC",
            null)) {
      while (c.moveToNext()) {
        LapRow r = new LapRow();
        r.rowId = c.getLong(0);
        r.lapIndex = c.getInt(1);
        r.type = c.getInt(2);
        r.distance = c.isNull(3) ? 0.0 : c.getDouble(3);
        r.time = c.isNull(4) ? 0L : c.getLong(4);
        out.add(r);
      }
    }
    return out;
  }

  private static final class LapRow {
    long rowId;
    int lapIndex;
    int type;
    double distance;
    long time;
  }

  /**
   * Invalidate every cached aggregate table that depends on activity / lap aggregates.
   *
   * <p>The various staleness checks ({@code BestTimesCalculator.isDataStale},
   * {@code StatisticsCalculator.isDataStale}, ...) only fire when a <em>new</em> activity ID
   * appears — they cannot detect that an existing activity's lap totals were rewritten by a
   * manual recompute. After such a rewrite the cached best times / statistics / HR zones /
   * yearly cumulative / monthly comparison rows are stale, so wipe their tracking rows here so
   * the next view that calls {@code isDataStale} will trigger a fresh recompute.
   */
  private static void invalidateAggregateCaches(SQLiteDatabase db) {
    try {
      // best_times + statistics share computation_tracking; clearing it forces both to
      // recompute on the next MainLayout / BestTimesFragment open.
      db.delete(DB.COMPUTATION_TRACKING.TABLE, null, null);
      // hr_zone_stats / yearly_cumulative / monthly_comparison track their own last_computed
      // timestamps. Deleting all rows forces their isDataStale() check to return true.
      db.delete(DB.HR_ZONE_STATS.TABLE, null, null);
      db.delete(DB.YEARLY_CUMULATIVE.TABLE, null, null);
      db.delete(DB.MONTHLY_COMPARISON.TABLE, null, null);
    } catch (Exception e) {
      Log.w("ActivityCleaner", "Failed to invalidate aggregate caches: " + e.getMessage());
    }
  }

  /**
   * Adjust the last lap of the latest activity by adding distance and adjusting time proportionally.
   * This is useful when GPS tracking stopped but the user continued running.
   * Only adjusts if the last lap appears incomplete (distance < 1000m) and hasn't been adjusted yet.
   * 
   * @param db Database connection
   * @param additionalDistanceMeters Additional distance to add to the last lap (in meters)
   * @return true if adjustment was made, false otherwise
   */
  public static boolean adjustLastLap(SQLiteDatabase db, double additionalDistanceMeters) {
    try {
      // Get the latest activity ID
      long activityId = db.compileStatement("SELECT MAX(_id) FROM " + DB.ACTIVITY.TABLE).simpleQueryForLong();
      
      // Get the last lap for this activity
      String[] lapCols = new String[] {DB.LAP.LAP, DB.LAP.DISTANCE, DB.LAP.TIME};
      long maxLap = db.compileStatement("SELECT MAX(" + DB.LAP.LAP + ") FROM " + DB.LAP.TABLE + 
          " WHERE " + DB.LAP.ACTIVITY + " = " + activityId).simpleQueryForLong();
      
      Cursor lapCursor = db.query(
          DB.LAP.TABLE,
          lapCols,
          DB.LAP.ACTIVITY + " = " + activityId + " AND " + DB.LAP.LAP + " = " + maxLap,
          null, null, null, null);
      
      if (lapCursor.moveToFirst()) {
        double currentDistance = lapCursor.isNull(1) ? 0 : lapCursor.getDouble(1);
        long currentTime = lapCursor.isNull(2) ? 0 : lapCursor.getLong(2);
        
        // Only adjust if the lap appears incomplete (distance < 1000m) to avoid repeated adjustments
        // Also check if it's close to the expected adjusted value (within 10m) - if so, already adjusted
        double expectedAdjusted = currentDistance + additionalDistanceMeters;
        if (currentDistance >= 1000 || Math.abs(currentDistance - expectedAdjusted) < 10) {
          Log.i("ActivityCleaner", "Last lap already complete or adjusted (" + currentDistance + "m), skipping adjustment");
          lapCursor.close();
          return false;
        }
        
        // Calculate new distance
        double newDistance = currentDistance + additionalDistanceMeters;
        
        // Calculate new time based on current pace (maintain same pace)
        long newTime = currentTime;
        if (currentDistance > 0 && currentTime > 0) {
          // pace = time / distance, so newTime = newDistance * (currentTime / currentDistance)
          newTime = Math.round(newDistance * (currentTime / currentDistance));
        } else if (currentTime > 0) {
          // If distance was 0 but time exists, estimate based on average pace (e.g., 5 min/km = 300s/km)
          // Use a conservative estimate of 5:00/km pace
          newTime = currentTime + Math.round(additionalDistanceMeters * 0.3); // 300s per 1000m = 0.3s per meter
        } else if (currentDistance > 0) {
          // If time was 0 but distance exists, estimate time based on average pace
          // Use 5:00/km = 300s/km = 0.3s/m
          newTime = Math.round(newDistance * 0.3);
        } else {
          // Both are 0, estimate based on average pace
          newTime = Math.round(additionalDistanceMeters * 0.3);
        }
        
        Log.i("ActivityCleaner", "Adjusting last lap (lap " + maxLap + ") of activity " + activityId + 
            ": distance " + currentDistance + "m -> " + newDistance + "m, time " + currentTime + "s -> " + newTime + "s");
        
        // Update the lap
        ContentValues lapUpdate = new ContentValues();
        lapUpdate.put(DB.LAP.DISTANCE, newDistance);
        lapUpdate.put(DB.LAP.TIME, newTime);
        db.update(
            DB.LAP.TABLE,
            lapUpdate,
            DB.LAP.ACTIVITY + " = " + activityId + " AND " + DB.LAP.LAP + " = " + maxLap,
            null);
        
        // Recompute activity totals from all laps
        ActivityCleaner cleaner = new ActivityCleaner();
        cleaner.recomputeSummary(db, activityId);
        
        Log.i("ActivityCleaner", "Last lap adjusted successfully");
        lapCursor.close();
        return true;
      } else {
        Log.w("ActivityCleaner", "No last lap found for activity " + activityId);
        lapCursor.close();
        return false;
      }
    } catch (Exception e) {
      Log.e("ActivityCleaner", "Error adjusting last lap: " + e.getMessage(), e);
      return false;
    }
  }

  public static void trim(SQLiteDatabase db, long activityId) {
    final String[] cols = new String[] {DB.LAP.LAP};

    ArrayList<Long> laps = new ArrayList<>();
    Cursor c =
        db.query(
            DB.LOCATION.LAP,
            cols,
            DB.LAP.ACTIVITY + " = " + activityId,
            null,
            null,
            null,
            "_id",
            null);
    if (c.moveToFirst()) {
      do {
        laps.add(c.getLong(0));
      } while (c.moveToNext());
    }
    c.close();

    for (long lap : laps) {
      int res = trimLap(db, activityId, lap);
      Log.e("ActivityCleaner", "lap " + lap + " removed " + res + " locations");
    }
  }

  private static final float MIN_DISTANCE = 2f;

  private static int trimLap(SQLiteDatabase db, long activityId, long lap) {
    int cnt = 0;
    final String[] cols =
        new String[] {
          DB.LOCATION.TIME, DB.LOCATION.LATITUDE, DB.LOCATION.LONGITUDE, DB.LOCATION.TYPE, "_id"
        };

    Cursor c =
        db.query(
            DB.LOCATION.TABLE,
            cols,
            DB.LOCATION.ACTIVITY + " = " + activityId + " and " + DB.LOCATION.LAP + " = " + lap,
            null,
            null,
            null,
            "_id",
            null);
    if (c.moveToFirst()) {
      Location[] p = {null, null};
      do {
        Location l = new Location("Dill poh");
        l.setTime(c.getLong(0));
        l.setLatitude(c.getDouble(1));
        l.setLongitude(c.getDouble(2));
        l.setProvider("" + c.getLong(3));

        int type = c.getInt(3);
        switch (type) {
          case DB.LOCATION.TYPE_START:
          case DB.LOCATION.TYPE_RESUME:
          case DB.LOCATION.TYPE_AUTO_RESUME:
            p[0] = l;
            p[1] = null;
            break;
          case DB.LOCATION.TYPE_END:
          case DB.LOCATION.TYPE_PAUSE:
          case DB.LOCATION.TYPE_AUTO_PAUSE:
          case DB.LOCATION.TYPE_GPS:
            if (p[0] == null) {
              p[0] = l;
              p[1] = null;
              break;
            } else if (p[1] == null) {
              p[1] = l;
            } else {
              float d1 = p[0].distanceTo(p[1]);
              float d2 = p[0].distanceTo(l);
              if (Math.abs(d1 - d2) <= MIN_DISTANCE) {
                // p[1] is redundant...prune it
                p[1] = l;
                cnt++;
              } else {
                p[0] = p[1];
                p[1] = null;
              }
            }
            break;
        }
      } while (c.moveToNext());
    }
    c.close();
    return cnt;
  }

  /**
   * Deletes locations with given IDs from the database.
   *
   * @param db Database.
   * @param ids ID to delete.
   */
  public static void deleteLocations(SQLiteDatabase db, ArrayList<String> ids) {
    String strIDs = TextUtils.join(",", ids);
    db.execSQL(
        "delete from "
            + DB.LOCATION.TABLE
            + " where _id in ("
            + strIDs
            + ")"
            + " and "
            + DB.LOCATION.TYPE
            + " = "
            + DB.LOCATION.TYPE_GPS);
  }
}
