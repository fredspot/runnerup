/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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

package org.runnerup.common.util;

import org.runnerup.common.BuildConfig;

public interface Constants {

  String LOG = "org.runnerup";

  /**
   * SQLite table and column names. Columns added only in {@link org.runnerup.data.DBHelper} schema
   * upgrades are documented in migration version blocks there and in {@link
   * org.runnerup.data.SchemaMigrations}.
   */
  interface DB {

    String PRIMARY_KEY = "_id";

    // DBVERSION update
    // interface DBINFO {
    //    String TABLE = "dbinfo";
    //    String ACCOUNT_VERSION = "account_version";
    // }

    interface ACTIVITY {
      String TABLE = "activity";
      String START_TIME = "start_time";
      String DISTANCE = "distance";
      String TIME = "time";
      String NAME = "name";
      String COMMENT = "comment";
      String SPORT = "type";
      String AVG_HR = "avg_hr";
      String MAX_HR = "max_hr";
      String AVG_CADENCE = "avg_cadence";
      String META_DATA = "meta_data";
      String DELETED = "deleted";
      String NULLCOLUMNHACK = "nullColumnHack";

      int SPORT_RUNNING = 0;
      int SPORT_BIKING = 1;
      int SPORT_OTHER = 2; // unknown
      int SPORT_ORIENTEERING = 3;
      int SPORT_WALKING = 4;
      int SPORT_TREADMILL = 5;
      int SPORT_MAX = 5;

      String WITH_BAROMETER = "<WithBarometer/>";
    }

    interface LOCATION {
      String TABLE = "location";
      String ACTIVITY = "activity_id";
      String LAP = "lap";
      String TYPE = "type";
      String TIME = "time"; // in milliseconds since epoch
      String ELAPSED = "elapsed";
      String DISTANCE = "distance";
      String LATITUDE = "latitude";
      String LONGITUDE = "longitude";
      String ACCURANCY = "accurancy";
      String ALTITUDE = "altitude";
      String GPS_ALTITUDE = "gps_altitude";
      String SPEED = "speed";
      String BEARING = "bearing";
      String SATELLITES = "satellites";
      String HR = "hr";
      String CADENCE = "cadence";
      String TEMPERATURE = "temperature";
      String PRESSURE = "pressure";
      // Phase 3: foreign key into the STEP table; identifies the workout step this sample
      // belongs to. Nullable for samples logged before this column existed and for sessions
      // without a structured workout.
      String STEP = "step";
      // Phase 5: source of the HR and cadence values on this row, from {@link SENSOR_SOURCE}.
      // Nullable; legacy rows leave these NULL. Future code can use them to decide which
      // source to trust when multiple sensors are paired or one drops out mid-run.
      String HR_SOURCE = "hr_source";
      String CADENCE_SOURCE = "cadence_source";

      int TYPE_START = 1;
      int TYPE_END = 2;
      int TYPE_GPS = 3;
      int TYPE_PAUSE = 4;
      int TYPE_RESUME = 5;
      int TYPE_DISCARD = 6;
      // Phase 1: distinguish auto-pause/resume from a manually-triggered pause/resume so that
      // recompute, exporters, and the event log can tell them apart. Existing TYPE_PAUSE /
      // TYPE_RESUME values continue to mean "manual" by default.
      int TYPE_AUTO_PAUSE = 7;
      int TYPE_AUTO_RESUME = 8;
    }

    /** Phase 5: source of an HR or cadence sample on a {@link LOCATION} row. */
    interface SENSOR_SOURCE {
      // Reserved: keep 0 free so a NULL column equals "unknown" by convention without needing
      // a sentinel constant.
      int UNKNOWN = 0;
      int INTERNAL = 1; // Android device sensor
      int BLE = 2; // Bluetooth Low Energy (e.g. chest strap, optical sensor)
      int ANT = 3; // ANT+ accessory
      int MANUAL = 4; // user-entered, not measured
    }

    interface LAP {
      String TABLE = "lap";
      String ACTIVITY = "activity_id";
      String LAP = "lap";
      String INTENSITY = "type";
      String TIME = "time";
      String DISTANCE = "distance";
      String PLANNED_TIME = "planned_time";
      String PLANNED_DISTANCE = "planned_distance";
      String PLANNED_PACE = "planned_pace";
      String AVG_HR = "avg_hr";
      String MAX_HR = "max_hr";
      String AVG_CADENCE = "avg_cadence";
      // Phase 2: foreign key into the new STEP table; identifies the workout step this lap
      // belongs to. Nullable for activities recorded before this column existed and for
      // sessions that don't have a structured workout (e.g. open-ended runs).
      String STEP = "step";
    }

    /**
     * Phase 4: streaming event log written during the activity. Captures things that don't fit
     * the per-sample {@link LOCATION} or per-lap {@link LAP} rows: button presses, auto-step
     * advance, audio cue firings, sensor connect/disconnect, etc. Optional and append-only;
     * recompute and other readers must tolerate it being empty.
     */
    interface ACTIVITY_EVENT {
      String TABLE = "activity_event";
      String ACTIVITY = "activity_id";
      // Tracker.getTimeMs() at the moment the event fired (matches LOCATION.ELAPSED).
      String TS_ELAPSED_MS = "ts_elapsed_ms";
      // System.currentTimeMillis() at the moment the event fired.
      String TS_WALLCLOCK_MS = "ts_wallclock_ms";
      // Discriminator from {@link EVENT_TYPE}.
      String EVENT_TYPE = "event_type";
      String STEP_ID = "step_id"; // FK to STEP._id, NULL if no current step
      String LAP = "lap"; // mirrors LOCATION.LAP at fire time, NULL if unknown
      // Small text blob carrying event-specific data (e.g. cue voice text). Optional.
      String PAYLOAD = "payload";
    }

    /** Event type discriminator for {@link ACTIVITY_EVENT#EVENT_TYPE}. */
    interface EVENT_TYPE {
      int MANUAL_LAP = 1;
      int AUTO_STEP_ADVANCE = 2;
      int MANUAL_PAUSE = 3;
      int MANUAL_RESUME = 4;
      int AUTO_PAUSE = 5;
      int AUTO_RESUME = 6;
      int CUE_FIRED = 7;
      int GPS_LOST = 8;
      int GPS_REGAINED = 9;
      int HR_CONNECTED = 10;
      int HR_DISCONNECTED = 11;
    }

    /**
     * Phase 2: persistent workout schedule. One row per planned step (and one row per repeat
     * container). Written once at workout start by {@code Workout.persistSchedule}; never
     * mutated after. Lets recompute and future analytics query the workout's intent without
     * keeping the in-memory {@code Workout} object alive.
     */
    interface STEP {
      String TABLE = "step";
      String ACTIVITY = "activity_id";
      // Parent repeat step's _id, NULL for top-level entries. Lets us reconstruct the tree.
      String PARENT_ID = "parent_id";
      // Position among siblings (0-based) under the parent (or the workout root if no parent).
      String ORDER_IN_PARENT = "order_in_parent";
      // Mirrors DB.INTENSITY: ACTIVE / RESTING / WARMUP / COOLDOWN / REPEAT / RECOVERY.
      String INTENSITY = "intensity";
      // Mirrors DB.DIMENSION (TIME / DISTANCE). NULL for keypress-terminated steps and repeat
      // containers.
      String DURATION_TYPE = "duration_type";
      String DURATION_VALUE = "duration_value";
      // Mirrors DB.DIMENSION (PACE / SPEED / HR / HRZ ...). NULL when no target was set.
      String TARGET_TYPE = "target_type";
      String TARGET_MIN = "target_min";
      String TARGET_MAX = "target_max";
      // Number of repeats for REPEAT steps; NULL otherwise.
      String REPEAT_COUNT = "repeat_count";
      // Optional human-readable step name (e.g. "Hill repeats x6").
      String NAME = "name";
    }

    interface INTENSITY {
      int ACTIVE = 0;
      int RESTING = 1;
      int WARMUP = 2;
      int COOLDOWN = 3;
      int REPEAT = 4;
      int RECOVERY = 5;
    }

    interface DIMENSION {
      int TIME = 1;
      int DISTANCE = 2;
      int SPEED = 3;
      int PACE = 4;
      int HR = 5;
      int HRZ = 6;
      int CAD = 7;
      int TEMPERATURE = 8;
      int PRESSURE = 9;
    }

    interface ACCOUNT {
      String TABLE = "account";
      String NAME = "name";
      String FLAGS = "default_send";
      String ENABLED = "enabled";
      String AUTH_CONFIG = "auth_config";

      String URL = "url";
      String FORMAT = "format";

      int FLAG_UPLOAD = 0;
      int FLAG_LIVE = 2;
      long DEFAULT_FLAGS = (1 << FLAG_UPLOAD) + (1 << FLAG_LIVE);
      // DBVERSION update
      String DESCRIPTION = "description";
      String AUTH_METHOD = "auth_method";
      String ICON = "icon";
      String AUTH_NOTICE = "auth_notice";
    }

    interface EXPORT {
      String TABLE = "report";
      String ACTIVITY = "activity_id";
      String ACCOUNT = "account_id";
      String STATUS = "status";
      String EXTERNAL_ID = "ext_id";
      String EXTRA = "extra";
    }

    interface AUDIO_SCHEMES {
      String TABLE = "audio_schemes";
      String NAME = "name";
      String SORT_ORDER = "sort_order";
    }

    interface FEED {
      String TABLE = "feed";
      String ACCOUNT_ID = "account_id";
      String EXTERNAL_ID = "ext_id"; // ID per account
      String FEED_TYPE = "entry_type";
      String FEED_SUBTYPE = "type";
      String FEED_TYPE_STRING = "type_string";
      String START_TIME = "start_time";
      String DURATION = "duration";
      String DISTANCE = "distance";
      String USER_ID = "user_id";
      String USER_FIRST_NAME = "user_first_name";
      String USER_LAST_NAME = "user_last_name";
      String USER_IMAGE_URL = "user_image_url";
      String NOTES = "notes";
      String COMMENTS = "comments";
      String URL = "url";
      String FLAGS = "flags";

      int FEED_TYPE_ACTIVITY = 0; // FEED_SUBTYPE contains activity.type
      int FEED_TYPE_EVENT = 1;

      int FEED_TYPE_EVENT_DATE_HEADER = 0;
    }

    interface BEST_TIMES {
      String TABLE = "best_times";
      String DISTANCE = "distance";           // target distance in meters
      String TIME = "time";                   // time in milliseconds
      String PACE = "pace";                   // pace in seconds per km
      String ACTIVITY_ID = "activity_id";     // reference to activity
      String START_TIME = "start_time";       // when this record was achieved
      String AVG_HR = "avg_hr";              // average heart rate (segment / best window)
      String MAX_HR = "max_hr";              // max heart rate for whole activity
      String RANK = "rank";                   // 1-25 (top 25)
    }

    interface YEARLY_STATS {
      String TABLE = "yearly_stats";
      String YEAR = "year";                   // year (e.g., 2024)
      String TOTAL_DISTANCE = "total_distance"; // total distance in meters
      String AVG_PACE = "avg_pace";           // average pace in seconds per km
      String AVG_RUN_LENGTH = "avg_run_length"; // average run length in meters
      String RUN_COUNT = "run_count";         // number of runs
    }

    interface MONTHLY_STATS {
      String TABLE = "monthly_stats";
      String YEAR = "year";                   // year (e.g., 2024)
      String MONTH = "month";                 // month (1-12)
      String TOTAL_DISTANCE = "total_distance"; // total distance in meters
      String AVG_PACE = "avg_pace";           // average pace in seconds per km
      String AVG_RUN_LENGTH = "avg_run_length"; // average run length in meters
      String RUN_COUNT = "run_count";         // number of runs
    }

    interface COMPUTATION_TRACKING {
      String TABLE = "computation_tracking";
      String COMPUTATION_TYPE = "computation_type"; // 'best_times' or 'statistics'
      String LAST_COMPUTED_TIME = "last_computed_time"; // Unix timestamp
      String LAST_ACTIVITY_ID = "last_activity_id"; // ID of last activity when computed
    }

    interface MONTHLY_COMPARISON {
      String TABLE = "monthly_comparison";
      String CURRENT_MONTH_YEAR = "current_month_year"; // e.g., "2025-01"
      String CURRENT_AVG_PACE = "current_avg_pace";     // seconds per km
      String CURRENT_TOTAL_KM = "current_total_km";     // meters
      String CURRENT_AVG_BPM = "current_avg_bpm";      // int
      String CURRENT_PB_COUNT = "current_pb_count";     // int
      String CURRENT_AVG_DISTANCE_PER_RUN = "current_avg_distance_per_run"; // meters
      String CURRENT_TOP25_COUNT = "current_top25_count"; // int
      String OTHER_AVG_PACE = "other_avg_pace";        // seconds per km
      String OTHER_TOTAL_KM = "other_total_km";         // meters
      String OTHER_AVG_BPM = "other_avg_bpm";          // int
      String OTHER_PB_COUNT = "other_pb_count";         // int
      String OTHER_AVG_DISTANCE_PER_RUN = "other_avg_distance_per_run"; // meters
      String OTHER_TOP25_COUNT = "other_top25_count"; // int
      // Legacy column names (pre-v47); kept for database migrations only
      String CURRENT_AVG_BPM_5MIN_KM = "current_avg_bpm_5min_km";
      String OTHER_AVG_BPM_5MIN_KM = "other_avg_bpm_5min_km";
      String BEST_AVG_BPM_5MIN_KM = "best_avg_bpm_5min_km";
      String BEST_AVG_BPM_5MIN_KM_MONTH = "best_avg_bpm_5min_km_month";
      String CURRENT_AVG_PACE_ZONE_1 = "current_avg_pace_zone_1"; // seconds per km
      String OTHER_AVG_PACE_ZONE_1 = "other_avg_pace_zone_1"; // seconds per km
      String CURRENT_AVG_PACE_ZONE_2 = "current_avg_pace_zone_2"; // seconds per km
      String OTHER_AVG_PACE_ZONE_2 = "other_avg_pace_zone_2"; // seconds per km
      String CURRENT_AVG_PACE_ZONE_3 = "current_avg_pace_zone_3"; // seconds per km
      String OTHER_AVG_PACE_ZONE_3 = "other_avg_pace_zone_3"; // seconds per km
      String CURRENT_AVG_PACE_ZONE_4 = "current_avg_pace_zone_4"; // seconds per km
      String OTHER_AVG_PACE_ZONE_4 = "other_avg_pace_zone_4"; // seconds per km
      // Best month columns (value and month/year)
      String BEST_AVG_PACE = "best_avg_pace";           // seconds per km (lower is better)
      String BEST_AVG_PACE_MONTH = "best_avg_pace_month"; // e.g., "Sep2024"
      String BEST_TOTAL_KM = "best_total_km";           // meters (higher is better)
      String BEST_TOTAL_KM_MONTH = "best_total_km_month"; // e.g., "Sep2024"
      String BEST_AVG_DISTANCE_PER_RUN = "best_avg_distance_per_run"; // meters (higher is better)
      String BEST_AVG_DISTANCE_PER_RUN_MONTH = "best_avg_distance_per_run_month"; // e.g., "Sep2024"
      String BEST_AVG_BPM = "best_avg_bpm";             // int (context-dependent, usually lower is better)
      String BEST_AVG_BPM_MONTH = "best_avg_bpm_month"; // e.g., "Sep2024"
      String BEST_PB_COUNT = "best_pb_count";           // int (higher is better)
      String BEST_PB_COUNT_MONTH = "best_pb_count_month"; // e.g., "Sep2024"
      String BEST_TOP25_COUNT = "best_top25_count";     // int (higher is better)
      String BEST_TOP25_COUNT_MONTH = "best_top25_count_month"; // e.g., "Sep2024"
      String BEST_AVG_PACE_ZONE_1 = "best_avg_pace_zone_1"; // seconds per km (lower is better)
      String BEST_AVG_PACE_ZONE_1_MONTH = "best_avg_pace_zone_1_month"; // e.g., "Sep24"
      String BEST_AVG_PACE_ZONE_2 = "best_avg_pace_zone_2"; // seconds per km (lower is better)
      String BEST_AVG_PACE_ZONE_2_MONTH = "best_avg_pace_zone_2_month"; // e.g., "Sep24"
      String BEST_AVG_PACE_ZONE_3 = "best_avg_pace_zone_3"; // seconds per km (lower is better)
      String BEST_AVG_PACE_ZONE_3_MONTH = "best_avg_pace_zone_3_month"; // e.g., "Sep24"
      String BEST_AVG_PACE_ZONE_4 = "best_avg_pace_zone_4"; // seconds per km (lower is better)
      String BEST_AVG_PACE_ZONE_4_MONTH = "best_avg_pace_zone_4_month"; // e.g., "Sep24"
      String LAST_COMPUTED = "last_computed";          // timestamp
    }

    interface HR_ZONE_STATS {
      String TABLE = "hr_zone_stats";
      String ZONE_NUMBER = "zone_number";             // 0-5
      String TIME_IN_ZONE = "time_in_zone";           // milliseconds
      String AVG_PACE_IN_ZONE = "avg_pace_in_zone";   // seconds per km
      String LAST_COMPUTED = "last_computed";          // timestamp
    }

    interface TENDON {
      String TABLE = "tendon";
      String ZONE = "zone";             // int: see ZONE_*
      String NAME = "name";              // tendon name
      String DESCRIPTION = "description"; // tendon description
      String ACTIVE = "active";          // 1 active, 0 inactive

      int ZONE_KNEE = 0;
      int ZONE_CALVES = 1;
      int ZONE_ANKLE_FOOT = 2;
      int ZONE_HIP = 3;
    }

    interface ACTIVITY_INJURY {
      String TABLE = "activity_injury";
      String ACTIVITY_ID = "activity_id"; // FK to activity
      String PHASE = "phase";             // 0 before, 1 during, 2 after
      String ZONE = "zone";               // int: see TENDON zones
      String TENDON_ID = "tendon_id";     // FK to tendon (nullable)
      String PAIN = "pain";               // int 0..10
      String CREATED_AT = "created_at";   // seconds epoch

      int PHASE_BEFORE = 0;
      int PHASE_DURING = 1;
      int PHASE_AFTER = 2;
    }

    interface YEARLY_CUMULATIVE {
      String TABLE = "yearly_cumulative";
      String DATE = "date";                           // YYYY-MM-DD
      String CUMULATIVE_KM = "cumulative_km";         // meters from Jan 1 to this date
      String YEAR = "year";                           // int
      String LAST_COMPUTED = "last_computed";          // timestamp
    }

    // Heart Rate Zone Constants (MHR = 186)
    interface HR_ZONES {
      int MHR = 186;
      int ZONE0_MAX = 117;    // <63% MHR
      int ZONE1_MIN = 117;    // 63-71% MHR
      int ZONE1_MAX = 132;
      int ZONE2_MIN = 132;    // 71-78% MHR
      int ZONE2_MAX = 145;
      int ZONE3_MIN = 145;    // 78-85% MHR
      int ZONE3_MAX = 158;
      int ZONE4_MIN = 158;    // 85-92% MHR
      int ZONE4_MAX = 171;
      int ZONE5_MIN = 171;    // >92% MHR
    }
  }

  interface SPEED_UNIT {
    String PACE = "pace";
    String SPEED = "speed";
  }

  interface Intents {
    String PAUSE_RESUME = BuildConfig.applicationIdFull + ".PAUSE_RESUME";
    String NEW_LAP = BuildConfig.applicationIdFull + ".NEW_LAP";
    String FROM_NOTIFICATION = BuildConfig.applicationIdFull + ".FROM_NOTIFICATION";
    String START_ACTIVITY = BuildConfig.applicationIdFull + ".START_WORKOUT";
    // Used from Wear
    String START_WORKOUT = BuildConfig.applicationIdFull + ".START_WORKOUT";
    String PAUSE_WORKOUT = BuildConfig.applicationIdFull + ".PAUSE_WORKOUT";
    String RESUME_WORKOUT = BuildConfig.applicationIdFull + ".RESUME_WORKOUT";
  }

  interface TRACKER_STATE {
    int INIT = 0; // initial state
    int INITIALIZING = 1; // initializing components
    int INITIALIZED = 2; // initialized
    int STARTED = 3; // Workout started
    int PAUSED = 4; // Workout paused
    int CLEANUP = 5; // Cleaning up components
    int ERROR = 6; // Components failed to initialize ;
    int CONNECTING = 7;
    int CONNECTED = 8;
    int STOPPED = 9;
  }

  interface WORKOUT_TYPE {
    int BASIC = 0;
    int INTERVAL = 1;
    int ADVANCED = 2;
  }

  interface Wear {

    interface Path {
      String PREFIX = "/org.runnerup";

      /* Data: phone/wear nodes */
      String WEAR_NODE_ID = PREFIX + "/config/wear/node_id";
      String PHONE_NODE_ID = PREFIX + "/config/phone/node_id";
      String WEAR_APP = PREFIX + "/config/wear/app";
      String PHONE_APP = PREFIX + "/config/phone/app";

      /* Data: Card headers */
      String HEADERS = PREFIX + "/config/headers";

      /* Data: Tracker/workout state */
      String TRACKER_STATE = PREFIX + "/tracker/state";
      String WORKOUT_PLAN = PREFIX + "/workout/plan";

      /* Msg: workout event */
      String MSG_WORKOUT_EVENT = PREFIX + "/workout/event";

      /* Msg: pause/resume from wear to phone */
      String MSG_CMD_WORKOUT_PAUSE = PREFIX + "/workout/pause";
      String MSG_CMD_WORKOUT_RESUME = PREFIX + "/workout/resume";
      String MSG_CMD_WORKOUT_NEW_LAP = PREFIX + "/workout/new_lap";
      String MSG_CMD_WORKOUT_START = PREFIX + "/workout/start";
    }

    interface RunInfo {
      String HEADER = "HEADER/";
      String DATA = "DATA/";
      String SCREENS = "SCREENS"; // Array of screen sizes, stored in HEADERS
      String PAUSE_STEP = "PAUSE_STEP"; // Stored in HEADERS
      String SCROLL = "SCROLL"; // Stored in HEADERS
      String COUNTDOWN = "COUNTDOWN"; // Stored in DATA
    }

    interface TrackerState {
      String STATE = "state";
    }
  }

  /** Preference keys; prefer {@link PreferenceKeys} in new code. */
  interface Prefs {
    String PREF_START_GPS = PreferenceKeys.PREF_START_GPS;
    String PREF_HRZ_VALUES = PreferenceKeys.PREF_HRZ_VALUES;
    String PREF_RUNNERUP_LIVE_ACTIVE = PreferenceKeys.PREF_RUNNERUP_LIVE_ACTIVE;
  }
}
