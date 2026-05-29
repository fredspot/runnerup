/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.core.util.ViewUtil
import org.runnerup.data.DBHelper
import org.runnerup.sync.SyncManager

class ManageWorkoutsActivity : AppCompatActivity(), Constants, ManageWorkoutsController.Host {

  private var mDB: SQLiteDatabase? = null
  private lateinit var phoneLabel: String
  private lateinit var controller: ManageWorkoutsController
  private var listController: ManageWorkoutsListController? = null
  private var syncManager: SyncManager? = null
  private lateinit var configureLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.manage_workouts)

    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setBackgroundDrawable(ContextCompat.getDrawable(this@ManageWorkoutsActivity, R.color.backgroundPrimary))
    }

    phoneLabel = getString(org.runnerup.common.R.string.my_phone)

    mDB = DBHelper.getReadableDatabase(this)
    syncManager = SyncManager(this)
    configureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
          syncManager?.handleConfigureResult(result.resultCode, result.data)
          mDB?.let { controller.requery(it) }
        }
    syncManager?.setConfigureLauncher(configureLauncher)

    val list = findViewById<RecyclerView>(R.id.expandable_list_view)
    listController =
        ManageWorkoutsListController(
            list,
            { provider, expanded ->
              controller.onWorkoutListHeaderClick(this, syncManager!!, provider, expanded)
            },
            { ref -> controller.onWorkoutLongPress(this, ref) },
        )
    controller = ManageWorkoutsController(this, phoneLabel, listController!!)

    findViewById<FloatingActionButton>(R.id.manage_workout_add_fab)
        .setOnClickListener { controller.showCreateWorkoutDialog(this) }

    mDB?.let { controller.requery(it) }
    controller.listLocal(this)
    listController?.expandFirstGroupIfNeeded()

    intent.data?.let { data ->
      intent.data = null
      val fileName = controller.getFilename(data) ?: "noname"
      try {
        controller.importData(this, fileName, data)
      } catch (e: Exception) {
        AlertDialog.Builder(this)
            .setTitle(org.runnerup.common.R.string.Error)
            .setMessage(getString(org.runnerup.common.R.string.Failed_to_import) + ": $fileName")
            .setPositiveButton(org.runnerup.common.R.string.OK) { dialog, _ ->
              dialog.dismiss()
              finish()
            }
            .show()
      }
    }

    ViewUtil.Insets(findViewById(R.id.manage_workouts_view), true)
  }

  override fun onResume() {
    super.onResume()
    controller.listLocal(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    mDB?.let { DBHelper.closeDB(it) }
    syncManager?.close()
  }

  override fun onSupportNavigateUp(): Boolean {
    finish()
    return true
  }

  companion object {
    const val WORKOUT_NAME: String = ""
    const val WORKOUT_EXISTS: String = "workout_exists"
  }
}
