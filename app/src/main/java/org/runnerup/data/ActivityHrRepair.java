package org.runnerup.data;

import android.database.sqlite.SQLiteDatabase;
import org.runnerup.common.util.Constants.DB;

/** Repairs legacy swapped avg/max heart-rate columns on activity rows. */
public final class ActivityHrRepair {

  private ActivityHrRepair() {}

  public static void repairSwappedActivityHeartRates(SQLiteDatabase db) {
    db.execSQL(
        "UPDATE "
            + DB.ACTIVITY.TABLE
            + " SET "
            + DB.ACTIVITY.AVG_HR
            + " = "
            + DB.ACTIVITY.MAX_HR
            + ", "
            + DB.ACTIVITY.MAX_HR
            + " = "
            + DB.ACTIVITY.AVG_HR
            + " WHERE "
            + DB.ACTIVITY.AVG_HR
            + " > 0 AND "
            + DB.ACTIVITY.MAX_HR
            + " > 0 AND "
            + DB.ACTIVITY.AVG_HR
            + " > "
            + DB.ACTIVITY.MAX_HR);
  }
}
