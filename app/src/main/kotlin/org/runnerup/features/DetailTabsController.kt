/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.os.Bundle
import android.view.View
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.runnerup.BuildConfig
import org.runnerup.BuildConfig.USING_OSMDROID
import org.runnerup.R
import org.runnerup.core.util.MapWrapper
import org.runnerup.core.workout.Sport
import org.runnerup.ui.common.widget.WidgetUtil

/** Detail ViewPager2 tabs, map tab visibility, and map wrapper lifecycle for [DetailActivity]. */
internal class DetailTabsController(private val activity: DetailActivity) {

  fun onCreate() {
    if (USING_OSMDROID || BuildConfig.MAPBOX_ENABLED > 0) {
      MapWrapper.start(activity)
    }
  }

  fun setupDetailTabs() {
    activity.detailPager = activity.findViewById(R.id.detail_pager)
    val detailTabLayout = activity.findViewById<TabLayout>(R.id.detail_tab_layout)
    val hasMap = USING_OSMDROID || BuildConfig.MAPBOX_ENABLED > 0
    val layouts = ArrayList<Int>()
    val titles = ArrayList<String>()
    layouts.add(R.layout.detail_tab_overview)
    titles.add("Overview")
    layouts.add(R.layout.detail_tab_laps)
    titles.add(activity.getString(org.runnerup.common.R.string.Laps))
    if (hasMap) {
      layouts.add(R.layout.detail_tab_map)
      titles.add(activity.getString(org.runnerup.common.R.string.Map))
      activity.mapTabIndex = 2
    } else {
      activity.mapTabIndex = -1
    }
    layouts.add(R.layout.detail_tab_graph)
    titles.add(activity.getString(org.runnerup.common.R.string.Graph))
    val layoutArr = layouts.map { it.toInt() }.toIntArray()
    activity.detailPager?.apply {
      adapter = DetailTabAdapter(layoutArr)
      offscreenPageLimit = layoutArr.size
    }
    TabLayoutMediator(detailTabLayout, activity.detailPager!!) { tab, position ->
      tab.customView = WidgetUtil.createHoloTabIndicator(activity, titles[position])
    }.attach()
    if (hasMap && activity.mapTabIndex >= 0) {
      detailTabLayout.getTabAt(activity.mapTabIndex)?.view?.let { activity.mapTab = it }
    }
  }

  fun updateViewForSport(sportValue: Int) {
    if (activity.saveModeController.edit && Sport.hasManualDistance(sportValue)) {
      activity.manualDistance?.visibility = View.VISIBLE
      activity.manualDistance?.isEnabled = true
    } else {
      activity.manualDistance?.visibility = View.GONE
    }
    activity.mapTab?.visibility =
        if (Sport.isWithoutGps(sportValue)) View.GONE else View.VISIBLE
    activity.graphController.updateForSport(sportValue)
  }

  fun onStart() {
    activity.mapWrapper?.onStart()
  }

  fun onResume() {
    activity.mapWrapper?.onResume()
  }

  fun onPause() {
    activity.mapWrapper?.onPause()
  }

  fun onStop() {
    activity.mapWrapper?.onStop()
  }

  fun onSaveInstanceState(outState: Bundle) {
    activity.mapWrapper?.onSaveInstanceState(outState)
  }

  fun onLowMemory() {
    activity.mapWrapper?.onLowMemory()
  }

  fun onDestroy() {
    activity.mapWrapper?.onDestroy()
  }
}
