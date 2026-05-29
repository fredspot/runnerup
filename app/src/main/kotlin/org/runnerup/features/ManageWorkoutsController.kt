/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.core.workout.WorkoutSerializer
import org.runnerup.sync.SyncManager
import org.runnerup.sync.SyncManager.WorkoutRef
import org.runnerup.sync.Synchronizer

/** Sync, import/export, and workout file operations for [ManageWorkoutsActivity]. */
internal class ManageWorkoutsController(
    private val activity: AppCompatActivity,
    private val phoneLabel: String,
    private val listController: ManageWorkoutsListController,
) {

  interface Host {
    fun getString(resId: Int): String
    fun getString(resId: Int, vararg formatArgs: Any): String
    fun finish()
    fun startActivity(intent: Intent)
    fun getContentResolver(): ContentResolver
    fun getApplicationContext(): android.content.Context
  }

  val providers = ArrayList<ContentValues>()
  val workouts = HashMap<String, ArrayList<WorkoutRef>>()
  val pendingWorkouts = HashSet<WorkoutRef>()

  private val loadedProviders = HashSet<String>()
  private var pendingExpandProvider: String? = null
  private var uploading = false

  fun initProviders() {
    providers.clear()
    val phone = ContentValues()
    phone.put(Constants.DB.ACCOUNT.NAME, phoneLabel)
    providers.add(phone)
    refreshListData()
  }

  fun requery(db: SQLiteDatabase) {
    try {
      val sql =
          "SELECT DISTINCT " +
              "  acc._id, " +
              ("  acc." + Constants.DB.ACCOUNT.NAME + ", ") +
              ("  acc." + Constants.DB.ACCOUNT.AUTH_CONFIG + ", ") +
              ("  acc." + Constants.DB.ACCOUNT.FLAGS + ", ") +
              ("  acc." + Constants.DB.ACCOUNT.ENABLED + " ") +
              (" FROM " + Constants.DB.ACCOUNT.TABLE + " acc ")
      db.rawQuery(sql, null).close()
    } catch (e: IllegalStateException) {
      Log.e(javaClass.name, "requery: ${e.message}")
      return
    }
    initProviders()
  }

  fun listLocal(host: Host) {
    val newlist = ArrayList<WorkoutRef>()
    val list = WorkoutListAdapter.load(host.getApplicationContext())
    if (list != null) {
      for (s in list) {
        newlist.add(WorkoutRef(phoneLabel, null, s.substring(0, s.lastIndexOf('.'))))
      }
    }
    workouts.remove(phoneLabel)
    workouts[phoneLabel] = newlist
    refreshListData()
  }

  fun refreshListData() {
    listController.setProvidersAndWorkouts(providers, workouts)
  }

  fun onWorkoutLongPress(host: Host, ref: WorkoutRef) {
    if (phoneLabel != ref.synchronizer) {
      return
    }
    AlertDialog.Builder(activity, R.style.AlertDialogTheme)
        .setTitle(ref.workoutName)
        .setItems(
            arrayOf(
                host.getString(org.runnerup.common.R.string.Edit),
                host.getString(org.runnerup.common.R.string.Delete),
            ),
        ) { dialog, which ->
          dialog.dismiss()
          when (which) {
            0 -> openWorkoutEditor(host, ref)
            1 -> confirmDeleteWorkout(host, ref)
          }
        }
        .show()
  }

  fun openWorkoutEditor(host: Host, ref: WorkoutRef) {
    val intent =
        Intent(host.getApplicationContext(), CreateAdvancedWorkout::class.java).apply {
          putExtra(ManageWorkoutsActivity.WORKOUT_NAME, ref.workoutName)
          putExtra(ManageWorkoutsActivity.WORKOUT_EXISTS, true)
        }
    host.startActivity(intent)
  }

  fun confirmDeleteWorkout(host: Host, ref: WorkoutRef) {
    AlertDialog.Builder(activity, R.style.AlertDialogTheme)
        .setTitle(
            host.getString(org.runnerup.common.R.string.Delete_workout) + " " + ref.workoutName,
        )
        .setMessage(org.runnerup.common.R.string.Are_you_sure)
        .setPositiveButton(org.runnerup.common.R.string.Yes) { dialog, _ ->
          dialog.dismiss()
          deleteWorkout(host, ref)
        }
        .setNegativeButton(org.runnerup.common.R.string.No) { dialog, _ -> dialog.dismiss() }
        .show()
  }

  fun deleteWorkout(host: Host, ref: WorkoutRef) {
    val f = WorkoutSerializer.getFile(host.getApplicationContext(), ref.workoutName)
    f.delete()
    val pref = PreferenceManager.getDefaultSharedPreferences(host.getApplicationContext())
    val prefKey = host.getString(R.string.pref_advanced_workout)
    if (ref.workoutName == pref.getString(prefKey, "")) {
      pref.edit().putString(prefKey, "").apply()
    }
    listLocal(host)
  }

  fun showCreateWorkoutDialog(host: Host) {
    val intent = Intent(host.getApplicationContext(), CreateAdvancedWorkout::class.java)
    val input = EditText(activity)
    AlertDialog.Builder(activity, R.style.AlertDialogTheme)
        .setTitle(org.runnerup.common.R.string.Create_new_workout)
        .setMessage(org.runnerup.common.R.string.Set_workout_name)
        .setView(input)
        .setPositiveButton(org.runnerup.common.R.string.OK) { _, _ ->
          val value = input.text.toString()
          intent.putExtra(ManageWorkoutsActivity.WORKOUT_NAME, value)
          intent.putExtra(ManageWorkoutsActivity.WORKOUT_EXISTS, false)
          host.startActivity(intent)
        }
        .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ -> dialog.dismiss() }
        .show()
  }

  fun getFilename(data: Uri): String? {
    Log.i(javaClass.name, "scheme: $data")
    var name: String? = null
    if (ContentResolver.SCHEME_FILE == data.scheme) {
      name = data.lastPathSegment
    } else if (ContentResolver.SCHEME_CONTENT == data.scheme) {
      val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
      activity.contentResolver.query(data, projection, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
          val col = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
          if (col >= 0) {
            name = c.getString(col)
          }
        }
      }
    }
    return name
  }

  @Throws(Exception::class)
  fun importData(host: Host, fileName: String, data: Uri) {
    val cr = host.getContentResolver()
    val inputStream = cr.openInputStream(data) ?: throw Exception("Failed to get input stream")
    val w = WorkoutSerializer.readJSON(BufferedReader(InputStreamReader(inputStream)))
    inputStream.close()
    if (w == null) {
      throw Exception("Failed to parse content")
    }

    var workoutFileName = fileName
    if (workoutFileName.endsWith(".json")) {
      workoutFileName = workoutFileName.substring(0, workoutFileName.length - ".json".length)
    }

    val prefix = host.getString(org.runnerup.common.R.string.RunnerUp_workout) + ": "
    if (workoutFileName.startsWith(prefix) && workoutFileName.length > prefix.length) {
      workoutFileName = workoutFileName.substring(prefix.length)
    }

    val exists = WorkoutSerializer.getFile(host.getApplicationContext(), workoutFileName).exists()
    val selected = booleanArrayOf(false)

    val builder =
        AlertDialog.Builder(activity)
            .setTitle(
                host.getString(org.runnerup.common.R.string.Import_workout) + ": $workoutFileName",
            )
            .setPositiveButton(org.runnerup.common.R.string.Yes) { dialog, _ ->
              dialog.dismiss()
              var saveName = workoutFileName
              try {
                if (exists && !selected[0]) {
                  for (i in 1..24) {
                    saveName = "$workoutFileName-$i"
                    if (!WorkoutSerializer.getFile(host.getApplicationContext(), saveName)
                        .exists()) {
                      break
                    }
                  }
                  Toast.makeText(
                          activity,
                          host.getString(org.runnerup.common.R.string.Saving_as) + " $saveName",
                          Toast.LENGTH_SHORT,
                      )
                      .show()
                }
                saveImport(host, saveName, cr.openInputStream(data)!!)
              } catch (e: IOException) {
                e.printStackTrace()
              }
              launchMain(host, saveName)
            }
            .setNegativeButton(org.runnerup.common.R.string.No) { dialog, _ ->
              dialog.dismiss()
              host.finish()
            }

    if (exists) {
      val items = arrayOf(host.getString(org.runnerup.common.R.string.Overwrite_existing))
      builder.setMultiChoiceItems(items, selected) { _, which, isChecked ->
        selected[which] = isChecked
      }
    }
    builder.show()
  }

  @Throws(IOException::class)
  private fun saveImport(host: Host, file: String, inputStream: InputStream) {
    val f = WorkoutSerializer.getFile(host.getApplicationContext(), file)
    BufferedOutputStream(FileOutputStream(f)).use { out ->
      BufferedInputStream(inputStream).use { input ->
        val buf = ByteArray(1024)
        var bytesRead: Int
        while (input.read(buf).also { bytesRead = it } > 0) {
          out.write(buf, 0, bytesRead)
        }
      }
    }
  }

  private fun launchMain(host: Host, fileName: String) {
    val pref = PreferenceManager.getDefaultSharedPreferences(host.getApplicationContext())
    pref.edit()
        .putString(host.getString(R.string.pref_advanced_workout), fileName)
        .apply()
    val intent =
        Intent(host.getApplicationContext(), MainLayout::class.java)
            .putExtra("mode", StartFragment.TAB_ADVANCED)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    host.startActivity(intent)
    host.finish()
  }

  fun onWorkoutListHeaderClick(
      host: Host,
      syncManager: SyncManager,
      provider: String,
      expanded: Boolean,
  ) {
    if (expanded) {
      listController.collapseProvider(provider)
      return
    }
    if (phoneLabel == provider) {
      listController.expandProvider(provider)
      return
    }
    if (loadedProviders.contains(provider)) {
      listController.expandProvider(provider)
      return
    }
    pendingExpandProvider = provider
    uploading = true
    if (!syncManager.isConfigured(provider)) {
      syncManager.connect(onSynchronizerConfiguredCallback(syncManager), provider)
    } else {
      onSynchronizerConfiguredCallback(syncManager).run(provider, Synchronizer.Status.OK)
    }
  }

  private fun onSynchronizerConfiguredCallback(syncManager: SyncManager): SyncManager.Callback {
    return SyncManager.Callback { synchronizerName, status ->
      Log.i(javaClass.name, "status: $status")
      if (status != Synchronizer.Status.OK) {
        uploading = false
        pendingExpandProvider = null
        return@Callback
      }
      val list = workouts[synchronizerName] ?: return@Callback
      list.clear()
      val tmp = HashSet<String>()
      tmp.add(synchronizerName)
      syncManager.loadWorkoutList(list, onLoadWorkoutListCallback(), tmp)
    }
  }

  private fun onLoadWorkoutListCallback(): SyncManager.Callback {
    return SyncManager.Callback { _, status ->
      uploading = false
      if (status == Synchronizer.Status.OK && pendingExpandProvider != null) {
        loadedProviders.add(pendingExpandProvider!!)
        refreshListData()
        listController.expandProvider(pendingExpandProvider!!)
      }
      pendingExpandProvider = null
    }
  }
}
