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

import android.annotation.SuppressLint
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import java.util.Locale
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.ActivitySummaryBinder
import org.runnerup.core.util.Formatter
import org.runnerup.core.util.SyncActivityItem
import org.runnerup.core.util.ViewUtil
import org.runnerup.core.workout.Sport
import org.runnerup.data.DBHelper
import org.runnerup.data.entities.ActivityEntity
import org.runnerup.sync.FileSynchronizer
import org.runnerup.sync.SyncManager
import org.runnerup.sync.Synchronizer

class UploadActivity : AppCompatActivity() {

  private var synchronizerName: String? = null
  private var syncMode = SyncManager.SyncMode.UPLOAD
  private var syncManager: SyncManager? = null
  private lateinit var uploadAdapter: UploadListAdapter

  private var db: SQLiteDatabase? = null
  private var formatter: Formatter? = null
  private val allSyncActivities = ArrayList<SyncActivityItem>()

  private var syncCount = 0
  private var actionButton: Button? = null
  private var actionButtonText: CharSequence? = null

  private var fetching = false
  private val cancelSync = StringBuffer()
  private lateinit var detailLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
  private lateinit var configureLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.upload)

    val intent = intent
    synchronizerName = intent.getStringExtra("synchronizer")
    syncMode = SyncManager.SyncMode.valueOf(intent.getStringExtra("mode")!!)

    db = DBHelper.getReadableDatabase(this)
    formatter = Formatter(this)
    syncManager = SyncManager(this)
    configureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
          syncManager?.handleConfigureResult(result.resultCode, result.data)
        }
    syncManager?.setConfigureLauncher(configureLauncher)
    detailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { fillData() }

    val recyclerView = findViewById<RecyclerView>(R.id.upload_view)
    recyclerView.layoutManager = LinearLayoutManager(this)
    uploadAdapter = UploadListAdapter()
    recyclerView.adapter = uploadAdapter

    findViewById<Button>(R.id.upload_account_set_all).setOnClickListener(setAllButtonClick)
    findViewById<Button>(R.id.upload_account_clear_all).setOnClickListener(clearAllButtonClick)

    val downloadBtn = findViewById<Button>(R.id.upload_account_download_button)
    val uploadBtn = findViewById<Button>(R.id.upload_account_button)
    if (syncMode == SyncManager.SyncMode.DOWNLOAD) {
      downloadBtn.setOnClickListener(downloadButtonClick)
      actionButton = downloadBtn
      actionButtonText = downloadBtn.text
      uploadBtn.visibility = View.GONE
    } else {
      uploadBtn.setOnClickListener(uploadButtonClick)
      actionButton = uploadBtn
      actionButtonText = uploadBtn.text
      downloadBtn.visibility = View.GONE
    }

    ViewUtil.Insets(findViewById(R.id.upload_rootview), true)

    fillData()
    val synchronizer = syncManager?.getSynchronizerByName(synchronizerName)
    val nameView = findViewById<TextView>(R.id.upload_account_list_name)
    val iconView = findViewById<ImageView>(R.id.upload_account_list_icon)
    if (synchronizer == null || synchronizer.iconId == 0) {
      iconView.visibility = View.GONE
      nameView.text = synchronizerName
      nameView.visibility = View.VISIBLE
    } else {
      iconView.visibility = View.VISIBLE
      nameView.visibility = View.GONE
      iconView.setImageDrawable(AppCompatResources.getDrawable(this, synchronizer.iconId))
    }

    onBackPressedDispatcher.addCallback(
        this,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            if (fetching) {
              cancelSync.append("1")
              return
            }
            finish()
          }
        },
    )
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(db)
    syncManager?.close()
  }

  private fun fillData() {
    val database = db ?: return
    val name = synchronizerName ?: return
    val manager = syncManager ?: return
    if (syncMode == SyncManager.SyncMode.DOWNLOAD) {
      manager.load(name)
      manager.loadActivityList(allSyncActivities, name) { _, _ ->
        filterAlreadyPresentActivities()
        requery()
      }
    } else {
      val from =
          arrayOf(
              DB.PRIMARY_KEY,
              DB.ACTIVITY.START_TIME,
              DB.ACTIVITY.DISTANCE,
              DB.ACTIVITY.TIME,
              DB.ACTIVITY.SPORT,
          )
      val args = arrayOf(name)
      val w =
          "NOT EXISTS (SELECT 1 FROM " +
              DB.EXPORT.TABLE +
              " r," +
              DB.ACCOUNT.TABLE +
              " a WHERE " +
              "r." +
              DB.EXPORT.ACTIVITY +
              " = " +
              DB.ACTIVITY.TABLE +
              "._id " +
              " AND r." +
              DB.EXPORT.ACCOUNT +
              " = a." +
              "_id" +
              " AND a." +
              DB.ACCOUNT.NAME +
              " = ?)"
      database
          .query(DB.ACTIVITY.TABLE, from, " deleted == 0 AND $w", args, null, null, "_id desc", null)
          .use { cursor ->
            allSyncActivities.clear()
            var i = 0
            val maxUpload = 10
            if (cursor.moveToFirst()) {
              do {
                val ac = ActivityEntity(cursor)
                val ai = SyncActivityItem(ac)
                if (name != FileSynchronizer.NAME && i++ >= maxUpload) {
                  ai.setSkipFlag(true)
                }
                allSyncActivities.add(ai)
              } while (cursor.moveToNext())
            }
          }
      syncCount = allSyncActivities.size
      requery()
    }
  }

  private fun filterAlreadyPresentActivities() {
    val database = db ?: return
    val from =
        arrayOf(
            DB.PRIMARY_KEY,
            DB.ACTIVITY.START_TIME,
            DB.ACTIVITY.DISTANCE,
            DB.ACTIVITY.TIME,
            DB.ACTIVITY.SPORT,
        )
    val presentActivities = ArrayList<SyncActivityItem>()
    database.query(DB.ACTIVITY.TABLE, from, " deleted = 0", null, null, null, "_id desc", null).use {
        cursor ->
      if (cursor.moveToFirst()) {
        do {
          val av = ActivityEntity(cursor)
          presentActivities.add(SyncActivityItem(av))
        } while (cursor.moveToNext())
      }
    }
    for (toDown in allSyncActivities) {
      for (present in presentActivities) {
        if (toDown.isSimilarTo(present)) {
          toDown.setPresentFlag(true)
          toDown.setSkipFlag(false)
          break
        }
      }
    }
    updateSyncCount()
  }

  private fun updateSyncCount() {
    syncCount = 0
    for (ai in allSyncActivities) {
      if (ai.synchronize(syncMode)) {
        syncCount++
      }
    }
  }

  private fun requery() {
    uploadAdapter.notifyDataSetChanged()
    val button = actionButton ?: return
    val label = actionButtonText ?: return
    if (syncCount > 0) {
      button.text = String.format(Locale.getDefault(), "%s (%d)", label, syncCount)
      button.isEnabled = true
    } else {
      button.text = label
      button.isEnabled = false
    }
  }

  private inner class UploadListAdapter : RecyclerView.Adapter<UploadListAdapter.Holder>() {
    private val inflater: LayoutInflater = LayoutInflater.from(this@UploadActivity)

    override fun getItemCount(): Int = allSyncActivities.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
      val view = inflater.inflate(R.layout.upload_row, parent, false)
      return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
      val ai = allSyncActivities[position]
      holder.activityId = ai.id
      val fmt = formatter ?: return
      holder.tvStartTime.text =
          if (ai.startTime != null) fmt.formatDateTime(ai.startTime) else ""
      val d = ai.distance
      val t = ai.duration
      if (d != null && t != null) {
        ActivitySummaryBinder.bind(
            fmt,
            holder.tvDistance,
            holder.tvTime,
            holder.tvPace,
            Formatter.Format.TXT_SHORT,
            Formatter.Format.TXT_LONG,
            d,
            t,
        )
      } else {
        holder.tvDistance.text = ""
        holder.tvTime.text = ""
        holder.tvPace.text = ""
      }
      val sport = ai.sport
      holder.tvSport.text =
          if (sport == null) {
            Sport.textOf(resources, DB.ACTIVITY.SPORT_RUNNING)
          } else {
            Sport.textOf(resources, Sport.valueOf(sport).dbValue)
          }
      holder.cb.setOnCheckedChangeListener(null)
      holder.cb.tag = position
      holder.cb.setOnCheckedChangeListener(checkedChangeClick)
      holder.cb.isChecked = !ai.skipActivity()
      holder.cb.isEnabled = ai.isRelevantForSynch(syncMode)
      if (syncMode == SyncManager.SyncMode.UPLOAD) {
        holder.itemView.setOnClickListener {
          val intent = Intent(this@UploadActivity, DetailActivity::class.java)
          intent.putExtra("ID", holder.activityId)
          intent.putExtra("mode", "details")
          detailLauncher.launch(intent)
        }
      } else {
        holder.itemView.setOnClickListener(null)
      }
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      val tvStartTime: TextView = itemView.findViewById(R.id.upload_list_start_time)
      val tvDistance: TextView = itemView.findViewById(R.id.upload_list_distance)
      val tvTime: TextView = itemView.findViewById(R.id.upload_list_time)
      val tvPace: TextView = itemView.findViewById(R.id.upload_list_pace)
      val tvSport: TextView = itemView.findViewById(R.id.upload_list_sport)
      val cb: CheckBox = itemView.findViewById(R.id.upload_list_check)
      var activityId: Long = 0
    }
  }

  private val checkedChangeClick =
      CompoundButton.OnCheckedChangeListener { button, checked ->
        val pos = button.tag as Int
        val tmp = allSyncActivities[pos]
        tmp.setSkipFlag(!checked)
        updateSyncCount()
        requery()
      }

  private val uploadButtonClick =
      View.OnClickListener {
        if (allSyncActivities.isEmpty()) return@OnClickListener
        val upload = selectedActivities
        Log.i(Constants.LOG, "Start uploading " + upload.size)
        fetching = true
        cancelSync.delete(0, cancelSync.length)
        syncManager?.syncActivities(
            SyncManager.SyncMode.UPLOAD,
            syncCallback,
            synchronizerName,
            upload,
            cancelSync,
        )
      }

  private val downloadButtonClick =
      View.OnClickListener {
        if (allSyncActivities.isEmpty()) return@OnClickListener
        val download = selectedActivities
        Log.i(Constants.LOG, "Start downloading " + download.size)
        fetching = true
        cancelSync.delete(0, cancelSync.length)
        syncManager?.syncActivities(
            SyncManager.SyncMode.DOWNLOAD,
            syncCallback,
            synchronizerName,
            download,
            cancelSync,
        )
      }

  private val selectedActivities: List<SyncActivityItem>
    get() {
      val selected = ArrayList<SyncActivityItem>()
      for (tmp in allSyncActivities) {
        if (tmp.synchronize(syncMode)) selected.add(tmp)
      }
      return selected
    }

  private val syncCallback = SyncManager.Callback { _, status ->
    fetching = false
    if (cancelSync.isNotEmpty() || status == Synchronizer.Status.CANCEL) {
      finish()
      return@Callback
    }
    if (syncMode == SyncManager.SyncMode.UPLOAD) {
      fillData()
    } else {
      filterAlreadyPresentActivities()
      requery()
    }
  }

  private val clearAllButtonClick =
      View.OnClickListener {
        for (tmp in allSyncActivities) {
          if (tmp.isRelevantForSynch(syncMode)) {
            tmp.setSkipFlag(true)
          }
        }
        updateSyncCount()
        requery()
      }

  private val setAllButtonClick =
      View.OnClickListener {
        var i = 0
        val maxUpload = 30
        val name = synchronizerName ?: return@OnClickListener
        for (ai in allSyncActivities) {
          if (ai.isRelevantForSynch(syncMode)) {
            val upload = name == FileSynchronizer.NAME || i++ < maxUpload
            ai.setSkipFlag(!upload)
          }
        }
        updateSyncCount()
        requery()
      }

}
