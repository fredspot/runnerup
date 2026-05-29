package org.runnerup.features

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import org.runnerup.R

class SettingsWorkoutFragment : SettingsFragment() {

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.settings_workout, rootKey)
    findPreference<Preference>(getString(R.string.pref_cue_workouts))?.apply {
      isEnabled = true
      setOnPreferenceClickListener {
        startActivity(Intent(requireContext(), ManageWorkoutsActivity::class.java))
        true
      }
    }
  }
}
