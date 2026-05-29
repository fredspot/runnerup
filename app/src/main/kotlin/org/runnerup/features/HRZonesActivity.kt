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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.Vector
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.core.util.HRZoneBounds
import org.runnerup.core.util.HRZones
import org.runnerup.core.util.SafeParse
import org.runnerup.core.util.ViewUtil
import org.runnerup.ui.common.widget.TitleSpinner
import org.runnerup.ui.common.widget.WidgetUtil

class HRZonesActivity : AppCompatActivity(), Constants {

  private lateinit var ageSpinner: TitleSpinner
  private lateinit var sexSpinner: TitleSpinner
  private lateinit var maxHRSpinner: TitleSpinner
  private lateinit var hrZones: HRZones
  private lateinit var hrZoneCalculator: HRZoneBounds

  private val zones = Vector<EditText>()
  private var skipSave = false

  @SuppressLint("InflateParams")
  private fun addZoneRow(inflator: LayoutInflater, root: ViewGroup, zone: Int): View {
    val row = inflator.inflate(R.layout.heartratezonerow, null) as TableRow
    val tv = row.findViewById<TextView>(R.id.zonetext)
    val lo = row.findViewById<EditText>(R.id.zonelo)
    val hi = row.findViewById<EditText>(R.id.zonehi)
    hi.keyListener = null
    hi.isEnabled = false
    val lim = hrZoneCalculator.getZoneLimits(zone) ?: return row
    tv.text =
        String.format(
            Locale.getDefault(),
            "%s %d %d%% - %d%%",
            getString(org.runnerup.common.R.string.Zone),
            zone,
            lim.first,
            lim.second)
    lo.tag = "zone${zone}lo"
    hi.tag = "zone${zone}hi"
    val zoneCount = hrZoneCalculator.zoneCount

    if (zone == zoneCount) {
      lo.setOnEditorActionListener { _, actionId, event ->
        if ((event != null && event.keyCode == KeyEvent.KEYCODE_ENTER) ||
            actionId == EditorInfo.IME_ACTION_DONE) {
          val loZone = hrZoneCalculator.zoneCount - 1 /* base 0 offset */
          var loHR = SafeParse.parseInt(lo.text.toString(), 0)
          val maxHR = SafeParse.parseInt(maxHRSpinner.value.toString(), loHR)
          if (loHR > maxHR - 1) {
            loHR = maxHR - 1
            zones[2 * loZone].setText(String.format(Locale.getDefault(), "%d", loHR))
          }
          zones[2 * loZone - 1].setText(String.format(Locale.getDefault(), "%d", loHR))
        }
        false
      }
    }

    lo.onFocusChangeListener =
        View.OnFocusChangeListener { _, hasFocus ->
          if (!hasFocus) {
            val loZone = zone - 1 /* base 0 offset */
            val prevZone = loZone - 1
            val nextZone = loZone + 1
            var loHR = SafeParse.parseInt(lo.text.toString(), 0)
            val maxHR = SafeParse.parseInt(maxHRSpinner.value.toString(), loHR)
            val zoneCountLocal = hrZoneCalculator.zoneCount
            val zoneDiff = zoneCountLocal - loZone

            if (loHR > maxHR - zoneDiff) {
              loHR = maxHR - zoneDiff
              zones[2 * loZone].setText(String.format(Locale.getDefault(), "%d", loHR))
            }

            if (nextZone < zoneCountLocal) {
              val nextLoHR = SafeParse.parseInt(zones[2 * nextZone].text.toString(), loHR)
              if (loHR >= nextLoHR) {
                loHR = nextLoHR - 1
                lo.setText(String.format(Locale.getDefault(), "%d", loHR))
              }
              if (loZone > 0) {
                zones[2 * prevZone + 1].setText(String.format(Locale.getDefault(), "%d", loHR))
              }
            }

            if (prevZone >= 0) {
              val prevLoHR = SafeParse.parseInt(zones[2 * prevZone].text.toString(), loHR)
              if (loHR <= prevLoHR) {
                loHR = prevLoHR + 1
                lo.setText(String.format(Locale.getDefault(), "%d", loHR))
              }
              zones[2 * prevZone + 1].setText(String.format(Locale.getDefault(), "%d", loHR))
            }
          }
        }

    zones.add(lo)
    zones.add(hi)

    return row
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.heartratezones)
    WidgetUtil.addLegacyOverflowButton(window)

    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setBackgroundDrawable(ContextCompat.getDrawable(this@HRZonesActivity, R.color.backgroundPrimary))
    }

    hrZones = HRZones(this)
    hrZoneCalculator = HRZoneBounds(this)
    ageSpinner = findViewById(R.id.hrz_age)
    sexSpinner = findViewById(R.id.hrz_sex)
    maxHRSpinner = findViewById(R.id.hrz_mhr)
    val zonesTable = findViewById<TableLayout>(R.id.zones_table)
    run {
      val zoneCount = hrZoneCalculator.zoneCount
      val inflator = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
      zones.clear()
      for (i in 0 until zoneCount) {
        val row = addZoneRow(inflator, zonesTable, i + 1)
        zonesTable.addView(row)
      }
    }
    ageSpinner.setOnCloseDialogListener { _, ok ->
      if (ok) {
        recomputeMaxHR()
      }
    }

    sexSpinner.setOnCloseDialogListener { _, ok ->
      if (ok) {
        recomputeMaxHR()
      }
    }

    maxHRSpinner.setOnCloseDialogListener { _, ok ->
      if (ok) {
        recomputeZones()
      }
    }

    ViewUtil.Insets(findViewById(R.id.heartratezone_view), true)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.hrzonessettings_menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
    when (item.itemId) {
      R.id.menu_hrzonessettings_clear -> clearHRSettings()
      android.R.id.home -> {
        finish()
        return true
      }
    }

    return true
  }

  override fun onBackPressed() {
    super.onBackPressed()
  }

  override fun onResume() {
    super.onResume()
    if (hrZones.isConfigured) {
      load()
    } else {
      recomputeZones()
    }
  }

  private fun load() {
    for (zone in 0 until zones.size / 2) {
      val values = hrZones.getHRValues(zone + 1)
      if (values != null) {
        val lo = zones[2 * zone /*+ 0*/]
        val hi = zones[2 * zone + 1]
        lo.setText(String.format(Locale.getDefault(), "%d", values.first))
        hi.setText(String.format(Locale.getDefault(), "%d", values.second))
        Log.e(javaClass.name, "loaded ${zone + 1} ${values.first}-${values.second}")
      }
    }
  }

  private fun recomputeMaxHR() {
    Handler(Looper.getMainLooper()).post {
      try {
        val age = SafeParse.parseInt(ageSpinner.value.toString(), 21)
        val maxHR = HRZoneBounds.computeMaxHR(age, "Male" == sexSpinner.value)
        maxHRSpinner.setValue(Integer.toString(maxHR))
        recomputeZones()
      } catch (_: NumberFormatException) {
      }
    }
  }

  private fun recomputeZones() {
    Handler(Looper.getMainLooper()).post {
      try {
        val zoneCount = hrZoneCalculator.zoneCount
        val maxHR = SafeParse.parseInt(maxHRSpinner.value.toString(), 180)
        for (i in 0 until zoneCount) {
          val zonePair = hrZoneCalculator.computeHRZone(i + 1, maxHR) ?: continue
          zones[2 * i /*+ 0*/].setText(String.format(Locale.getDefault(), "%d", zonePair.first))
          zones[2 * i + 1].setText(String.format(Locale.getDefault(), "%d", zonePair.second))
        }
      } catch (_: NumberFormatException) {
      }
    }
  }

  private fun saveHR() {
    try {
      val vals = Vector<Int>()
      System.err.print("saving: ")
      var i = 0
      while (i < zones.size) {
        vals.add(zones[i].text.toString().toInt())
        System.err.print(" ${vals.lastElement()}")
        i += 2
      }
      vals.add(zones.lastElement().text.toString().toInt())
      Log.e(javaClass.name, " ${vals.lastElement()}")
      hrZones.save(vals)
    } catch (_: Exception) {
    }
  }

  private fun clearHRSettings() {
    AlertDialog.Builder(this)
        .setTitle(org.runnerup.common.R.string.Clear_heart_rate_zone_settings)
        .setMessage(org.runnerup.common.R.string.Are_you_sure)
        .setPositiveButton(org.runnerup.common.R.string.OK) { dialog, _ ->
          ageSpinner.clear()
          sexSpinner.clear()
          maxHRSpinner.clear()
          hrZones.clear()
          dialog.dismiss()
          skipSave = true
          finish()
        }
        .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ -> dialog.dismiss() }
        .show()
  }

  override fun onPause() {
    if (!skipSave) {
      saveHR()
    }
    skipSave = false
    super.onPause()
  }

  override fun onDestroy() {
    super.onDestroy()
  }
}
