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
import android.widget.LinearLayout

class DurationPicker(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
  private val hours: NumberPicker
  private val minutes: NumberPicker
  private val seconds: NumberPicker

  init {
    hours = NumberPicker(context, attrs)
    hours.minimumHeight = 48
    hours.minimumWidth = 48
    minutes = NumberPicker(context, attrs)
    minutes.minimumHeight = 48
    minutes.minimumWidth = 48
    seconds = NumberPicker(context, attrs)
    seconds.minimumHeight = 48
    seconds.minimumWidth = 48

    hours.orientation = VERTICAL
    minutes.orientation = VERTICAL
    seconds.orientation = VERTICAL

    orientation = HORIZONTAL
    layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    addView(hours)
    addView(minutes)
    addView(seconds)
  }

  fun getEpochTime(): Long {
    var ret: Long = 0
    ret += seconds.value
    ret += minutes.value.toLong() * 60
    ret += hours.value.toLong() * 60 * 60
    return ret
  }

  fun setEpochTime(s: Long) {
    var time = s
    val h = time / 3600
    time -= h * 3600
    val m = time / 60
    time -= m * 60
    hours.value = h.toInt()
    minutes.value = m.toInt()
    seconds.value = time.toInt()
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    hours.isEnabled = enabled
    minutes.isEnabled = enabled
    seconds.isEnabled = enabled
  }
}
