/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/** ViewPager2 + bottom tab wiring for [MainLayout]. */
object MainLayoutTabs {

  @JvmStatic
  fun wire(activity: AppCompatActivity, pager: ViewPager2, tabLayout: TabLayout) {
    val adapter = BottomNavFragmentStateAdapter(activity)
    pager.adapter = adapter
    pager.isUserInputEnabled = true
    TabLayoutMediator(tabLayout, pager, false, true) { tab, position ->
          tab.setIcon(adapter.getIcon(position))
        }
        .attach()
  }
}
