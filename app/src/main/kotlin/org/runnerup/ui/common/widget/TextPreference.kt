/*
 * Copyright (C) 2012 jonas.oreland@gmail.com
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
import android.util.AttributeSet
import org.runnerup.R

class TextPreference : androidx.preference.EditTextPreference {
  constructor(context: Context) : super(context)

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

  constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

  override fun onSetInitialValue(defaultValue: Any?) {
    super.onSetInitialValue(defaultValue)
    super.setSummary(super.getPersistedString(""))
    setOnBindEditTextListener { editText ->
      editText.minimumWidth = 48
      editText.minimumHeight = 48
    }

    setOnPreferenceChangeListener { preference, newValue ->
      var `val` = if (newValue is String) newValue else ""
      if (TextUtils.isEmpty(`val`)) {
        val res = context.resources
        if (preference.key == res.getString(R.string.pref_mapbox_default_style)) {
          `val` = res.getString(R.string.mapboxDefaultStyle)
          super.setText(`val`)
        } else if (preference.key == res.getString(R.string.pref_path_simplification_tolerance)) {
          `val` = res.getString(R.string.path_simplification_default_tolerance)
          super.setText(`val`)
        }
      }
      super.setSummary(`val`)
      true
    }
  }
}
