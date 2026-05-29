package org.runnerup.features

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.common.util.Constants.DB
import org.runnerup.data.DBHelper
import org.runnerup.ui.common.widget.TitleSpinner

class InjuryEditorActivity : AppCompatActivity(), Constants {

  companion object {
    const val EXTRA_ACTIVITY_ID = "activity_id"
    const val EXTRA_PHASE = "phase"
  }

  private var activityId: Long = 0
  private var currentPhase: Int = 0
  private var currentZone: Int = DB.TENDON.ZONE_KNEE
  private var db: SQLiteDatabase? = null
  private lateinit var phaseSpinner: TitleSpinner
  private lateinit var zoneSpinner: TitleSpinner
  private lateinit var tendonList: androidx.recyclerview.widget.RecyclerView
  private lateinit var adapter: InjuryTendonAdapter
  private val rows = ArrayList<TendonRow>()
  private val existingInjuries = HashMap<Long, Int>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.injury_editor)

    val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    db = DBHelper.getWritableDatabase(this)
    activityId = intent.getLongExtra(EXTRA_ACTIVITY_ID, -1)
    if (activityId < 0) {
      finish()
      return
    }

    var initialPhase = intent.getIntExtra(EXTRA_PHASE, DB.ACTIVITY_INJURY.PHASE_BEFORE)
    if (initialPhase < 0) initialPhase = DB.ACTIVITY_INJURY.PHASE_BEFORE
    if (initialPhase > 2) initialPhase = DB.ACTIVITY_INJURY.PHASE_AFTER
    currentPhase = initialPhase

    phaseSpinner = findViewById(R.id.phase_spinner)
    zoneSpinner = findViewById(R.id.zone_spinner)
    tendonList = findViewById(R.id.tendon_list)

    val spacingPx = (12 * resources.displayMetrics.density).toInt()
    tendonList.layoutManager = LinearLayoutManager(this)
    tendonList.addItemDecoration(TendonSpacingDecoration(spacingPx))

    setupPhaseSpinner()
    setupZoneSpinner()
    loadExistingInjuries()
    loadTendonsForZone()
    setupAdapter()
  }

  private fun setupPhaseSpinner() {
    val phases = arrayOf("Before", "During", "After")
    phaseSpinner.setArrayEntries(phases)
    phaseSpinner.setViewSelection(currentPhase)
    phaseSpinner.setViewValue(currentPhase)
    phaseSpinner.setViewOnItemSelectedListener(
        object : AdapterView.OnItemSelectedListener {
          override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
            if (position in 0..2 && position != currentPhase) {
              saveCurrentInjuries()
              currentPhase = position
              loadExistingInjuries()
              loadTendonsForZone()
              adapter.notifyDataSetChanged()
            }
          }

          override fun onNothingSelected(parent: AdapterView<*>?) {}
        },
    )
  }

  private fun setupZoneSpinner() {
    val zones = arrayOf("Knee", "Calves", "Ankle/Foot", "Hip")
    val zoneValues =
        intArrayOf(
            DB.TENDON.ZONE_KNEE,
            DB.TENDON.ZONE_CALVES,
            DB.TENDON.ZONE_ANKLE_FOOT,
            DB.TENDON.ZONE_HIP,
        )
    zoneSpinner.setArrayEntries(zones)
    var zoneIndex = 0
    for (i in zoneValues.indices) {
      if (zoneValues[i] == currentZone) {
        zoneIndex = i
        break
      }
    }
    zoneSpinner.setViewSelection(zoneIndex)
    zoneSpinner.setViewValue(zoneIndex)
    zoneSpinner.setViewOnItemSelectedListener(
        object : AdapterView.OnItemSelectedListener {
          override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
            if (position in zoneValues.indices && zoneValues[position] != currentZone) {
              saveCurrentInjuries()
              currentZone = zoneValues[position]
              loadExistingInjuries()
              loadTendonsForZone()
              adapter.notifyDataSetChanged()
            }
          }

          override fun onNothingSelected(parent: AdapterView<*>?) {}
        },
    )
  }

  private fun loadExistingInjuries() {
    existingInjuries.clear()
    val cols = arrayOf(DB.ACTIVITY_INJURY.TENDON_ID, DB.ACTIVITY_INJURY.PAIN)
    val where =
        "${DB.ACTIVITY_INJURY.ACTIVITY_ID} = ? AND ${DB.ACTIVITY_INJURY.PHASE} = ? AND ${DB.ACTIVITY_INJURY.ZONE} = ?"
    val args = arrayOf(activityId.toString(), currentPhase.toString(), currentZone.toString())
    val database = db ?: return
    database.query(DB.ACTIVITY_INJURY.TABLE, cols, where, args, null, null, null).use { cursor ->
      while (cursor.moveToNext()) {
        existingInjuries[cursor.getLong(0)] = cursor.getInt(1)
      }
    }
  }

  private fun loadTendonsForZone() {
    rows.clear()
    val cols = arrayOf(DB.PRIMARY_KEY, DB.TENDON.NAME, DB.TENDON.DESCRIPTION)
    val where = "${DB.TENDON.ZONE} = ? AND ${DB.TENDON.ACTIVE} = 1"
    val args = arrayOf(currentZone.toString())
    val database = db ?: return
    database.query(DB.TENDON.TABLE, cols, where, args, null, null, DB.TENDON.NAME).use { cursor ->
      while (cursor.moveToNext()) {
        val tendonId = cursor.getLong(0)
        rows.add(
            TendonRow(
                tendonId = tendonId,
                name = cursor.getString(1),
                description = cursor.getString(2),
                zone = currentZone,
                pain = existingInjuries.getOrDefault(tendonId, 0),
            ),
        )
      }
    }
  }

  private fun setupAdapter() {
    adapter = InjuryTendonAdapter(rows) { saveCurrentInjuries() }
    tendonList.adapter = adapter
  }

  private fun saveCurrentInjuries() {
    val database = db
    if (database == null || activityId < 0) {
      return
    }
    try {
      val where =
          "${DB.ACTIVITY_INJURY.ACTIVITY_ID} = ? AND ${DB.ACTIVITY_INJURY.PHASE} = ? AND ${DB.ACTIVITY_INJURY.ZONE} = ?"
      val args = arrayOf(activityId.toString(), currentPhase.toString(), currentZone.toString())
      database.delete(DB.ACTIVITY_INJURY.TABLE, where, args)

      for (row in rows) {
        if (row.pain > 0) {
          val values =
              ContentValues().apply {
                put(DB.ACTIVITY_INJURY.ACTIVITY_ID, activityId)
                put(DB.ACTIVITY_INJURY.PHASE, currentPhase)
                put(DB.ACTIVITY_INJURY.ZONE, currentZone)
                put(DB.ACTIVITY_INJURY.TENDON_ID, row.tendonId)
                put(DB.ACTIVITY_INJURY.PAIN, row.pain)
                put(DB.ACTIVITY_INJURY.CREATED_AT, System.currentTimeMillis() / 1000)
              }
          database.insert(DB.ACTIVITY_INJURY.TABLE, null, values)
        }
      }
    } catch (e: Exception) {
      Log.e("InjuryEditorActivity", "Error saving injuries", e)
    }
  }

  override fun onSupportNavigateUp(): Boolean {
    saveCurrentInjuries()
    finish()
    return true
  }

  override fun onPause() {
    super.onPause()
    if (db != null && !isFinishing) {
      saveCurrentInjuries()
    }
  }
}
