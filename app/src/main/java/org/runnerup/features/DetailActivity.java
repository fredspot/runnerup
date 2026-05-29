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

import static org.runnerup.BuildConfig.USING_OSMDROID;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.data.DBHelper;
import org.runnerup.data.WorkoutStepGrouper;
import org.runnerup.sync.SyncManager;
import org.runnerup.sync.Synchronizer;
import org.runnerup.sync.Synchronizer.Feature;
import org.runnerup.core.util.ActivitySummaryBinder;
import org.runnerup.core.util.Bitfield;
import org.runnerup.core.util.Formatter;
import org.runnerup.core.util.GraphWrapper;
import org.runnerup.core.util.MapWrapper;
import org.runnerup.core.util.SafeParse;
import org.runnerup.ui.common.widget.SpinnerInterface.OnSetValueListener;
import org.runnerup.ui.common.widget.TitleSpinner;
import org.runnerup.ui.common.widget.WidgetUtil;
import org.runnerup.core.workout.Intensity;
import org.runnerup.core.workout.Sport;

public class DetailActivity extends AppCompatActivity implements Constants {

  long mID = 0;
  SQLiteDatabase mDB = null;
  final DetailSyncController syncController = new DetailSyncController();
  final DetailGraphController graphController = new DetailGraphController();
  final DetailSaveModeController saveModeController = new DetailSaveModeController(this);
  final DetailMenuController menuController = new DetailMenuController(this);
  final DetailLapListController.Host lapListHost =
      new DetailLapListController.Host() {
        @Override
        public String labelForIntensity(int intensity) {
          switch (intensity) {
            case DB.INTENSITY.WARMUP:
              return getString(R.string.lap_list_warmup_total);
            case DB.INTENSITY.COOLDOWN:
              return getString(R.string.lap_list_cooldown_total);
            case DB.INTENSITY.ACTIVE:
              return intervalWorkout
                  ? getString(R.string.lap_list_interval_total)
                  : getString(R.string.lap_list_step_total);
            default:
              return getString(R.string.lap_list_step_total);
          }
        }

        @Override
        public boolean isIntervalWorkout() {
          return intervalWorkout;
        }

        @Override
        public Formatter getFormatter() {
          return formatter;
        }

        @Override
        public boolean isLapHrPresent() {
          return lapHrPresent;
        }

        @Override
        public WorkoutStepGrouper.LapDisplayEntry[] getLapDisplayEntries() {
          return lapDisplayEntries;
        }

        @Override
        public void setLapDisplayEntries(WorkoutStepGrouper.LapDisplayEntry[] entries) {
          lapDisplayEntries = entries;
        }
      };

  boolean lapHrPresent = false;
  boolean intervalWorkout = false;
  ContentValues[] laps = null;
  WorkoutStepGrouper.LapDisplayEntry[] lapDisplayEntries = null;
  final ArrayList<ContentValues> reports = new ArrayList<>();
  DetailLapListController.LapListAdapter lapListAdapter;

  private boolean uploading = false;

  Button saveButton = null;
  private Button uploadButton = null;
  Button resumeButton = null;
  private TextView activityTime = null;
  private TextView activityPace = null;
  private View activityPaceSeparator = null;
  private TextView activityDistance = null;

  TitleSpinner sport = null;
  TitleSpinner manualDistance = null;
  EditText notes = null;
  DetailInjuryController injuryController = null;
  View rootView;
  ViewPager2 detailPager;
  boolean detailTabContentBound = false;
  final DetailTabContentController tabContentController = new DetailTabContentController(this);
  final DetailRequeryController requeryController = new DetailRequeryController(this);
  private View mapTab;
  private int mapTabIndex = -1;

  MapWrapper mapWrapper = null;

  SyncManager syncManager = null;
  Formatter formatter = null;

  private long mStartTime = 0; // activity start time in unix timestamp
  ContentValues headerData = new ContentValues();
  private ActivityResultLauncher<Intent> accountListLauncher;
  private ActivityResultLauncher<Intent> configureLauncher;

  /** Called when the activity is first created. */
  @SuppressLint("ObsoleteSdkInt")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (USING_OSMDROID || BuildConfig.MAPBOX_ENABLED > 0) {
      // MapBox or Osmdroid, set mapWrapper.
      MapWrapper.start(this);
    }
    setContentView(R.layout.detail);
    rootView = findViewById(R.id.detail_view);
    setupDetailTabs();

    Toolbar toolbar = findViewById(R.id.actionbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    WidgetUtil.addLegacyOverflowButton(getWindow());

    Intent intent = getIntent();
    mID = intent.getLongExtra("ID", -1);
    String intentMode = intent.getStringExtra("mode");
    
    // Store the source tab index if provided (for navigation back)
    if (intentMode != null && intentMode.contentEquals("details")) {
      int sourceTab = intent.getIntExtra("source_tab", -1);
      if (sourceTab >= 0) {
        android.content.SharedPreferences prefs = 
            getSharedPreferences("nav_prefs", MODE_PRIVATE);
        prefs.edit().putInt("navigate_to_tab", sourceTab).apply();
      }
    }

    mDB = DBHelper.getReadableDatabase(this);
    syncManager = new SyncManager(this);
    configureLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              syncManager.handleConfigureResult(result.getResultCode(), result.getData());
              requery();
            });
    syncManager.setConfigureLauncher(configureLauncher);
    accountListLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> requery());
    formatter = new Formatter(this);

    if (intentMode.contentEquals("save")) {
      saveModeController.mode = DetailSaveModeController.MODE_SAVE;
    } else if (intentMode.contentEquals("details")) {
      saveModeController.mode = DetailSaveModeController.MODE_DETAILS;
    } else {
      if (BuildConfig.DEBUG) {
        throw new AssertionError();
      }
    }

    saveButton = findViewById(R.id.save_button);
    Button discardButton = findViewById(R.id.discard_button);
    resumeButton = findViewById(R.id.resume_button);
    uploadButton = findViewById(R.id.upload_button);
    activityTime = findViewById(R.id.activity_time);
    activityDistance = findViewById(R.id.activity_distance);
    activityPace = findViewById(R.id.activity_pace);
    activityPaceSeparator = findViewById(R.id.activity_pace_separator);

    clearUploadClick =
        syncController.createClearUploadListener(this, syncManager, mID, this::requery);
    onSendChecked =
        syncController.createSendCheckedListener(
            () -> {
              if (saveModeController.mode == DetailSaveModeController.MODE_DETAILS) {
                setUploadVisibility();
              }
            });
    uploadButton.setOnClickListener(
        syncController.createUploadListener(
            syncManager, mID, uploading -> DetailActivity.this.uploading = uploading, this::requery));
    uploadButton.setVisibility(View.GONE);

    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (uploading) {
                  // Ignore while uploading
                  return;
                }
                if (saveModeController.handleBackPressedSave()) {
                  return;
                }
                navigateToHistory();
              }
            });

    ViewCompat.setOnApplyWindowInsetsListener(
        rootView,
        new OnApplyWindowInsetsListener() {
          @NonNull
          @Override
          public WindowInsetsCompat onApplyWindowInsets(
              @NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottomPadding =
                saveButton.getVisibility() == View.VISIBLE
                        || uploadButton.getVisibility() == View.VISIBLE
                    ? insets.bottom
                    : 0;
            v.setPadding(insets.left, 0, insets.right, bottomPadding);

            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.topMargin = insets.top;
            return WindowInsetsCompat.CONSUMED;
          }
        });

    scheduleBindDetailTabContent(savedInstanceState);
  }

  private void scheduleBindDetailTabContent(Bundle savedInstanceState) {
    tabContentController.scheduleBind(savedInstanceState);
  }

  private void bindDetailTabContent(Bundle savedInstanceState) {
    tabContentController.bind(savedInstanceState);
  }

  void updateViewForSport(int sportValue) {
    if (saveModeController.edit && Sport.hasManualDistance(sportValue)) {
      manualDistance.setVisibility(View.VISIBLE);
      manualDistance.setEnabled(true);
    } else {
      manualDistance.setVisibility(View.GONE);
    }

    if (mapTab != null) {
      if (Sport.isWithoutGps(sportValue)) {
        mapTab.setVisibility(View.GONE);
      } else {
        mapTab.setVisibility(View.VISIBLE);
      }
    }
    graphController.updateForSport(sportValue);
  }

  void startUploadAfterSave() {
    uploading = true;
    syncManager.startUploading(
        (synchronizerName, status) -> {
          uploading = false;
          final Intent returnIntent = new Intent();
          int sportValue = sport.getValueInt();
          if (Sport.hasManualDistance(sportValue)) {
            returnIntent.putExtra("MANUAL_DISTANCE", headerData.getAsDouble(DB.ACTIVITY.DISTANCE));
          }
          DetailActivity.this.setResult(RESULT_OK, returnIntent);
          DetailActivity.this.finish();
        },
        syncController.pendingSynchronizers,
        mID);
  }

  private void setupDetailTabs() {
    detailPager = findViewById(R.id.detail_pager);
    TabLayout detailTabLayout = findViewById(R.id.detail_tab_layout);
    boolean hasMap = USING_OSMDROID || BuildConfig.MAPBOX_ENABLED > 0;
    java.util.ArrayList<Integer> layouts = new java.util.ArrayList<>();
    java.util.ArrayList<String> titles = new java.util.ArrayList<>();
    layouts.add(R.layout.detail_tab_overview);
    titles.add("Overview");
    layouts.add(R.layout.detail_tab_laps);
    titles.add(getString(org.runnerup.common.R.string.Laps));
    if (hasMap) {
      layouts.add(R.layout.detail_tab_map);
      titles.add(getString(org.runnerup.common.R.string.Map));
      mapTabIndex = 2;
    } else {
      mapTabIndex = -1;
    }
    layouts.add(R.layout.detail_tab_graph);
    titles.add(getString(org.runnerup.common.R.string.Graph));
    int[] layoutArr = new int[layouts.size()];
    for (int i = 0; i < layouts.size(); i++) {
      layoutArr[i] = layouts.get(i);
    }
    detailPager.setAdapter(new DetailTabAdapter(layoutArr));
    detailPager.setOffscreenPageLimit(layoutArr.length);
    String[] titleArr = titles.toArray(new String[0]);
    new TabLayoutMediator(
            detailTabLayout,
            detailPager,
            (tab, position) ->
                tab.setCustomView(
                    WidgetUtil.createHoloTabIndicator(this, titleArr[position])))
        .attach();
    if (hasMap && mapTabIndex >= 0) {
      TabLayout.Tab mapTabLayout = detailTabLayout.getTabAt(mapTabIndex);
      if (mapTabLayout != null) {
        mapTab = mapTabLayout.view;
      }
    }
  }

  void setUploadVisibility() {
    boolean enabled = !syncController.pendingSynchronizers.isEmpty();
    if (enabled) {
      uploadButton.setVisibility(View.VISIBLE);
    } else {
      uploadButton.setVisibility(View.GONE);
    }
    ViewCompat.requestApplyInsets(rootView);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.detail_menu, menu);
    saveModeController.setSaveMenuItem(menu.findItem(R.id.menu_save_activity));
    saveModeController.attachNotesChangeListener();
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == android.R.id.home) {
      if (saveModeController.mode == DetailSaveModeController.MODE_DETAILS) {
        navigateToHistory();
        return true;
      }
      return super.onOptionsItemSelected(item);
    } else if (id == R.id.menu_save_activity) {
      saveModeController.onMenuSaveSelected();
    } else if (id == R.id.menu_delete_activity) {
      menuController.onDeleteSelected();
    } else if (id == R.id.menu_edit_activity) {
      if (!saveModeController.edit) {
        saveModeController.setEdit(true);
        notes.requestFocus();
        requery();
      }
    } else if (id == R.id.menu_recompute_activity) {
      menuController.onRecomputeSelected();
    } else if (id == R.id.menu_simplify_path) {
      menuController.onSimplifyPathSelected();
    } else if (id == R.id.menu_share_activity) {
      menuController.onShareSelected();
    }

    return true;
  }

  @Override
  public void onResume() {
    super.onResume();
    if (!detailTabContentBound && detailPager != null) {
      scheduleBindDetailTabContent(null);
    }
    if (mapWrapper != null) {
      mapWrapper.onResume();
    }
    if (injuryController != null) {
      injuryController.renderIcons();
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    if (mapWrapper != null) {
      mapWrapper.onStart();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (mapWrapper != null) {
      mapWrapper.onStop();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (mapWrapper != null) {
      mapWrapper.onPause();
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    if (mapWrapper != null) {
      mapWrapper.onSaveInstanceState(outState);
    }
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    if (mapWrapper != null) {
      mapWrapper.onLowMemory();
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    DBHelper.closeDB(mDB);
    syncManager.close();
    if (mapWrapper != null) {
      mapWrapper.onDestroy();
    }
  }

  void requery() {
    requeryController.requery();
  }

  void fillHeaderData() {
    // Fields from the database (projection)
    // Must include the _id column for the adapter to work
    String[] from =
        new String[] {
          DB.ACTIVITY.START_TIME,
          DB.ACTIVITY.DISTANCE,
          DB.ACTIVITY.TIME,
          DB.ACTIVITY.COMMENT,
          DB.ACTIVITY.SPORT
        };

    Cursor c = mDB.query(DB.ACTIVITY.TABLE, from, "_id == " + mID, null, null, null, null, null);
    c.moveToFirst();
    ContentValues tmp = DBHelper.get(c);
    c.close();

    if (tmp.containsKey(DB.ACTIVITY.START_TIME)) {
      long st = tmp.getAsLong(DB.ACTIVITY.START_TIME);
      mStartTime = st;
      menuController.setStartTime(st);
      setTitle(formatter.formatDateTime(st));
    }

    if (tmp.containsKey(DB.ACTIVITY.COMMENT)) {
      notes.setText(tmp.getAsString(DB.ACTIVITY.COMMENT));
    }

    headerData = tmp;
    updateHeader(tmp, /* fromManualDistance= */false);
  }

  void updateHeader(ContentValues data, boolean fromManualDistance) {
    DetailHeaderBinder.bind(
        formatter,
        activityDistance,
        activityTime,
        activityPace,
        activityPaceSeparator,
        manualDistance,
        sport,
        data,
        fromManualDistance);
  }

  private void navigateToHistory() {
    // The source tab is already stored in SharedPreferences in onCreate
    // Just finish - let Android handle the back animation naturally
    finish();
  }

  private OnLongClickListener clearUploadClick;
  private OnCheckedChangeListener onSendChecked;

}
