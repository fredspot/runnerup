package org.runnerup.features

import android.os.Bundle
import org.runnerup.R

class SettingsWorkoutFragment : SettingsFragment() {

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.settings_workout, rootKey)
  }
}
