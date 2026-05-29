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
import android.text.TextUtils
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import org.runnerup.R

object WidgetUtil {
  @JvmStatic
  fun setEditable(editText: EditText, onoff: Boolean) {
    editText.isClickable = onoff
    editText.isFocusable = onoff
    if (onoff) {
      editText.isFocusableInTouchMode = onoff
    }
  }

  @JvmStatic
  fun createHoloTabIndicator(ctx: Context, title: String?): View {
    val txtTab = TextView(ctx)
    txtTab.text = title
    txtTab.setTextColor(ContextCompat.getColor(ctx, R.color.colorText))
    txtTab.gravity = android.view.Gravity.CENTER
    txtTab.isSingleLine = true
    txtTab.ellipsize = TextUtils.TruncateAt.END
    val drawable = AppCompatResources.getDrawable(ctx, R.drawable.tab_indicator_holo)
    ViewCompat.setBackground(txtTab, drawable)

    val h = (25 * drawable!!.intrinsicHeight) / 10
    txtTab.setPadding(0, h, 0, h)
    return txtTab
  }

  @JvmStatic
  fun addLegacyOverflowButton(window: Window) {
    if (window.peekDecorView() == null) {
      return
    }

    try {
      window.addFlags(
          WindowManager.LayoutParams::class.java.getField("FLAG_NEEDS_MENU_KEY").getInt(null))
    } catch (_: NoSuchFieldException) {
      // Ignore since this field won't exist in most versions of Android
    } catch (_: IllegalAccessException) {
    }
  }
}
