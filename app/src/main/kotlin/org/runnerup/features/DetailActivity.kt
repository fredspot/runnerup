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

package org.runnerup.features

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import org.runnerup.BuildConfig
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.Formatter
import org.runnerup.core.util.MapWrapper
import org.runnerup.data.DBHelper
import org.runnerup.data.WorkoutStepGrouper
import org.runnerup.sync.SyncManager
import org.runnerup.ui.common.widget.TitleSpinner
import org.runnerup.ui.common.widget.WidgetUtil

class DetailActivity : AppCompatActivity(), Constants {

  var mID: Long = 0
  lateinit var mDB: SQLiteDatabase
  internal val syncController = DetailSyncController()
  internal val graphController = DetailGraphController()
  internal val saveModeController = DetailSaveModeController(this)
  internal val menuController = DetailMenuController(this)
  internal val lapListHost =
      object : DetailLapListController.Host {
        override fun labelForIntensity(intensity: Int): String =
            when (intensity) {
              DB.INTENSITY.WARMUP -> getString(R.string.lap_list_warmup_total)
              DB.INTENSITY.COOLDOWN -> getString(R.string.lap_list_cooldown_total)
              DB.INTENSITY.ACTIVE ->
                  if (intervalWorkout) {
                    getString(R.string.lap_list_interval_total)
                  } else {
                    getString(R.string.lap_list_step_total)
                  }
              else -> getString(R.string.lap_list_step_total)
            }

        override fun isIntervalWorkout(): Boolean = intervalWorkout

        override fun getFormatter(): Formatter = formatter

        override fun isLapHrPresent(): Boolean = lapHrPresent

        override fun getLapDisplayEntries(): Array<WorkoutStepGrouper.LapDisplayEntry>? =
            lapDisplayEntries

        override fun setLapDisplayEntries(entries: Array<WorkoutStepGrouper.LapDisplayEntry>) {
          lapDisplayEntries = entries
        }
      }

  var lapHrPresent = false
  var intervalWorkout = false
  var laps: Array<ContentValues>? = null
  var lapDisplayEntries: Array<WorkoutStepGrouper.LapDisplayEntry>? = null
  val reports = ArrayList<ContentValues>()
  internal var lapListAdapter: DetailLapListController.LapListAdapter? = null

  var uploading = false

  lateinit var saveButton: Button
  lateinit var uploadButton: Button
  lateinit var resumeButton: Button
  lateinit var activityTime: TextView
  lateinit var activityPace: TextView
  lateinit var activityPaceSeparator: View
  lateinit var activityDistance: TextView

  var sport: TitleSpinner? = null
  var manualDistance: TitleSpinner? = null
  var notes: EditText? = null
  var injuryController: DetailInjuryController? = null
  lateinit var rootView: View
  var detailPager: ViewPager2? = null
  var detailTabContentBound = false
  internal val tabContentController = DetailTabContentController(this)
  internal val requeryController = DetailRequeryController(this)
  internal val headerController = DetailHeaderController(this)
  internal val saveFlowController = DetailSaveFlowController(this)
  internal val tabsController = DetailTabsController(this)
  var mapTab: View? = null
  var mapTabIndex = -1

  var mapWrapper: MapWrapper? = null

  lateinit var syncManager: SyncManager
  lateinit var formatter: Formatter

  var mStartTime: Long = 0
  var headerData = ContentValues()
  private lateinit var accountListLauncher: ActivityResultLauncher<Intent>
  private lateinit var configureLauncher: ActivityResultLauncher<Intent>

  private lateinit var clearUploadClick: View.OnLongClickListener
  private lateinit var onSendChecked: CompoundButton.OnCheckedChangeListener

  @SuppressLint("ObsoleteSdkInt")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    tabsController.onCreate()
    setContentView(R.layout.detail)
    rootView = findViewById(R.id.detail_view)
    tabsController.setupDetailTabs()

    val toolbar = findViewById<Toolbar>(R.id.actionbar)
    setSupportActionBar(toolbar)
    supportActionBar!!.setDisplayHomeAsUpEnabled(true)

    WidgetUtil.addLegacyOverflowButton(window)

    val intent = intent
    mID = intent.getLongExtra("ID", -1)
    val intentMode = intent.getStringExtra("mode")

    if (intentMode != null && intentMode.contentEquals("details")) {
      val sourceTab = intent.getIntExtra("source_tab", -1)
      if (sourceTab >= 0) {
        getSharedPreferences("nav_prefs", MODE_PRIVATE)
            .edit()
            .putInt("navigate_to_tab", sourceTab)
            .apply()
      }
    }

    mDB = DBHelper.getReadableDatabase(this)
    syncManager = SyncManager(this)
    configureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
          syncManager.handleConfigureResult(result.resultCode, result.data)
          requery()
        }
    syncManager.setConfigureLauncher(configureLauncher)
    accountListLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { requery() }
    formatter = Formatter(this)

    when {
      intentMode.contentEquals("save") -> {
        saveModeController.mode = DetailSaveModeController.MODE_SAVE
      }
      intentMode.contentEquals("details") -> {
        saveModeController.mode = DetailSaveModeController.MODE_DETAILS
      }
      else -> {
        if (BuildConfig.DEBUG) {
          throw AssertionError()
        }
      }
    }

    saveButton = findViewById(R.id.save_button)
    findViewById<View>(R.id.discard_button)
    resumeButton = findViewById(R.id.resume_button)
    uploadButton = findViewById(R.id.upload_button)
    activityTime = findViewById(R.id.activity_time)
    activityDistance = findViewById(R.id.activity_distance)
    activityPace = findViewById(R.id.activity_pace)
    activityPaceSeparator = findViewById(R.id.activity_pace_separator)

    clearUploadClick =
        syncController.createClearUploadListener(this, syncManager, mID) { requery() }
    onSendChecked =
        syncController.createSendCheckedListener {
          if (saveModeController.mode == DetailSaveModeController.MODE_DETAILS) {
            setUploadVisibility()
          }
        }
    uploadButton.setOnClickListener(
        syncController.createUploadListener(
            syncManager,
            mID,
            DetailSyncController.UploadingState { uploading = it },
        ) { requery() },
    )
    uploadButton.visibility = View.GONE

    onBackPressedDispatcher.addCallback(
        this,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            if (uploading) {
              return
            }
            if (saveModeController.handleBackPressedSave()) {
              return
            }
            finish()
          }
        },
    )

    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
      val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
      val bottomPadding =
          if (saveButton.visibility == View.VISIBLE || uploadButton.visibility == View.VISIBLE) {
            insets.bottom
          } else {
            0
          }
      v.setPadding(insets.left, 0, insets.right, bottomPadding)

      val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
      mlp.topMargin = insets.top
      WindowInsetsCompat.CONSUMED
    }

    tabContentController.scheduleBind(savedInstanceState)
  }

  fun updateViewForSport(sportValue: Int) {
    tabsController.updateViewForSport(sportValue)
  }

  fun startUploadAfterSave() {
    saveFlowController.startUploadAfterSave()
  }

  fun setUploadVisibility() {
    val enabled = syncController.pendingSynchronizers.isNotEmpty()
    uploadButton.visibility = if (enabled) View.VISIBLE else View.GONE
    ViewCompat.requestApplyInsets(rootView)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.detail_menu, menu)
    saveModeController.setSaveMenuItem(menu.findItem(R.id.menu_save_activity))
    saveModeController.attachNotesChangeListener()
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> {
        if (saveModeController.mode == DetailSaveModeController.MODE_DETAILS) {
          finish()
          return true
        }
        return super.onOptionsItemSelected(item)
      }
      R.id.menu_save_activity -> saveModeController.onMenuSaveSelected()
      R.id.menu_delete_activity -> menuController.onDeleteSelected()
      R.id.menu_edit_activity -> {
        if (!saveModeController.edit) {
          saveModeController.setEdit(true)
          notes?.requestFocus()
          requery()
        }
      }
      R.id.menu_recompute_activity -> menuController.onRecomputeSelected()
      R.id.menu_simplify_path -> menuController.onSimplifyPathSelected()
      R.id.menu_share_activity -> menuController.onShareSelected()
    }

    return true
  }

  override fun onResume() {
    super.onResume()
    if (!detailTabContentBound && detailPager != null) {
      tabContentController.scheduleBind(null)
    }
    tabsController.onResume()
    injuryController?.renderIcons()
  }

  override fun onStart() {
    super.onStart()
    tabsController.onStart()
  }

  override fun onStop() {
    super.onStop()
    tabsController.onStop()
  }

  override fun onPause() {
    super.onPause()
    tabsController.onPause()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    tabsController.onSaveInstanceState(outState)
  }

  override fun onLowMemory() {
    super.onLowMemory()
    tabsController.onLowMemory()
  }

  override fun onDestroy() {
    super.onDestroy()
    DBHelper.closeDB(mDB)
    syncManager.close()
    tabsController.onDestroy()
  }

  fun requery() {
    requeryController.requery()
  }

  fun fillHeaderData() {
    headerController.fillHeaderData()
  }

  fun updateHeader(data: ContentValues, fromManualDistance: Boolean) {
    headerController.updateHeader(data, fromManualDistance)
  }
}
