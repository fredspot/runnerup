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
import com.google.android.material.tabs.TabLayoutMediator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.data.ActivityCleaner;
import org.runnerup.analytics.AutoComputeRunner;
import org.runnerup.data.ComputationTracker;
import org.runnerup.data.DBHelper;
import org.runnerup.analytics.MonthlyComparisonCalculator;
import org.runnerup.core.util.BgTasks;
import org.runnerup.core.util.FileUtil;
import org.runnerup.core.util.Formatter;
import org.runnerup.core.util.HRZones;
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

  /** Set from background task when a full activity recompute just finished (for UI feedback). */
  private volatile boolean autoComputeBulkRecomputeJustRan = false;

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

    // clear basicTargetType between application startup/shutdown
    pref.edit().remove(getString(R.string.pref_basic_target_type)).apply();

    Log.e(
        getClass().getName(),
        "app-version: " + versionCode + ", upgradeState: " + upgradeState + ", km: " + km);

    // Migration in 1.56: convert pref_mute to pref_mute_bool
    Resources res = getResources();
    try {
      if (pref.contains(res.getString(R.string.pref_mute))) {
        String v = pref.getString(res.getString(R.string.pref_mute), "no");
        editor.putBoolean(res.getString(R.string.pref_mute_bool), v.equalsIgnoreCase("yes"));
        editor.remove(res.getString(R.string.pref_mute));
        editor.apply();
      }
    } catch (Exception e) {
    }

    PreferenceManager.setDefaultValues(this, R.xml.settings, false);
    PreferenceManager.setDefaultValues(this, R.xml.audio_cue_settings, true);
    PreferenceManager.setDefaultValues(this, R.xml.settings_runtime_defaults, true);
    PreferenceManager.setDefaultValues(this, R.xml.settings_maintenance, true);
    PreferenceManager.setDefaultValues(this, R.xml.settings_sensors, true);
    PreferenceManager.setDefaultValues(this, R.xml.settings_units, true);
    PreferenceManager.setDefaultValues(this, R.xml.settings_workout, true);

    // Set up the ViewPager2 and associate it with the adapter responsible
    // for managing the lifecycle and displaying the different fragment pages.
    pager = findViewById(R.id.pager);
    BottomNavFragmentStateAdapter adapter = new BottomNavFragmentStateAdapter(this);
    pager.setAdapter(adapter);

    // Allows swiping between tabs
    pager.setUserInputEnabled(true);

    // Attach the TabLayout to the ViewPager2 using a TabLayoutMediator.
    // The mediator synchronizes the selected tab with the displayed page in the ViewPager2,
    // and allows for configuring the appearance of each tab (e.g., setting icons/titles).
    TabLayout tabLayout = findViewById(R.id.tab_layout);
    new TabLayoutMediator(
            tabLayout,
            pager,
            false,
            true, // Uses animation when switching tabs
            (tab, position) -> tab.setIcon(adapter.getIcon(position)))
        .attach();

    // Handle intent extras for navigation to History tab with filters
    handleHistoryNavigationIntent();

    if (upgradeState == UpgradeState.UPGRADE) {
      whatsNew();
    }

    // Import workouts/schemes. No permission needed
    handleBundled(getApplicationContext().getAssets(), "bundled", getFilesDir().getPath() + "/..");

    // if we were called from an intent-filter because user opened "runnerup.db.export", load it
    final String filePath;
    final Uri data = getIntent().getData();
    if (data != null) {
      if ("content".equals(data.getScheme())) {
        Cursor cursor =
            this.getContentResolver()
                .query(
                    data,
                    new String[] {android.provider.MediaStore.Images.ImageColumns.DATA},
                    null,
                    null,
                    null);
        cursor.moveToFirst();
        filePath = cursor.getString(0);
        cursor.close();
      } else {
        filePath = data.getPath();
      }
    } else {
      filePath = null;
    }

    if (filePath != null) {
      // No check for permissions or that this is within scooped storage (>=SDK29)
      Log.i(getClass().getSimpleName(), "Importing database from " + filePath);
      DBHelper.importDatabase(MainLayout.this, filePath);
    }

    // Apply system bars insets to avoid UI overlap
    ViewUtil.Insets(findViewById(R.id.main_root), true);

    // Handle back navigation
    getOnBackPressedDispatcher().addCallback(this, onBackPressed);

    // Start auto-computation of statistics and best times
    runAutoComputeInBackground();
  }

    @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    handleHistoryNavigationIntent();
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

  private void handleHistoryNavigationIntent() {
    Intent intent = getIntent();
    if (intent != null && pager != null) {
      // Check for navigate_to_tab extra (from DetailActivity)
      if (intent.hasExtra("navigate_to_tab")) {
        int tabIndex = intent.getIntExtra("navigate_to_tab", -1);
        if (tabIndex >= 0 && pager.getAdapter() != null && tabIndex < pager.getAdapter().getItemCount()) {
          pager.post(() -> {
            pager.setCurrentItem(tabIndex, false);
          });
        }
      }
      // Check for HISTORY_TAB with filters
      else if (intent.getBooleanExtra("HISTORY_TAB", false)) {
        int filterYear = intent.getIntExtra("FILTER_YEAR", -1);
        int filterMonth = intent.getIntExtra("FILTER_MONTH", -1);
        
        if (filterYear != -1 && filterMonth != -1) {
          // Navigate to History tab (position 1)
          pager.post(() -> {
            pager.setCurrentItem(1, false);
            
            // Wait for fragment to be ready, then apply filter
            pager.postDelayed(() -> {
              Fragment fragment = getCurrentFragment();
              if (fragment instanceof HistoryFragment) {
                HistoryFragment historyFragment = (HistoryFragment) fragment;
                historyFragment.applyFilter(filterYear, filterMonth);
              }
            }, 100);
          });
        }
      }
    }
  }

  private void handleBundled(AssetManager mgr, String srcBase, String dstBase) {
    String[] list;

    try {
      list = mgr.list(srcBase);
    } catch (IOException e) {
      e.printStackTrace();
      list = null;
    }
    if (list != null) {
      for (String add : list) {
        boolean isFile = false;

        String src = srcBase + File.separator + add;
        String dst = dstBase + File.separator + add;
        try {
          InputStream is = mgr.open(src);
          is.close();
          isFile = true;
        } catch (Exception ex) {
          // Normal, src is directory for first call
        }

        Log.v(getClass().getName(), "Found: " + src + ", " + dst + ", isFile: " + isFile);

        if (!isFile) {
          // The request is hierarchical, source is still on a directory level
          File dstDir = new File(dstBase);
          //noinspection ResultOfMethodCallIgnored
          dstDir.mkdir();
          if (!dstDir.isDirectory()) {
            Log.w(
                getClass().getName(),
                "Failed to copy " + src + " as \"" + dstBase + "\" is not a directory!");
            continue;
          }
          handleBundled(mgr, src, dst);
        } else {
          // Source is a file, ready to copy
          File dstFile = new File(dst);
          if (dstFile.isDirectory() || dstFile.isFile()) {
            Log.v(
                getClass().getName(),
                "Skip: "
                    + dst
                    + ", isDirectory(): "
                    + dstFile.isDirectory()
                    + ", isFile(): "
                    + dstFile.isFile());
            continue;
          }

          // Only copy if the key do not exist already
          String key = "install_bundled_" + add;
          SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
          if (pref.contains(key)) {
            Log.v(getClass().getName(), "Skip already existing pref: " + key);
            continue;
          }

          pref.edit().putBoolean(key, true).apply();

          Log.v(getClass().getName(), "Copying: " + dst);
          InputStream input = null;
          try {
            input = mgr.open(src);
            FileUtil.copy(input, dst);
            handleHooks(add);
          } catch (IOException e) {
            e.printStackTrace();
          } finally {
            FileUtil.close(input);
          }
        }
      }
    }
  }

  private void handleHooks(String key) {
    if (key.contains("_audio_cues.xml")) {
      String name = key.substring(0, key.indexOf("_audio_cues.xml"));

      SQLiteDatabase mDB = DBHelper.getWritableDatabase(this);

      ContentValues tmp = new ContentValues();
      tmp.put(DB.AUDIO_SCHEMES.NAME, name);
      tmp.put(DB.AUDIO_SCHEMES.SORT_ORDER, 0);
      mDB.insert(DB.AUDIO_SCHEMES.TABLE, null, tmp);

      DBHelper.closeDB(mDB);
    }
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

  private static final String PREF_ACTIVITY_BULK_STAMP = "activity_bulk_recompute_stamp";
  private static final int BULK_RECOMPUTE_DATA_REVISION = 2;

  private void runAutoComputeInBackground() {
    BgTasks.run(
        () -> {
          autoComputeBulkRecomputeJustRan = false;
          SQLiteDatabase db = null;
          try {
            db = DBHelper.getWritableDatabase(MainLayout.this);
            SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(MainLayout.this);
            long requiredStamp =
                (long) BuildConfig.VERSION_CODE * 1000L + BULK_RECOMPUTE_DATA_REVISION;
            long appliedStamp = prefs.getLong(PREF_ACTIVITY_BULK_STAMP, 0L);
            if (appliedStamp < requiredStamp) {
              Log.i(
                  getClass().getSimpleName(),
                  "Bulk activity recompute (apk "
                      + BuildConfig.VERSION_CODE
                      + ", revision "
                      + BULK_RECOMPUTE_DATA_REVISION
                      + ")");
              ActivityCleaner cleaner = new ActivityCleaner();
              int n = 0;
              try (Cursor c =
                  db.query(
                      DB.ACTIVITY.TABLE,
                      new String[] {DB.PRIMARY_KEY},
                      DB.ACTIVITY.DELETED + " = ?",
                      new String[] {"0"},
                      null,
                      null,
                      DB.PRIMARY_KEY + " ASC")) {
                while (c.moveToNext()) {
                  long id = c.getLong(0);
                  try {
                    cleaner.recompute(db, id);
                    n++;
                  } catch (Exception e) {
                    Log.w(getClass().getSimpleName(), "Recompute failed for activity " + id, e);
                  }
                }
              }
              ComputationTracker.deleteTracking(
                  db, ComputationTracker.TYPE_BEST_TIMES, ComputationTracker.TYPE_STATISTICS);
              prefs.edit().putLong(PREF_ACTIVITY_BULK_STAMP, requiredStamp).apply();
              autoComputeBulkRecomputeJustRan = true;
            }
            AutoComputeRunner.runAll(db, getMonthlyComparisonZoneBounds(MainLayout.this));
          } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "Error during auto-computation: " + e.getMessage(), e);
          } finally {
            if (db != null) {
              DBHelper.closeDB(db);
            }
          }
        },
        () -> {
          if (autoComputeBulkRecomputeJustRan) {
            Toast.makeText(
                    MainLayout.this,
                    R.string.activity_bulk_recompute_done,
                    Toast.LENGTH_LONG)
                .show();
          }
        });
  }

  static int[] getMonthlyComparisonZoneBounds(android.content.Context context) {
    return MonthlyComparisonCalculator.resolveZoneBounds(new HRZones(context));
  }
}
