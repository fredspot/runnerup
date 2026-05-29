/*
 * Copyright (C) 2025 robert.jonsson75@gmail.com
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

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.runnerup.R

/**
 * Hosts the application's settings hierarchy, starting with [SettingsFragment] and allowing
 * navigation to sub-preference screens like units or sensor settings. This container manages the
 * transition between these nested settings screens using its child FragmentManager and handles back
 * navigation within this hierarchy via a custom [OnBackPressedCallback], ensuring that back
 * presses navigate correctly within the settings sub-screens before propagating to the main bottom
 * nav navigation in [MainLayout].
 */
class SettingsContainerFragment :
    Fragment(R.layout.settings_activity), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

  override fun onAttach(context: Context) {
    super.onAttach(context)
    requireActivity().onBackPressedDispatcher.addCallback(requireActivity(), onBackPressed)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.backgroundPrimary))

    if (savedInstanceState == null) {
      childFragmentManager
          .beginTransaction()
          .replace(R.id.settings_fragment_container, SettingsFragment())
          .commit()
    }
  }

  override fun onResume() {
    super.onResume()
    onBackPressed.isEnabled = hasBackStackEntries()
  }

  override fun onPause() {
    super.onPause()
    onBackPressed.isEnabled = false
  }

  override fun onPreferenceStartFragment(
      caller: PreferenceFragmentCompat,
      pref: Preference,
  ): Boolean {
    val args = pref.extras
    val fragmentClassName = pref.fragment ?: return false

    val fragment =
        childFragmentManager.fragmentFactory.instantiate(
            requireContext().classLoader,
            fragmentClassName,
        )
    fragment.arguments = args

    childFragmentManager
        .beginTransaction()
        .replace(R.id.settings_fragment_container, fragment)
        .setReorderingAllowed(true)
        .addToBackStack(null)
        .commit()

    onBackPressed.isEnabled = true
    return true
  }

  private fun hasBackStackEntries(): Boolean = childFragmentManager.backStackEntryCount > 0

  private val onBackPressed =
      object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
          if (hasBackStackEntries()) {
            childFragmentManager.popBackStackImmediate()
            isEnabled = hasBackStackEntries()
          }
        }
      }
}
