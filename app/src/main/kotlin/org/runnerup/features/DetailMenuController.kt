/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import org.runnerup.R
import org.runnerup.core.content.ActivityProvider
import org.runnerup.core.util.FileNameHelper
import org.runnerup.data.ActivityCleaner
import org.runnerup.data.DBHelper
import org.runnerup.data.PathSimplifier
import org.runnerup.core.workout.Sport
import org.runnerup.ui.common.widget.TitleSpinner
import java.util.ArrayList
import org.runnerup.core.content.ActivityProvider.GPX_MIME
import org.runnerup.core.content.ActivityProvider.TCX_MIME

/** Detail screen menu actions: recompute, simplify path, share, delete. */
internal class DetailMenuController(private val activity: DetailActivity) {

  private var mDB: SQLiteDatabase? = null
  private var mID: Long = 0
  private var mStartTime: Long = 0
  private var sport: TitleSpinner? = null

  fun bind(mDB: SQLiteDatabase, mID: Long, sport: TitleSpinner) {
    this.mDB = mDB
    this.mID = mID
    this.sport = sport
  }

  fun setStartTime(startTime: Long) {
    mStartTime = startTime
  }

  fun onRecomputeSelected() {
    AlertDialog.Builder(activity)
        .setTitle(org.runnerup.common.R.string.Recompute_activity)
        .setMessage(org.runnerup.common.R.string.Are_you_sure)
        .setPositiveButton(org.runnerup.common.R.string.Yes) { dialog, _ ->
          dialog.dismiss()
          val db = mDB ?: return@setPositiveButton
          ActivityCleaner().recompute(db, mID, true)
          activity.requery()
          activity.fillHeaderData()
          activity.finish()
        }
        .setNegativeButton(org.runnerup.common.R.string.No) { dialog, _ -> dialog.dismiss() }
        .show()
  }

  fun onSimplifyPathSelected() {
    AlertDialog.Builder(activity)
        .setTitle(org.runnerup.common.R.string.path_simplification_menu)
        .setMessage(org.runnerup.common.R.string.Are_you_sure)
        .setPositiveButton(org.runnerup.common.R.string.Yes) { dialog, _ ->
          dialog.dismiss()
          val db = mDB ?: return@setPositiveButton
          val simplifier = PathSimplifier(activity)
          val ids: ArrayList<String> = simplifier.getNoisyLocationIDsAsStrings(db, mID)
          ActivityCleaner.deleteLocations(db, ids)
          ActivityCleaner().recompute(db, mID)
          activity.requery()
          activity.fillHeaderData()
          activity.finish()
        }
        .setNegativeButton(org.runnerup.common.R.string.No) { dialog, _ -> dialog.dismiss() }
        .show()
  }

  fun onShareSelected() {
    val which = intArrayOf(1)
    val items = arrayOf<CharSequence>("gpx", "tcx")
    AlertDialog.Builder(activity)
        .setTitle(activity.getString(org.runnerup.common.R.string.Share_activity))
        .setPositiveButton(org.runnerup.common.R.string.OK) { dialog, _ ->
          if (which[0] == -1) {
            dialog.dismiss()
            return@setPositiveButton
          }
          val fmt = items[which[0]]
          val intent = Intent(Intent.ACTION_SEND)
          if (fmt == "tcx") {
            intent.type = TCX_MIME
          } else {
            intent.type = GPX_MIME
          }
          val sportValue = sport?.valueInt ?: 0
          val actType = Sport.textOf(activity.resources, sportValue)
          val uri =
              Uri.parse(
                  "content://${ActivityProvider.AUTHORITY}/$fmt/$mID/" +
                      FileNameHelper.getExportFileName(mStartTime, actType) +
                      fmt,
              )
          intent.putExtra(Intent.EXTRA_STREAM, uri)
          activity.startActivity(
              Intent.createChooser(
                  intent,
                  activity.getString(org.runnerup.common.R.string.Share_activity),
              ),
          )
        }
        .setNegativeButton(
            org.runnerup.common.R.string.Cancel,
            { dialog, _ -> dialog.dismiss() },
        )
        .setSingleChoiceItems(items, which[0]) { _, w -> which[0] = w }
        .show()
  }

  fun onDeleteSelected() {
    AlertDialog.Builder(activity)
        .setTitle(org.runnerup.common.R.string.Delete_activity)
        .setMessage(org.runnerup.common.R.string.Are_you_sure)
        .setPositiveButton(org.runnerup.common.R.string.Yes) { dialog, _ ->
          val db = mDB ?: return@setPositiveButton
          DBHelper.deleteActivity(db, mID)
          dialog.dismiss()
          activity.setResult(android.app.Activity.RESULT_OK)
          activity.finish()
        }
        .setNegativeButton(org.runnerup.common.R.string.No) { dialog, _ -> dialog.dismiss() }
        .show()
  }
}
