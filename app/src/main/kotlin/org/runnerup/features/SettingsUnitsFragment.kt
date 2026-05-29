package org.runnerup.features

import android.os.Bundle
import org.runnerup.R

class SettingsUnitsFragment : SettingsFragment() {

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.settings_units, rootKey)
  }
}
