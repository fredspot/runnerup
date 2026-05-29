/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import org.runnerup.R
import org.runnerup.core.util.Formatter
import org.runnerup.core.util.GoogleApiHelper
import org.runnerup.core.util.ViewUtil

class MainLayout : AppCompatActivity() {

  private enum class UpgradeState {
    UNKNOWN,
    NEW,
    UPGRADE,
    DOWNGRADE,
    SAME,
  }

  private var pager: ViewPager2? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    setContentView(R.layout.main)

    var versionCode = 0
    var upgradeState = UpgradeState.UNKNOWN
    val pref = PreferenceManager.getDefaultSharedPreferences(this)
    val editor = pref.edit()
    try {
      val pInfo = packageManager.getPackageInfo(packageName, 0)
      versionCode = pInfo.versionCode
      val version = pref.getInt("app-version", -1)
      upgradeState =
          when {
            version == -1 -> UpgradeState.NEW
            versionCode == version -> UpgradeState.SAME
            versionCode > version -> UpgradeState.UPGRADE
            else -> UpgradeState.DOWNGRADE
          }
    } catch (e: NameNotFoundException) {
      e.printStackTrace()
    }
    editor.putInt("app-version", versionCode)
    val km = Formatter.getUseMetric(resources, pref, editor)
    if (upgradeState == UpgradeState.NEW) {
      editor.putString(
          resources.getString(R.string.pref_autolap),
          (if (km) Formatter.km_meters else Formatter.mi_meters).toString(),
      )
    }
    editor.apply()

    Log.e(javaClass.name, "app-version: $versionCode, upgradeState: $upgradeState, km: $km")

    MainLayoutPrefsBootstrap.applyDefaultValues(this)

    pager = findViewById(R.id.pager)
    val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
    MainLayoutTabs.wire(this, pager!!, tabLayout)

    MainLayoutNavigation.handleHistoryNavigationIntent(this, intent, pager!!, ::getCurrentFragment)

    if (upgradeState == UpgradeState.UPGRADE) {
      whatsNew()
    }

    MainLayoutBootstrap.installBundledAssets(this, "bundled", filesDir.path + "/..")
    MainLayoutBootstrap.importDatabaseFromIntent(this, intent.data)

    ViewUtil.Insets(findViewById(R.id.main_root), true)
    onBackPressedDispatcher.addCallback(this, onBackPressed)
    MainLayoutNavigation.runAutoComputeInBackground(this)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pager?.let { MainLayoutNavigation.handleHistoryNavigationIntent(this, intent, it, ::getCurrentFragment) }
  }

  override fun onResume() {
    super.onResume()
    val prefs = getSharedPreferences("nav_prefs", MODE_PRIVATE)
    val targetTab = prefs.getInt("navigate_to_tab", -1)
    if (targetTab in 0..4) {
      prefs.edit().remove("navigate_to_tab").apply()
      pager?.let { p ->
        if (p.adapter != null) {
          p.post {
            if (p.currentItem != targetTab) {
              p.setCurrentItem(targetTab, false)
            }
          }
        }
      }
    }
  }

  private val onBackPressed =
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          val p = pager ?: return
          if (p.currentItem != 0) {
            p.currentItem = 0
            return
          }
          val fragment = getCurrentFragment()
          if (fragment is StartFragment) {
            if (!fragment.autoStartGps && fragment.isGpsLogging()) {
              fragment.stopGps()
              fragment.updateView()
              return
            }
          }
          isEnabled = false
          Toast.makeText(
                  this@MainLayout,
                  getString(org.runnerup.common.R.string.Catch_backbuttonpress),
                  Toast.LENGTH_SHORT,
              )
              .show()
          Handler(Looper.getMainLooper()).postDelayed({ isEnabled = true }, 3 * 1000)
        }
      }

  private fun getCurrentFragment(): Fragment? {
    for (fragment in supportFragmentManager.fragments) {
      if (fragment != null && fragment.isResumed) {
        return fragment
      }
    }
    return null
  }

  private fun whatsNew() {
    val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
    @SuppressLint("InflateParams")
    val view = inflater.inflate(R.layout.whatsnew, null)
    val wv = view.findViewById<WebView>(R.id.web_view1)
    val builder =
        AlertDialog.Builder(this)
            .setTitle(org.runnerup.common.R.string.Whats_new)
            .setView(view)
            .setNegativeButton(org.runnerup.common.R.string.OK) { dialog, _ -> dialog.dismiss() }
    if (GoogleApiHelper.isGooglePlayServicesAvailable(this)) {
      builder.setPositiveButton(org.runnerup.common.R.string.Rate_RunnerUp) { _, _ ->
        try {
          val uri = Uri.parse("market://details?id=$packageName")
          startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (ex: Exception) {
          ex.printStackTrace()
        }
      }
    }
    builder.show()
    wv.loadUrl("file:///android_asset/changes.html")
  }
}
