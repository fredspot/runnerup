package org.runnerup.data;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.runnerup.common.util.Constants.DB;

/** SQL helpers to shift lap indices after deleting or merging laps. */
final class LapIndexRenumberer {

  private LapIndexRenumberer() {}

  static void renumberLapsAfterDeletingIndex(
      SQLiteDatabase db, long activityId, int deletedLapIndex, int offset) {
    String aid = String.valueOf(activityId);
    String del = String.valueOf(deletedLapIndex);
    String offStr = String.valueOf(offset);
    db.execSQL(
        "UPDATE "
            + DB.LAP.TABLE
            + " SET "
            + DB.LAP.LAP
            + " = "
            + DB.LAP.LAP
            + " + "
            + offset
            + " WHERE "
            + DB.LAP.ACTIVITY
            + " = ? AND "
            + DB.LAP.LAP
            + " > ?",
        new String[] {aid, del});
    db.execSQL(
        "UPDATE "
            + DB.LOCATION.TABLE
            + " SET "
            + DB.LOCATION.LAP
            + " = "
            + DB.LOCATION.LAP
            + " + "
            + offset
            + " WHERE "
            + DB.LOCATION.ACTIVITY
            + " = ? AND "
            + DB.LOCATION.LAP
            + " > ?",
        new String[] {aid, del});
    try {
      db.execSQL(
          "UPDATE "
              + DB.ACTIVITY_EVENT.TABLE
              + " SET "
              + DB.ACTIVITY_EVENT.LAP
              + " = "
              + DB.ACTIVITY_EVENT.LAP
              + " + "
              + offset
              + " WHERE "
              + DB.ACTIVITY_EVENT.ACTIVITY
              + " = ? AND "
              + DB.ACTIVITY_EVENT.LAP
              + " IS NOT NULL AND "
              + DB.ACTIVITY_EVENT.LAP
              + " > ?",
          new String[] {aid, del});
    } catch (Exception e) {
      Log.w("LapIndexRenumberer", "activity_event renumber (add): " + e.getMessage());
    }
    int dec = offset + 1;
    db.execSQL(
        "UPDATE "
            + DB.LAP.TABLE
            + " SET "
            + DB.LAP.LAP
            + " = "
            + DB.LAP.LAP
            + " - "
            + dec
            + " WHERE "
            + DB.LAP.ACTIVITY
            + " = ? AND "
            + DB.LAP.LAP
            + " > ?",
        new String[] {aid, offStr});
    db.execSQL(
        "UPDATE "
            + DB.LOCATION.TABLE
            + " SET "
            + DB.LOCATION.LAP
            + " = "
            + DB.LOCATION.LAP
            + " - "
            + dec
            + " WHERE "
            + DB.LOCATION.ACTIVITY
            + " = ? AND "
            + DB.LOCATION.LAP
            + " > ?",
        new String[] {aid, offStr});
    try {
      db.execSQL(
          "UPDATE "
              + DB.ACTIVITY_EVENT.TABLE
              + " SET "
              + DB.ACTIVITY_EVENT.LAP
              + " = "
              + DB.ACTIVITY_EVENT.LAP
              + " - "
              + dec
              + " WHERE "
              + DB.ACTIVITY_EVENT.ACTIVITY
              + " = ? AND "
              + DB.ACTIVITY_EVENT.LAP
              + " IS NOT NULL AND "
              + DB.ACTIVITY_EVENT.LAP
              + " > ?",
          new String[] {aid, offStr});
    } catch (Exception e) {
      Log.w("LapIndexRenumberer", "activity_event renumber (sub): " + e.getMessage());
    }
  }
}
