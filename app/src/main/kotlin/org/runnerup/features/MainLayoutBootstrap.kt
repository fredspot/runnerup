/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.ContentValues
import android.content.Context
import android.content.res.AssetManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.FileUtil
import org.runnerup.data.DBHelper

/** First-run asset install and external DB import for [MainLayout]. */
object MainLayoutBootstrap {

  @JvmStatic
  fun importDatabaseFromIntent(activity: AppCompatActivity, data: Uri?) {
    if (data == null) return
    val filePath =
        if ("content" == data.scheme) {
          val cursor =
              activity.contentResolver.query(
                  data,
                  arrayOf(android.provider.MediaStore.Images.ImageColumns.DATA),
                  null,
                  null,
                  null,
              ) ?: return
          cursor.use {
            if (!it.moveToFirst()) return
            it.getString(0)
          }
        } else {
          data.path
        }
    if (filePath != null) {
      Log.i(activity.javaClass.simpleName, "Importing database from $filePath")
      DBHelper.importDatabase(activity, filePath)
    }
  }

  @JvmStatic
  fun installBundledAssets(activity: AppCompatActivity, srcBase: String, dstBase: String) {
    handleBundled(activity, activity.applicationContext.assets, srcBase, dstBase)
  }

  private fun handleBundled(
      activity: AppCompatActivity,
      mgr: AssetManager,
      srcBase: String,
      dstBase: String,
  ) {
    val list =
        try {
          mgr.list(srcBase)
        } catch (e: IOException) {
          e.printStackTrace()
          null
        } ?: return

    for (add in list) {
      val src = "$srcBase${File.separator}$add"
      val dst = "$dstBase${File.separator}$add"
      var isFile = false
      try {
        mgr.open(src).close()
        isFile = true
      } catch (_: Exception) {
        // directory
      }

      Log.v(activity.javaClass.name, "Found: $src, $dst, isFile: $isFile")

      if (!isFile) {
        val dstDir = File(dstBase)
        dstDir.mkdir()
        if (!dstDir.isDirectory) {
          Log.w(activity.javaClass.name, "Failed to copy $src; \"$dstBase\" is not a directory")
          continue
        }
        handleBundled(activity, mgr, src, dst)
      } else {
        val dstFile = File(dst)
        if (dstFile.isDirectory || dstFile.isFile) {
          Log.v(activity.javaClass.name, "Skip: $dst")
          continue
        }
        val key = "install_bundled_$add"
        val pref = PreferenceManager.getDefaultSharedPreferences(activity)
        if (pref.contains(key)) {
          Log.v(activity.javaClass.name, "Skip already existing pref: $key")
          continue
        }
        pref.edit().putBoolean(key, true).apply()
        Log.v(activity.javaClass.name, "Copying: $dst")
        var input: InputStream? = null
        try {
          input = mgr.open(src)
          FileUtil.copy(input, dst)
          handleAudioCueHook(activity, add)
        } catch (e: IOException) {
          e.printStackTrace()
        } finally {
          FileUtil.close(input)
        }
      }
    }
  }

  private fun handleAudioCueHook(activity: AppCompatActivity, key: String) {
    if (!key.contains("_audio_cues.xml")) return
    val name = key.substring(0, key.indexOf("_audio_cues.xml"))
    val mDB: SQLiteDatabase = DBHelper.getWritableDatabase(activity)
    val tmp = ContentValues()
    tmp.put(DB.AUDIO_SCHEMES.NAME, name)
    tmp.put(DB.AUDIO_SCHEMES.SORT_ORDER, 0)
    mDB.insert(DB.AUDIO_SCHEMES.TABLE, null, tmp)
    DBHelper.closeDB(mDB)
  }
}
