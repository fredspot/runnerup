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

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.text.DateFormat
import java.text.ParseException
import java.util.Calendar
import org.runnerup.R
import org.runnerup.common.util.Constants.DB
import org.runnerup.core.util.Formatter
import org.runnerup.core.util.SafeParse
import org.runnerup.core.util.ViewUtil
import org.runnerup.data.DBHelper
import org.runnerup.ui.common.widget.SpinnerInterface.OnSetValueListener
import org.runnerup.ui.common.widget.TitleSpinner

class ManualActivity : AppCompatActivity() {

  private lateinit var manualDate: TitleSpinner
  private lateinit var manualTime: TitleSpinner
  private lateinit var manualDistance: TitleSpinner
  private lateinit var manualDuration: TitleSpinner
  private lateinit var manualPace: TitleSpinner
  private lateinit var manualNotes: EditText

  private var mDB: SQLiteDatabase? = null
  private lateinit var formatter: Formatter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    mDB = DBHelper.getWritableDatabase(this)
    formatter = Formatter(this)

    setContentView(R.layout.manual)

    val toolbar = findViewById<Toolbar>(R.id.manual_toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    manualDate = findViewById(R.id.manual_date)
    manualTime = findViewById(R.id.manual_time)
    manualDistance = findViewById(R.id.manual_distance)
    manualDistance.setOnSetValueListener(onSetManualDistance)
    manualDuration = findViewById(R.id.manual_duration)
    manualDuration.setOnSetValueListener(onSetManualDuration)
    manualPace = findViewById(R.id.manual_pace)
    manualPace.visibility = View.GONE
    manualNotes = findViewById(R.id.manual_notes)

    ViewUtil.Insets(findViewById(R.id.tab_manual), true)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    val inflater: MenuInflater = menuInflater
    inflater.inflate(R.menu.manual_menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.menu_save -> {
        saveEntry()
        return true
      }
      android.R.id.home -> {
        finish()
        return true
      }
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onDestroy() {
    DBHelper.closeDB(mDB)
    super.onDestroy()
  }

  private fun setManualPace(distance: String, duration: String) {
    Log.e(javaClass.name, "distance: >$distance< duration: >$duration<")
    val dist = SafeParse.parseDouble(distance, 0.0)
    val seconds = SafeParse.parseSeconds(duration, 0)
    if (seconds == 0L) {
      manualPace.visibility = View.GONE
      return
    }
    manualPace.setValue(
        formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_SHORT, dist / seconds))
    manualPace.visibility = View.VISIBLE
  }

  private val onSetManualDistance =
      object : OnSetValueListener {
        override fun preSetValue(newValue: String): String {
          setManualPace(newValue, manualDuration.value.toString())
          return newValue
        }

        override fun preSetValue(newValue: Int): Int {
          return newValue
        }
      }

  private val onSetManualDuration =
      object : OnSetValueListener {
        override fun preSetValue(newValue: String): String {
          setManualPace(manualDistance.value.toString(), newValue)
          return newValue
        }

        override fun preSetValue(newValue: Int): Int {
          return newValue
        }
      }

  private fun saveEntry() {
    val save = ContentValues()
    val sport = DB.ACTIVITY.SPORT_RUNNING
    val date = manualDate.value
    val time = manualTime.value
    val distance = manualDistance.value
    val duration = manualDuration.value
    val notes = manualNotes.text.toString().trim()
    var start_time = 0L

    if (notes.isNotEmpty()) {
      save.put(DB.ACTIVITY.COMMENT, notes)
    }
    var dist = 0.0
    if (distance.isNotEmpty()) {
      dist = distance.toString().toDouble()
      save.put(DB.ACTIVITY.DISTANCE, dist)
    }
    var secs = 0L
    if (duration.isNotEmpty()) {
      secs = SafeParse.parseSeconds(duration.toString(), 0)
      save.put(DB.ACTIVITY.TIME, secs)
    }
    if (date.isNotEmpty() && time.isNotEmpty()) {
      val dfd: DateFormat = android.text.format.DateFormat.getDateFormat(this)
      val dft: DateFormat = android.text.format.DateFormat.getTimeFormat(this)
      try {
        val d = dfd.parse(date.toString())!!
        val t = dft.parse(time.toString())!!
        val cd = Calendar.getInstance()
        val ct = Calendar.getInstance()
        cd.time = d
        ct.time = t
        cd.set(Calendar.HOUR_OF_DAY, ct.get(Calendar.HOUR_OF_DAY))
        cd.set(Calendar.MINUTE, ct.get(Calendar.MINUTE))
        cd.set(Calendar.SECOND, ct.get(Calendar.SECOND))
        cd.set(Calendar.MILLISECOND, ct.get(Calendar.MILLISECOND))
        start_time = cd.time.time / 1000
      } catch (_: ParseException) {
      }
    }
    save.put(DB.ACTIVITY.START_TIME, start_time)

    save.put(DB.ACTIVITY.SPORT, sport)
    val id = mDB!!.insert(DB.ACTIVITY.TABLE, null, save)

    if (dist > 0 && secs > 0) {
      val lapDistance = 1000.0
      val lapCount = Math.ceil(dist / lapDistance).toInt()
      val timePerLap = secs.toDouble() / lapCount

      for (lapIndex in 0 until lapCount) {
        val lap = ContentValues()
        lap.put(DB.LAP.ACTIVITY, id)
        lap.put(DB.LAP.LAP, lapIndex)
        lap.put(DB.LAP.INTENSITY, DB.INTENSITY.ACTIVE)

        val lapDist =
            if (lapIndex < lapCount - 1) lapDistance else dist - lapIndex * lapDistance
        lap.put(DB.LAP.DISTANCE, lapDist)

        val lapTime =
            if (lapIndex < lapCount - 1) timePerLap.toLong()
            else secs - (timePerLap * lapIndex).toLong()
        lap.put(DB.LAP.TIME, lapTime)

        mDB!!.insert(DB.LAP.TABLE, null, lap)
      }
    } else {
      val lap = ContentValues()
      lap.put(DB.LAP.ACTIVITY, id)
      lap.put(DB.LAP.LAP, 0)
      lap.put(DB.LAP.INTENSITY, DB.INTENSITY.ACTIVE)
      lap.put(DB.LAP.TIME, secs)
      lap.put(DB.LAP.DISTANCE, dist)
      mDB!!.insert(DB.LAP.TABLE, null, lap)
    }

    finish()
  }
}
