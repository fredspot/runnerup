package org.runnerup.features

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceManager
import org.runnerup.R
import org.runnerup.tracking.component.TrackerCadence
import org.runnerup.tracking.component.TrackerPressure
import org.runnerup.tracking.component.TrackerTemperature

class SettingsSensorsFragment : SettingsFragment() {

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.settings_sensors, rootKey)
    val res = resources
    val ctx = requireContext()

    if (!hasHR(ctx)) {
      preferenceManager
          .findPreference<androidx.preference.Preference>(getString(R.string.cue_configure_hrzones))
          ?.isEnabled = false
      preferenceManager
          .findPreference<androidx.preference.Preference>(
              getString(R.string.pref_battery_level_low_threshold))
          ?.isEnabled = false
      preferenceManager
          .findPreference<androidx.preference.Preference>(
              getString(R.string.pref_battery_level_high_threshold))
          ?.isEnabled = false
    }

    if (!TrackerCadence.isAvailable(ctx)) {
      findPreference<androidx.preference.Preference>(getString(R.string.pref_use_cadence_step_sensor))
          ?.isEnabled = false
    }
    if (!TrackerTemperature.isAvailable(ctx)) {
      findPreference<androidx.preference.Preference>(
              getString(R.string.pref_use_temperature_sensor))
          ?.isEnabled = false
    }
    if (!TrackerPressure.isAvailable(ctx)) {
      findPreference<androidx.preference.Preference>(getString(R.string.pref_use_pressure_sensor))
          ?.isEnabled = false
    }

    val simplifyOnSave =
        findPreference<CheckBoxPreference>(getString(R.string.pref_path_simplification_on_save))
    val simplifyOnExport =
        findPreference<CheckBoxPreference>(getString(R.string.pref_path_simplification_on_export))
    if (simplifyOnSave != null && simplifyOnExport != null) {
      if (simplifyOnSave.isChecked) {
        simplifyOnExport.isChecked = true
      }
      simplifyOnSave.setOnPreferenceChangeListener { _, newValue ->
        if (newValue as Boolean) {
          simplifyOnExport.isChecked = true
        }
        true
      }
    }

    val sharedPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(ctx)
    val autoPause = sharedPreferences.getBoolean(getString(R.string.pref_autopause_active), true)
    findPreference<androidx.preference.Preference>(getString(R.string.pref_autopause_afterseconds))
        ?.isEnabled = autoPause
    findPreference<androidx.preference.Preference>(getString(R.string.pref_autopause_minpace))
        ?.isEnabled = autoPause
  }


  companion object {
    @JvmStatic
    fun hasHR(ctx: Context): Boolean {
      val res = ctx.resources
      val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
      val btAddress = prefs.getString(res.getString(R.string.pref_bt_address), null)
      val btProviderName = prefs.getString(res.getString(R.string.pref_bt_provider), null)
      return btProviderName != null && btAddress != null
    }
  }
}
