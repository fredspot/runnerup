/*
 * Copyright (C) 2024 RunnerUp
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

import android.app.Activity
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import org.runnerup.R
import org.runnerup.common.util.Constants.DB

/** Injury summary cards and editor launch on the activity detail screen. */
class DetailInjuryController(
    private val activity: Activity,
    private val db: SQLiteDatabase,
    private val activityIdProvider: () -> Long,
) {
  private var injuryButton: View? = null
  private var injuryCardBefore: View? = null
  private var injuryCardDuring: View? = null
  private var injuryCardAfter: View? = null
  private var injuryIconsBefore: LinearLayout? = null
  private var injuryIconsDuring: LinearLayout? = null
  private var injuryIconsAfter: LinearLayout? = null

  fun bindViews(activity: Activity) {
    injuryButton = activity.findViewById(R.id.injury_button)
    injuryCardBefore = activity.findViewById(R.id.injury_card_before)
    injuryCardDuring = activity.findViewById(R.id.injury_card_during)
    injuryCardAfter = activity.findViewById(R.id.injury_card_after)
    injuryIconsBefore = activity.findViewById(R.id.injury_icons_before)
    injuryIconsDuring = activity.findViewById(R.id.injury_icons_during)
    injuryIconsAfter = activity.findViewById(R.id.injury_icons_after)

    injuryButton?.setOnClickListener { openEditor(DB.ACTIVITY_INJURY.PHASE_BEFORE) }
    injuryCardBefore?.setOnClickListener { openEditor(DB.ACTIVITY_INJURY.PHASE_BEFORE) }
    injuryCardDuring?.setOnClickListener { openEditor(DB.ACTIVITY_INJURY.PHASE_DURING) }
    injuryCardAfter?.setOnClickListener { openEditor(DB.ACTIVITY_INJURY.PHASE_AFTER) }
  }

  fun renderIcons() {
    val iconsBefore = injuryIconsBefore ?: return
    val iconsDuring = injuryIconsDuring ?: return
    val iconsAfter = injuryIconsAfter ?: return

    iconsBefore.removeAllViews()
    iconsDuring.removeAllViews()
    iconsAfter.removeAllViews()

    val maxByPhaseZone = Array(3) { IntArray(4) { -1 } }
    val sql =
        "SELECT ${DB.ACTIVITY_INJURY.PHASE}, ${DB.ACTIVITY_INJURY.ZONE}, MAX(${DB.ACTIVITY_INJURY.PAIN})" +
            " FROM ${DB.ACTIVITY_INJURY.TABLE}" +
            " WHERE ${DB.ACTIVITY_INJURY.ACTIVITY_ID} = ?" +
            " GROUP BY ${DB.ACTIVITY_INJURY.PHASE}, ${DB.ACTIVITY_INJURY.ZONE}"

    db.rawQuery(sql, arrayOf(activityIdProvider().toString())).use { cursor ->
      while (cursor.moveToNext()) {
        val phase = cursor.getInt(0)
        val zone = cursor.getInt(1)
        val maxPain = cursor.getInt(2)
        if (phase in 0..2 && zone in 0..3) {
          maxByPhaseZone[phase][zone] = maxPain
        }
      }
    }

    addZoneIconRow(iconsBefore, maxByPhaseZone[DB.ACTIVITY_INJURY.PHASE_BEFORE])
    addZoneIconRow(iconsDuring, maxByPhaseZone[DB.ACTIVITY_INJURY.PHASE_DURING])
    addZoneIconRow(iconsAfter, maxByPhaseZone[DB.ACTIVITY_INJURY.PHASE_AFTER])
  }

  private fun openEditor(phase: Int) {
    val intent =
        Intent(activity, InjuryEditorActivity::class.java).apply {
          putExtra(InjuryEditorActivity.EXTRA_ACTIVITY_ID, activityIdProvider())
          putExtra(InjuryEditorActivity.EXTRA_PHASE, phase)
        }
    activity.startActivity(intent)
  }

  private fun addZoneIconRow(container: LinearLayout, zonePains: IntArray) {
    val icons =
        intArrayOf(
            R.drawable.ic_zone_knee_24dp,
            R.drawable.ic_zone_calves_24dp,
            R.drawable.ic_zone_ankle_foot_24dp,
            R.drawable.ic_zone_hip_24dp,
        )
    for (z in 0 until 4) {
      val pain = zonePains[z]
      if (pain < 0) continue
      val imageView = ImageView(activity)
      imageView.setBackgroundColor(Color.TRANSPARENT)
      imageView.isFocusable = false
      imageView.isClickable = false
      imageView.setImageResource(icons[z])
      imageView.setColorFilter(colorForPain(pain))
      val lp =
          LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.WRAP_CONTENT,
              LinearLayout.LayoutParams.WRAP_CONTENT,
          )
      lp.marginEnd = 16
      imageView.layoutParams = lp
      container.addView(imageView)
    }
  }

  private fun colorForPain(pain: Int): Int {
    if (pain <= 0) return 0xFFFFFFFF.toInt()
    if (pain >= 10) return 0xFF8B0000.toInt()
    val start = 0xFFFFF9C4.toInt()
    val end = 0xFF8B0000.toInt()
    val t = pain / 10f
    val sr = (start shr 16) and 0xFF
    val sg = (start shr 8) and 0xFF
    val sb = start and 0xFF
    val er = (end shr 16) and 0xFF
    val eg = (end shr 8) and 0xFF
    val eb = end and 0xFF
    val r = (sr + (er - sr) * t).toInt()
    val g = (sg + (eg - sg) * t).toInt()
    val b = (sb + (eb - sb) * t).toInt()
    return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
  }
}
