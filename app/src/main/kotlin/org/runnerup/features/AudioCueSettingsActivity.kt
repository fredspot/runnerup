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

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.runnerup.R
import org.runnerup.ui.common.widget.WidgetUtil

class AudioCueSettingsActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WidgetUtil.addLegacyOverflowButton(window)
    setContentView(R.layout.settings_activity)

    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setTitle("Audio Cues")
      setBackgroundDrawable(ColorDrawable(getColor(R.color.backgroundPrimary)))
    }

    if (savedInstanceState == null) {
      val bundle = Bundle()
      intent.getStringExtra("name")?.let { bundle.putString("name", it) }

      supportFragmentManager
          .beginTransaction()
          .setReorderingAllowed(true)
          .replace(R.id.settings_fragment_container, AudioCueSettingsFragment::class.java, bundle)
          .commit()
    }

    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_fragment_container)) {
        v,
        windowInsets ->
      val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
      v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
      WindowInsetsCompat.CONSUMED
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  companion object {
    @JvmField val SUFFIX = "_audio_cues"
  }
}
