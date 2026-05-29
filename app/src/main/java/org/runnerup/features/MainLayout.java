/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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

package org.runnerup.features;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.data.DBHelper;
import org.runnerup.core.util.FileUtil;
import org.runnerup.core.util.Formatter;
import org.runnerup.core.util.GoogleApiHelper;
import org.runnerup.core.util.ViewUtil;

public class MainLayout extends AppCompatActivity {

  private enum UpgradeState {
    UNKNOWN,
    NEW,
    UPGRADE,
    DOWNGRADE,
    SAME
  }

  private ViewPager2 pager;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    setContentView(R.layout.main);

    int versionCode = 0;
    UpgradeState upgradeState = UpgradeState.UNKNOWN;
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
    Editor editor = pref.edit();
    try {
      PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
      versionCode = pInfo.versionCode;
      int version = pref.getInt("app-version", -1);
      if (version == -1) {
        upgradeState = UpgradeState.NEW;
      } else if (versionCode == version) {
        upgradeState = UpgradeState.SAME;
      } else if (versionCode > version) {
        upgradeState = UpgradeState.UPGRADE;
      } else if (versionCode < version) {
        upgradeState = UpgradeState.DOWNGRADE;
      }
    } catch (NameNotFoundException e) {
      e.printStackTrace();
    }
    editor.putInt("app-version", versionCode);
    boolean km = Formatter.getUseMetric(getResources(), pref, editor);

    if (upgradeState == UpgradeState.NEW) {
      editor.putString(
          getResources().getString(R.string.pref_autolap),
          Double.toString(km ? Formatter.km_meters : Formatter.mi_meters));
    }
    editor.apply();

    Log.e(
        getClass().getName(),
        "app-version: " + versionCode + ", upgradeState: " + upgradeState + ", km: " + km);

    MainLayoutPrefsBootstrap.applyDefaultValues(this);

    pager = findViewById(R.id.pager);
    TabLayout tabLayout = findViewById(R.id.tab_layout);
    MainLayoutTabs.wire(this, pager, tabLayout);

    // Handle intent extras for navigation to History tab with filters
    MainLayoutNavigation.handleHistoryNavigationIntent(
        this, getIntent(), pager, this::getCurrentFragment);

    if (upgradeState == UpgradeState.UPGRADE) {
      whatsNew();
    }

    MainLayoutBootstrap.installBundledAssets(
        this, "bundled", getFilesDir().getPath() + "/..");
    MainLayoutBootstrap.importDatabaseFromIntent(this, getIntent().getData());

    // Apply system bars insets to avoid UI overlap
    ViewUtil.Insets(findViewById(R.id.main_root), true);

    // Handle back navigation
    getOnBackPressedDispatcher().addCallback(this, onBackPressed);

    // Start auto-computation of statistics and best times
    MainLayoutNavigation.runAutoComputeInBackground(this);
  }

    @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    MainLayoutNavigation.handleHistoryNavigationIntent(
        this, intent, pager, this::getCurrentFragment);
  }

  @Override
  protected void onResume() {
    super.onResume();
    // Check if we should navigate to a specific tab (set by DetailActivity when finishing)
    android.content.SharedPreferences prefs = 
        getSharedPreferences("nav_prefs", MODE_PRIVATE);
    int targetTab = prefs.getInt("navigate_to_tab", -1);
    if (targetTab >= 0 && targetTab < 5) { // Valid tab indices are 0-4
      prefs.edit().remove("navigate_to_tab").apply();
      // Navigate to the target tab after a short delay to ensure UI is ready
      if (pager != null && pager.getAdapter() != null) {
        pager.post(() -> {
          if (pager.getCurrentItem() != targetTab) {
            pager.setCurrentItem(targetTab, false);
          }
        });
      }
    }
  }

  /**
   * An {@link OnBackPressedCallback} instance that provides custom handling for back navigation
   * within the activity.
   *
   * <p>When on the first page ({@link StartFragment}), it implements a "press back again to exit"
   * behavior. Otherwise, it navigates back to the first page.
   */
  private final OnBackPressedCallback onBackPressed =
      new OnBackPressedCallback(true /* enabled */) {
        @Override
        public void handleOnBackPressed() {
          // If not on the first page, navigate back to the first page instead of exiting.
          if (pager.getCurrentItem() != 0) {
            pager.setCurrentItem(0);
            return;
          }

          // If on the first page (StartFragment) and GPS logging is active but not auto-started,
          // stop GPS instead of exiting the app.
          Fragment fragment = getCurrentFragment();

          if (fragment instanceof StartFragment startFragment) {
            if (!startFragment.getAutoStartGps() && startFragment.isGpsLogging()) {
              startFragment.stopGps();
              startFragment.updateView();
              return;
            }
          }

          // Temporarily disable this callback to allow the system to handle the next back press
          // for exiting the app.
          this.setEnabled(false);

          // If none of the above conditions were met, show the "press back again to exit" toast,
          // and re-enable the callback after a delay.
          Toast.makeText(
                  MainLayout.this,
                  getString(org.runnerup.common.R.string.Catch_backbuttonpress),
                  Toast.LENGTH_SHORT)
              .show();

          new Handler().postDelayed(() -> this.setEnabled(true), 3 * 1000);
        }
      };

  /**
   * Returns the currently resumed fragment within this activity's fragment manager.
   *
   * @return The Fragment that is currently in the {@link Lifecycle.State#RESUMED RESUMED} state, or
   *     {@code null} if no fragment is in the resumed state.
   */
  private Fragment getCurrentFragment() {
    FragmentManager fragmentManager = getSupportFragmentManager();
    List<Fragment> fragments = fragmentManager.getFragments();

    // There is currently no direct API in ViewPager2 or FragmentStateAdapter to reliably
    // retrieve the fragment at a specific position or the currently active fragment based
    // on its position alone. Instead, we iterate through all fragments managed by the
    // FragmentManager and identify the one that is currently in the RESUMED state,
    // which FragmentStateAdapter ensures is the current page's fragment.
    // See: https://issuetracker.google.com/issues/210202198#comment2
    for (Fragment fragment : fragments) {
      if (fragment != null && fragment.isResumed()) {
        return fragment;
      }
    }

    return null;
  }

  private final OnClickListener onRateClick =
      arg0 -> {
        try {
          Uri uri = Uri.parse("market://details?id=" + getPackageName());
          startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      };

  private void whatsNew() {
    LayoutInflater inflater = (LayoutInflater) getSystemService(Service.LAYOUT_INFLATER_SERVICE);
    @SuppressLint("InflateParams")
    View view = inflater.inflate(R.layout.whatsnew, null);
    WebView wv = view.findViewById(R.id.web_view1);
    AlertDialog.Builder builder =
        new AlertDialog.Builder(this)
            .setTitle(org.runnerup.common.R.string.Whats_new)
            .setView(view)
            .setNegativeButton(
                org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss());
    if (GoogleApiHelper.isGooglePlayServicesAvailable(this)) {
      builder.setPositiveButton(
          org.runnerup.common.R.string.Rate_RunnerUp, (dialog, which) -> onRateClick.onClick(null));
    }
    builder.show();
    wv.loadUrl("file:///android_asset/changes.html");
  }
}
