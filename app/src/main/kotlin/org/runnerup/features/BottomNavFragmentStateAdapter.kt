/*
 * Copyright (C) 2025 robert.jonsson75@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.runnerup.R

/**
 * Provides the [Fragment] instances displayed in the app's main bottom navigation. This adapter is
 * used with a ViewPager2 to manage fragment states.
 */
class BottomNavFragmentStateAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {

  @DrawableRes
  private val icons: IntArray =
      intArrayOf(
          R.drawable.ic_tab_main_24dp,
          R.drawable.ic_tab_history_24dp,
          R.drawable.ic_tab_besttimes_24dp,
          R.drawable.ic_tab_statistics_24dp,
          R.drawable.ic_tab_settings_24dp,
      )

  override fun createFragment(position: Int): Fragment =
      when (position) {
        1 -> HistoryFragment()
        2 -> BestTimesFragment()
        3 -> StatisticsFragment()
        4 -> SettingsContainerFragment()
        else -> StartFragment()
      }

  override fun getItemCount(): Int = NUM_PAGES

  @DrawableRes
  fun getIcon(position: Int): Int {
    if (position < 0 || position >= icons.size) {
      throw ArrayIndexOutOfBoundsException(
          "Position $position is out of bounds of the icons array of size ${icons.size}",
      )
    }
    return icons[position]
  }

  companion object {
    private const val NUM_PAGES = 5
  }
}
