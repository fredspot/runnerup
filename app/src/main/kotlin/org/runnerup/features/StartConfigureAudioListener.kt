/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.Intent
import org.runnerup.ui.common.widget.SpinnerInterface.OnSetValueListener

/** Opens audio cue settings when the spinner's trailing "configure" row is chosen. */
internal class StartConfigureAudioListener(
    private val fragment: StartFragment,
    private val adapter: AudioSchemeListAdapter,
) : OnSetValueListener {

  override fun preSetValue(newValue: String): String {
    if (newValue != null &&
        newValue.contentEquals(adapter.getItem(adapter.count - 1) as String)) {
      val i = Intent(fragment.requireContext(), AudioCueSettingsActivity::class.java)
      fragment.startActivity(i)
      throw IllegalArgumentException()
    }
    return newValue
  }

  override fun preSetValue(newValue: Int): Int = newValue
}
