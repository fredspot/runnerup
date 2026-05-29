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

package org.runnerup.features

import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.PreferenceManager
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.Bitfield
import org.runnerup.core.util.ViewUtil
import org.runnerup.core.workout.FileFormats
import org.runnerup.data.DBHelper
import org.runnerup.sync.FileSynchronizer
import org.runnerup.sync.RunnerUpLiveSynchronizer
import org.runnerup.sync.SyncManager
import org.runnerup.sync.Synchronizer
import org.runnerup.ui.common.widget.WidgetUtil

class AccountActivity : AppCompatActivity(), Constants {

  private lateinit var mSynchronizerName: String
  private var mDB: SQLiteDatabase? = null
  private val mCursors = ArrayList<Cursor>()

  private var flags: Long = 0
  private lateinit var format: FileFormats
  private var syncManager: SyncManager? = null
  private var mRunnerUpLiveApiAddress: EditText? = null
  private lateinit var configureLauncher: ActivityResultLauncher<Intent>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.account)

    WidgetUtil.addLegacyOverflowButton(window)

    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setBackgroundDrawable(
          android.graphics.drawable.ColorDrawable(getColor(R.color.backgroundPrimary)))
    }

    mSynchronizerName = intent.getStringExtra("synchronizer")!!

    mDB = DBHelper.getReadableDatabase(this)
    syncManager = SyncManager(this)
    configureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
          syncManager?.handleConfigureResult(result.resultCode, result.data)
        }
    syncManager?.setConfigureLauncher(configureLauncher)
    fillData()

    val synchronizer = syncManager?.getSynchronizerByName(mSynchronizerName) ?: return

    findViewById<Button>(R.id.ok_account_button).setOnClickListener(okButtonClick)
    findViewById<Button>(R.id.account_upload_button).setOnClickListener(uploadButtonClick)

    val downloadBtn = findViewById<Button>(R.id.account_download_button)
    if (synchronizer.checkSupport(Synchronizer.Feature.ACTIVITY_LIST) &&
        synchronizer.checkSupport(Synchronizer.Feature.GET_ACTIVITY)) {
      downloadBtn.setOnClickListener(downloadButtonClick)
    } else {
      downloadBtn.visibility = View.GONE
    }

    findViewById<Button>(R.id.disconnect_account_button).setOnClickListener(disconnectButtonClick)

    ViewUtil.Insets(findViewById(R.id.account_rootview), false)
  }

  override fun onDestroy() {
    super.onDestroy()
    for (c in mCursors) {
      c.close()
    }
    DBHelper.closeDB(mDB)
    mCursors.clear()
    syncManager?.close()
  }

  private fun fillData() {
    val from =
        arrayOf(
            "_id",
            DB.ACCOUNT.NAME,
            DB.ACCOUNT.FLAGS,
            DB.ACCOUNT.FORMAT,
            DB.ACCOUNT.AUTH_CONFIG,
        )

    val args = arrayOf(mSynchronizerName)
    val c = mDB!!.query(DB.ACCOUNT.TABLE, from, DB.ACCOUNT.NAME + " = ?", args, null, null, null)

    if (c.moveToFirst()) {
      val tmp = DBHelper.get(c)
      val synchronizer = syncManager?.add(tmp)
      flags = tmp.getAsLong(DB.ACCOUNT.FLAGS)
      format = FileFormats(tmp.getAsString(DB.ACCOUNT.FORMAT))
      if (synchronizer == null) {
        return
      }

      val im = findViewById<ImageView>(R.id.account_icon)
      val tv = findViewById<TextView>(R.id.account_name)
      if (synchronizer.iconId == 0 || mSynchronizerName == FileSynchronizer.NAME) {
        if (!TextUtils.isEmpty(synchronizer.publicUrl)) {
          tv.text = synchronizer.publicUrl
          tv.tag = synchronizer.publicUrl
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
              synchronizer.name != FileSynchronizer.NAME) {
            tv.setOnClickListener(urlButtonClick)
          }
        } else {
          tv.text = synchronizer.name
        }
        im.visibility = View.GONE
        tv.visibility = View.VISIBLE
      } else {
        im.setImageDrawable(AppCompatResources.getDrawable(this, synchronizer.iconId))
        if (!TextUtils.isEmpty(synchronizer.publicUrl)) {
          im.tag = synchronizer.publicUrl
          im.setOnClickListener(urlButtonClick)
        }
        im.visibility = View.VISIBLE
        tv.visibility = View.GONE
      }

      if (synchronizer.name == RunnerUpLiveSynchronizer.NAME) {
        val prefs: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val res: Resources = resources
        val POST_URL = "http://weide.devsparkles.se/api/Resource/"
        val postUrl = prefs.getString(res.getString(R.string.pref_runneruplive_serveradress), POST_URL)

        mRunnerUpLiveApiAddress = EditText(applicationContext)
        mRunnerUpLiveApiAddress!!.setSingleLine()
        mRunnerUpLiveApiAddress!!.setText(postUrl, TextView.BufferType.EDITABLE)
        addRow(
            resources.getString(org.runnerup.common.R.string.RunnerUp_live_address) + ":",
            mRunnerUpLiveApiAddress)
      }

      if (synchronizer.checkSupport(Synchronizer.Feature.UPLOAD)) {
        val cb = CheckBox(this)
        cb.tag = DB.ACCOUNT.FLAG_UPLOAD
        cb.isChecked = Bitfield.test(flags, DB.ACCOUNT.FLAG_UPLOAD)
        cb.setOnCheckedChangeListener(sendCBChecked)
        cb.minimumHeight = 48
        cb.minimumWidth = 48
        addRow(resources.getString(org.runnerup.common.R.string.Automatic_upload), cb)
      } else {
        findViewById<Button>(R.id.account_upload_button).visibility = View.GONE
      }

      if (synchronizer.checkSupport(Synchronizer.Feature.FILE_FORMAT)) {
        addRow(resources.getString(org.runnerup.common.R.string.File_format), null)
        for (f in FileFormats.ALL_FORMATS) {
          val cb = CheckBox(this)
          cb.isChecked = format.contains(f)
          cb.tag = f
          cb.setOnCheckedChangeListener(sendCBChecked)
          cb.minimumHeight = 48
          cb.minimumWidth = 48
          addRow(f.name, cb)
        }
      }

      if (synchronizer.checkSupport(Synchronizer.Feature.LIVE)) {
        val cb = CheckBox(this)
        cb.tag = DB.ACCOUNT.FLAG_LIVE
        cb.isChecked = Bitfield.test(flags, DB.ACCOUNT.FLAG_LIVE)
        cb.setOnCheckedChangeListener(sendCBChecked)
        addRow(resources.getString(org.runnerup.common.R.string.Live), cb)
      }
    }
    mCursors.add(c)
  }

  private fun addRow(string: String, btn: View?) {
    val table = findViewById<TableLayout>(R.id.account_table)
    val row = TableRow(this)
    row.minimumHeight = 48
    row.minimumWidth = 48
    row.setPadding(0, 12, 0, 12)
    val title = TextView(this)
    title.text = string
    title.setTextColor(resources.getColor(R.color.colorText, null))
    row.addView(title)
    if (btn != null) row.addView(btn)
    table.addView(row)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.account_menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.menu_clear_uploads -> clearUploadsButtonClick.onClick(null)
      R.id.menu_upload_workouts -> uploadButtonClick.onClick(null)
      R.id.menu_disconnect_account -> disconnectButtonClick.onClick(null)
      android.R.id.home -> return super.onOptionsItemSelected(item)
    }
    return true
  }

  private val clearUploadsButtonClick =
      View.OnClickListener {
        AlertDialog.Builder(this@AccountActivity)
            .setTitle(org.runnerup.common.R.string.Clear_uploads)
            .setMessage(org.runnerup.common.R.string.Clear_uploads_from_phone)
            .setPositiveButton(org.runnerup.common.R.string.OK) { _, _ ->
              syncManager?.clearUploadsByName(callback, mSynchronizerName)
            }
            .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ -> dialog.dismiss() }
            .show()
      }

  private val uploadButtonClick =
      View.OnClickListener {
        val intent = Intent(this@AccountActivity, UploadActivity::class.java)
        intent.putExtra("synchronizer", mSynchronizerName)
        intent.putExtra("mode", SyncManager.SyncMode.UPLOAD.name)
        startActivity(intent)
      }

  private val downloadButtonClick =
      View.OnClickListener {
        val intent = Intent(this@AccountActivity, UploadActivity::class.java)
        intent.putExtra("synchronizer", mSynchronizerName)
        intent.putExtra("mode", SyncManager.SyncMode.DOWNLOAD.name)
        startActivity(intent)
      }

  private val urlButtonClick =
      View.OnClickListener { v ->
        val intent = Intent(Intent.ACTION_VIEW).setData(Uri.parse(v.tag as String))
        try {
          startActivity(intent)
        } catch (e: Exception) {
          Log.i(javaClass.name, "No handler for file intent installed? " + e.message)
        }
      }

  private val sendCBChecked =
      CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
        val tmp = ContentValues()
        val flag = buttonView.tag
        if (flag is FileFormats.Format) {
          if (isChecked) {
            format.add(flag)
          } else {
            format.remove(flag)
            if (TextUtils.isEmpty(format.toString())) {
              format.add(flag)
              buttonView.isChecked = true
              Toast.makeText(
                      applicationContext,
                      resources.getString(org.runnerup.common.R.string.File_need_one_format),
                      Toast.LENGTH_SHORT)
                  .show()
            }
          }
          tmp.put(DB.ACCOUNT.FORMAT, format.toString())
        } else {
          when (flag as Int) {
            DB.ACCOUNT.FLAG_UPLOAD,
            DB.ACCOUNT.FLAG_LIVE -> flags = Bitfield.set(flags, flag, isChecked)
          }
          tmp.put(DB.ACCOUNT.FLAGS, flags)
        }
        val args = arrayOf(mSynchronizerName)
        mDB!!.update(DB.ACCOUNT.TABLE, tmp, "name = ?", args)
      }

  private val okButtonClick =
      View.OnClickListener {
        if (mRunnerUpLiveApiAddress != null) {
          val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
          val res = resources

          prefs
              .edit()
              .putString(
                  res.getString(R.string.pref_runneruplive_serveradress),
                  mRunnerUpLiveApiAddress!!.text.toString())
              .apply()
          mRunnerUpLiveApiAddress = null
        }
        finish()
      }

  private val disconnectButtonClick =
      View.OnClickListener {
        val items = arrayOf(getString(org.runnerup.common.R.string.Clear_uploads_from_phone))
        val selected = booleanArrayOf(true)
        AlertDialog.Builder(this@AccountActivity)
            .setTitle(org.runnerup.common.R.string.Disconnect_account)
            .setPositiveButton(org.runnerup.common.R.string.OK) { _, _ ->
              syncManager?.disableSynchronizer(disconnectCallback, mSynchronizerName, selected[0])
            }
            .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ -> dialog.dismiss() }
            .setMultiChoiceItems(items, selected) { _, arg1, arg2 -> selected[arg1] = arg2 }
            .show()
      }

  private val callback = SyncManager.Callback { _, _ -> }

  private val disconnectCallback = SyncManager.Callback { _, _ -> finish() }
}
