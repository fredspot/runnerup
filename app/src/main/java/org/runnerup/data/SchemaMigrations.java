package org.runnerup.data;

import android.database.sqlite.SQLiteDatabase;

/** Entry point for SQLite schema version upgrades (body lives on {@link DBHelper}). */
public final class SchemaMigrations {

  private SchemaMigrations() {}

  public static void upgrade(DBHelper dbHelper, SQLiteDatabase db, int oldVersion, int newVersion) {
    dbHelper.applySchemaUpgrade(db, oldVersion, newVersion);
  }
}
