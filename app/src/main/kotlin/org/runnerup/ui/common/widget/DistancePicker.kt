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

package org.runnerup.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import org.runnerup.core.util.Formatter

class DistancePicker(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
  private var baseUnitMeters: Long = 0

  private val unitMeters: NumberPicker // e.g km or mi
  private val unitString: TextView
  private val meters: NumberPicker

  init {
    unitMeters = NumberPicker(context, attrs)
    val unitStringLayout = LinearLayout(context)
    unitStringLayout.gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
    unitStringLayout.layoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
    unitString = TextView(context)
    unitString.textSize = 25f
    unitString.minimumHeight = 48
    unitString.minimumWidth = 48
    unitStringLayout.addView(unitString)
    meters = NumberPicker(context, attrs)

    unitMeters.setDigits(3)
    unitMeters.setRange(0, 999, true)
    unitMeters.orientation = VERTICAL
    meters.setDigits(4)
    meters.orientation = VERTICAL

    orientation = HORIZONTAL
    layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    addView(unitMeters)
    addView(unitStringLayout)
    addView(meters)

    val f = Formatter(context)
    setBaseUint(f.getUnitMeters().toLong(), f.getUnitString())
  }

  private fun setBaseUint(baseUnit: Long, baseString: String?) {
    baseUnitMeters = baseUnit
    unitString.text = baseString
    meters.setRange(0, (baseUnitMeters - 1).toInt(), true)
  }

  fun getDistance(): Long {
    var ret: Long = 0
    ret += meters.value
    ret += unitMeters.value.toLong() * baseUnitMeters
    return ret
  }

  fun setDistance(s: Long) {
    var distance = s
    val h = distance / baseUnitMeters
    distance -= h * baseUnitMeters
    unitMeters.value = h.toInt()
    meters.value = distance.toInt()
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    unitMeters.isEnabled = enabled
    meters.isEnabled = enabled
  }
}
