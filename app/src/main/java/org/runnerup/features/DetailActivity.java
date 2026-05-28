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
import static org.runnerup.core.content.ActivityProvider.GPX_MIME;
import static org.runnerup.core.content.ActivityProvider.TCX_MIME;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
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
import android.widget.ListView;
import android.widget.TextView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import org.runnerup.core.content.ActivityProvider;
import org.runnerup.data.ActivityCleaner;
import org.runnerup.data.DBHelper;
import org.runnerup.data.WorkoutStepGrouper;
import org.runnerup.data.PathSimplifier;
import org.runnerup.sync.SyncManager;
import org.runnerup.sync.Synchronizer;
import org.runnerup.sync.Synchronizer.Feature;
import org.runnerup.core.util.ActivitySummaryBinder;
import org.runnerup.core.util.Bitfield;
import org.runnerup.core.util.FileNameHelper;
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

  private long mID = 0;
  private SQLiteDatabase mDB = null;
  private final DetailSyncController syncController = new DetailSyncController();
  private final DetailGraphController graphController = new DetailGraphController();
  private final DetailLapListController.Host lapListHost =
      new DetailLapListController.Host() {
        @Override
        public String labelForIntensity(int intensity) {
          switch (intensity) {
            case DB.INTENSITY.WARMUP:
              return getString(R.string.lap_list_warmup_total);
            case DB.INTENSITY.COOLDOWN:
              return getString(R.string.lap_list_cooldown_total);
            case DB.INTENSITY.ACTIVE:
              return getString(R.string.lap_list_interval_total);
            default:
              return getString(R.string.lap_list_step_total);
          }
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

  private boolean lapHrPresent = false;
  private ContentValues[] laps = null;
  private WorkoutStepGrouper.LapDisplayEntry[] lapDisplayEntries = null;
  private final ArrayList<ContentValues> reports = new ArrayList<>();
  private final ArrayList<BaseAdapter> adapters = new ArrayList<>(2);

  private int mode; // 0 == save 1 == details
  private static final int MODE_SAVE = 0;
  private static final int MODE_DETAILS = 1;
  private boolean edit = false;
  private boolean uploading = false;
  private boolean hasUnsavedChanges = false;
  private MenuItem saveMenuItem = null;

  private Button saveButton = null;
  private Button uploadButton = null;
  private Button resumeButton = null;
  private TextView activityTime = null;
  private TextView activityPace = null;
  private View activityPaceSeparator = null;
  private TextView activityDistance = null;

  private TitleSpinner sport = null;
  private TitleSpinner manualDistance = null;
  private EditText notes = null;
  private DetailInjuryController injuryController = null;
  private View rootView;
  private ViewPager2 detailPager;
  private boolean detailTabContentBound = false;
  private View mapTab;
  private int mapTabIndex = -1;

  private MapWrapper mapWrapper = null;

  private SyncManager syncManager = null;
  private Formatter formatter = null;

  private long mStartTime = 0; // activity start time in unix timestamp
  private ContentValues headerData = new ContentValues();
  private static final int EDIT_ACCOUNT_REQUEST = 2;

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
    formatter = new Formatter(this);

    if (intentMode.contentEquals("save")) {
      this.mode = MODE_SAVE;
    } else if (intentMode.contentEquals("details")) {
      this.mode = MODE_DETAILS;
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

    saveButton.setOnClickListener(saveButtonClick);
    clearUploadClick =
        syncController.createClearUploadListener(this, syncManager, mID, this::requery);
    onSendChecked =
        syncController.createSendCheckedListener(
            () -> {
              if (mode == MODE_DETAILS) {
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
                if (mode == MODE_SAVE) {
                  // When finishing a run, save it and navigate back to Run page (tab 0)
                  // Save the activity first (don't discard it)
                  saveActivity();
                  
                  // Prepare return intent with manual distance if applicable
                  Intent returnIntent = new Intent();
                  int sportValue = sport.getValueInt();
                  if (Sport.hasManualDistance(sportValue)) {
                    returnIntent.putExtra(
                        "MANUAL_DISTANCE", headerData.getAsDouble(DB.ACTIVITY.DISTANCE));
                  }
                  
                  // Set navigation target to Run page (tab 0)
                  android.content.SharedPreferences prefs = 
                      getSharedPreferences("nav_prefs", MODE_PRIVATE);
                  prefs.edit().putInt("navigate_to_tab", 0).apply(); // Run tab is at position 0
                  
                  // Return RESULT_OK to save the run (not RESULT_CANCELED which discards it)
                  setResult(RESULT_OK, returnIntent);
                  finish();
                } else {
                  // When in details mode, navigate back to History tab
                  navigateToHistory();
                }
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
    if (detailPager == null) {
      return;
    }
    detailPager
        .getViewTreeObserver()
        .addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
              @Override
              public void onGlobalLayout() {
                if (detailTabContentBound || isFinishing()) {
                  removeListener();
                  return;
                }
                if (findViewById(R.id.summary_sport) != null) {
                  removeListener();
                  bindDetailTabContent(savedInstanceState);
                }
              }

              private void removeListener() {
                ViewTreeObserver observer = detailPager.getViewTreeObserver();
                if (observer.isAlive()) {
                  observer.removeOnGlobalLayoutListener(this);
                }
              }
            });
  }

  private void bindDetailTabContent(Bundle savedInstanceState) {
    if (detailTabContentBound || isFinishing()) {
      return;
    }
    sport = findViewById(R.id.summary_sport);
    manualDistance = findViewById(R.id.summary_manual_distance);
    notes = findViewById(R.id.notes_text);
    ListView lapList = findViewById(R.id.laplist);
    if (sport == null || manualDistance == null || notes == null || lapList == null) {
      return;
    }
    detailTabContentBound = true;

    sport.setOnSetValueListener(
        new OnSetValueListener() {
          @Override
          public String preSetValue(String newValue) throws IllegalArgumentException {
            return newValue;
          }

          @Override
          public int preSetValue(int newValue) throws IllegalArgumentException {
            updateViewForSport(newValue);
            ViewCompat.requestApplyInsets(rootView);
            headerData.put(DB.ACTIVITY.SPORT, newValue);
            return newValue;
          }
        });
    sport.setArrayEntries(Sport.getStringArray(getResources()));

    manualDistance.setOnSetValueListener(
        new OnSetValueListener() {
          @Override
          public String preSetValue(String newValue) throws IllegalArgumentException {
            double dist = SafeParse.parseDouble(newValue, 0);
            headerData.put(DB.ACTIVITY.DISTANCE, dist);
            updateHeader(headerData, /* fromManualDistance= */true);
            return newValue;
          }

          @Override
          public int preSetValue(int newValue) throws IllegalArgumentException {
            return newValue;
          }
        });
    injuryController = new DetailInjuryController(this, mDB, () -> mID);
    injuryController.bindViews(this);

    if (USING_OSMDROID || BuildConfig.MAPBOX_ENABLED > 0) {
      Object mapView = findViewById(R.id.mapview);
      if (mapView != null) {
        mapWrapper = new MapWrapper(this, mDB, mID, formatter, mapView);
        mapWrapper.onCreate(savedInstanceState);
      }
    }

    fillHeaderData();
    requery();

    BaseAdapter lapAdapter = DetailLapListController.createAdapter(this, lapListHost);
    adapters.add(lapAdapter);
    lapList.setAdapter(lapAdapter);

    LinearLayout graphTabLayout = findViewById(R.id.tab_graph);
    LinearLayout hrzonesBarLayout = findViewById(R.id.hrzonesBarLayout);
    if (graphTabLayout != null && hrzonesBarLayout != null) {
      graphController.attach(
          this, graphTabLayout, hrzonesBarLayout, formatter, mDB, mID, sport.getValueInt());
    }

    Button discardButton = findViewById(R.id.discard_button);
    if (mode == MODE_SAVE) {
      View buttonsLayout = findViewById(R.id.buttons);
      buttonsLayout.setVisibility(View.GONE);
      resumeButton.setOnClickListener(resumeButtonClick);
      discardButton.setOnClickListener(discardButtonClick);
      setEdit(true);
      autoSaveActivity();
    } else if (mode == MODE_DETAILS) {
      resumeButton.setVisibility(View.GONE);
      discardButton.setVisibility(View.GONE);
      setEdit(false);
    }

    injuryController.renderIcons();
  }
  
  private void autoSaveActivity() {
    // Auto-save the activity when screen loads
    saveActivity();
    
    // Set result to indicate activity was saved
    final Intent returnIntent = new Intent();
    int sportValue = sport.getValueInt();
    if (Sport.hasManualDistance(sportValue)) {
      returnIntent.putExtra("MANUAL_DISTANCE", headerData.getAsDouble(DB.ACTIVITY.DISTANCE));
    }
    setResult(RESULT_OK, returnIntent);
    
    // Track activity is already saved
    hasUnsavedChanges = false;
    updateSaveMenuVisibility();
  }

  private void setEdit(boolean value) {
    edit = value;
    if (value) {
      saveButton.setVisibility(View.VISIBLE);
    } else {
      saveButton.setVisibility(View.GONE);
    }
    WidgetUtil.setEditable(notes, value);
    sport.setEnabled(value);
    updateViewForSport(sport.getValueInt());
    ViewCompat.requestApplyInsets(rootView);
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
            detailTabLayout, detailPager, (tab, position) -> tab.setText(titleArr[position]))
        .attach();
    if (hasMap && mapTabIndex >= 0) {
      TabLayout.Tab mapTabLayout = detailTabLayout.getTabAt(mapTabIndex);
      if (mapTabLayout != null) {
        mapTab = mapTabLayout.view;
      }
    }
  }

  private void updateViewForSport(int sportValue) {
    if (edit && Sport.hasManualDistance(sportValue)) {
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

  private void setUploadVisibility() {
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
    saveMenuItem = menu.findItem(R.id.menu_save_activity);
    updateSaveMenuVisibility();
    
    // Add text change listener to notes field
    if (notes != null && mode == MODE_SAVE) {
      notes.addTextChangedListener(new android.text.TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
          markAsUnsaved();
        }
        
        @Override
        public void afterTextChanged(android.text.Editable s) {}
      });
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == android.R.id.home) {
      if (mode == MODE_DETAILS) {
        // When in details mode, navigate back to History tab
        navigateToHistory();
        return true;
      }
      return super.onOptionsItemSelected(item);
    } else if (id == R.id.menu_save_activity) {
      saveActivity();
      hasUnsavedChanges = false;
      updateSaveMenuVisibility();
    } else if (id == R.id.menu_delete_activity) {
deleteButtonClick.onClick(null);
    } else if (id == R.id.menu_edit_activity) {
      if (!edit) {
        setEdit(true);
        notes.requestFocus();
        requery();
      }
    } else if (id == R.id.menu_recompute_activity) {
      new AlertDialog.Builder(this)
          .setTitle(org.runnerup.common.R.string.Recompute_activity)
          .setMessage(org.runnerup.common.R.string.Are_you_sure)
          .setPositiveButton(
              org.runnerup.common.R.string.Yes,
              (dialog, which) -> {
                dialog.dismiss();
                // Force-recompute from raw GPS rows: overwrite saved DISTANCE/TIME for every lap
                // unless GPS yields zero. This is the user explicitly asking us to trust the raw
                // data over whatever the workout / older cleaner had stored.
                new ActivityCleaner().recompute(mDB, mID, true);
                requery();
                fillHeaderData();
                finish();
              })
          .setNegativeButton(org.runnerup.common.R.string.No, (dialog, which) -> dialog.dismiss())
          .show();
    } else if (id == R.id.menu_simplify_path) {
      new AlertDialog.Builder(this)
          .setTitle(org.runnerup.common.R.string.path_simplification_menu)
          .setMessage(org.runnerup.common.R.string.Are_you_sure)
          .setPositiveButton(
              org.runnerup.common.R.string.Yes,
              (dialog, which) -> {
                dialog.dismiss();
                PathSimplifier simplifier = new PathSimplifier(this);
                ArrayList<String> ids = simplifier.getNoisyLocationIDsAsStrings(mDB, mID);
                ActivityCleaner.deleteLocations(mDB, ids);
                new ActivityCleaner().recompute(mDB, mID);
                requery();
                fillHeaderData();
                finish();
              })
          .setNegativeButton(org.runnerup.common.R.string.No, (dialog, which) -> dialog.dismiss())
          .show();
    } else if (id == R.id.menu_share_activity) {
      shareActivity();
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

  private void requery() {
    {
      /*
       * Laps
       */
      String[] from =
          new String[] {
            "_id",
            DB.LAP.LAP,
            DB.LAP.INTENSITY,
            DB.LAP.TIME,
            DB.LAP.DISTANCE,
            DB.LAP.PLANNED_TIME,
            DB.LAP.PLANNED_DISTANCE,
            DB.LAP.PLANNED_PACE,
            DB.LAP.AVG_HR,
            DB.LAP.STEP
          };

      Cursor c =
          mDB.query(
              DB.LAP.TABLE, from, DB.LAP.ACTIVITY + " == " + mID, null, null, null, "_id", null);

      laps = DBHelper.toArray(c);
      c.close();
      lapHrPresent = false;
      for (ContentValues v : laps) {
        if (v.containsKey(DB.LAP.AVG_HR) && v.getAsInteger(DB.LAP.AVG_HR) > 0) {
          lapHrPresent = true;
          break;
        }
      }
      buildLapDisplayEntries();
    }

    {
      /*
       * Accounts/reports
       */
      String sql =
          "SELECT DISTINCT "
              + "  acc._id, "
              + ("  acc." + DB.ACCOUNT.NAME + ", ")
              + ("  acc." + DB.ACCOUNT.FLAGS + ", ")
              + ("  acc." + DB.ACCOUNT.AUTH_CONFIG + ", ")
              + ("  acc." + DB.ACCOUNT.FORMAT + ", ")
              + ("  rep._id as repid, ")
              + ("  rep." + DB.EXPORT.ACCOUNT + ", ")
              + ("  rep." + DB.EXPORT.ACTIVITY + ", ")
              + ("  rep." + DB.EXPORT.EXTERNAL_ID + ", ")
              + ("  rep." + DB.EXPORT.STATUS)
              + (" FROM " + DB.ACCOUNT.TABLE + " acc ")
              + (" LEFT OUTER JOIN " + DB.EXPORT.TABLE + " rep ")
              + (" ON ( acc._id = rep." + DB.EXPORT.ACCOUNT)
              + ("     AND rep." + DB.EXPORT.ACTIVITY + " = " + mID + " )");

      Cursor c = mDB.rawQuery(sql, null);
      syncController.alreadySynched.clear();
      syncController.synchedExternalId.clear();
      syncController.pendingSynchronizers.clear();
      reports.clear();
      if (c.moveToFirst()) {
        do {
          ContentValues tmp = DBHelper.get(c);
          Synchronizer synchronizer = syncManager.add(tmp);
          // Note: Show all configured accounts (also those are not currently enabled)
          // Uploaded but removed accounts are not displayed
          if (synchronizer == null
              || !synchronizer.checkSupport(Feature.UPLOAD)
              || !synchronizer.isConfigured()) {
            continue;
          }

          String name = tmp.getAsString(DB.ACCOUNT.NAME);
          reports.add(tmp);
          if (tmp.containsKey("repid")) {
            syncController.alreadySynched.add(name);
            if (tmp.containsKey(DB.EXPORT.STATUS)
                && tmp.getAsInteger(DB.EXPORT.STATUS)
                    == Synchronizer.ExternalIdStatus.getInt(Synchronizer.ExternalIdStatus.OK)) {
              String url =
                  syncManager
                      .getSynchronizerByName(name)
                      .getActivityUrl(syncController.synchedExternalId.get(name));
              if (url != null) {
                syncController.synchedExternalId.put(name, tmp.getAsString(DB.EXPORT.EXTERNAL_ID));
              }
            }
          } else if (tmp.containsKey(DB.ACCOUNT.FLAGS)
              && Bitfield.test(tmp.getAsLong(DB.ACCOUNT.FLAGS), DB.ACCOUNT.FLAG_UPLOAD)) {
            syncController.pendingSynchronizers.add(name);
          }
        } while (c.moveToNext());
      }
      c.close();
    }

    if (mode == MODE_DETAILS) {
      setUploadVisibility();
    }

    for (BaseAdapter a : adapters) {
      a.notifyDataSetChanged();
    }
  }

  private void fillHeaderData() {
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
      setTitle(formatter.formatDateTime(st));
    }

    if (tmp.containsKey(DB.ACTIVITY.COMMENT)) {
      notes.setText(tmp.getAsString(DB.ACTIVITY.COMMENT));
    }

    headerData = tmp;
    updateHeader(tmp, /* fromManualDistance= */false);
  }

  private void updateHeader(ContentValues data, boolean fromManualDistance) {
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

  private void buildLapDisplayEntries() {
    lapDisplayEntries = DetailLapListController.buildDisplayEntries(laps, lapListHost);
  }

  private class ReportListAdapter extends BaseAdapter {

    @Override
    public int getCount() {
      return reports.size() + 1;
    }

    @Override
    public Object getItem(int position) {
      if (position < reports.size()) return reports.get(position);
      return this;
    }

    @Override
    public long getItemId(int position) {
      if (position < reports.size()) return reports.get(position).getAsLong("_id");

      return 0;
    }

    private class ViewHolderDetailActivity {
      private TextView tv0;
      private CheckBox cb;
      private TextView tv1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (position == reports.size()) {
        Button b = new Button(DetailActivity.this);
        b.setText(org.runnerup.common.R.string.Configure_accounts);
        b.setBackgroundResource(R.drawable.btn_blue);
        b.setTextColor(
            AppCompatResources.getColorStateList(DetailActivity.this, R.color.btn_text_color));
        b.setOnClickListener(
            v -> {
              Intent i = new Intent(DetailActivity.this, AccountListActivity.class);
              DetailActivity.this.startActivityForResult(i, EDIT_ACCOUNT_REQUEST);
            });
        return b;
      }

      View view = convertView;
      ViewHolderDetailActivity viewHolder;

      // Note: Special ViewHolder support as the Configure button is not in the view
      if (view == null || view.getTag() == null) {
        viewHolder = new ViewHolderDetailActivity();

        LayoutInflater inflater = LayoutInflater.from(DetailActivity.this);
        view = inflater.inflate(R.layout.reportlist_row, parent, false);

        viewHolder.tv0 = view.findViewById(R.id.reportlist_account_id);
        viewHolder.cb = view.findViewById(R.id.reportlist_sent);
        viewHolder.tv1 = view.findViewById(R.id.reportlist_account_name);

        view.setTag(viewHolder);
      } else {
        viewHolder = (ViewHolderDetailActivity) view.getTag();
      }

      ContentValues tmp = reports.get(position);
      String name = tmp.getAsString(DB.ACCOUNT.NAME);
      viewHolder.cb.setOnCheckedChangeListener(null);
      viewHolder.cb.setChecked(false);
      viewHolder.cb.setEnabled(false);
      viewHolder.cb.setTag(name);
      viewHolder.tv1.setTag(name);
      viewHolder.tv1.setTextColor(viewHolder.cb.getTextColors());
      if (syncController.alreadySynched.contains(name)) {
        viewHolder.cb.setChecked(true);
        if (syncController.synchedExternalId.containsKey(name)) {
          // Indicate Clickable label
          viewHolder.tv1.setTextColor(Color.BLUE);
        }
        viewHolder.cb.setText(org.runnerup.common.R.string.Uploaded);
        viewHolder.cb.setOnLongClickListener(clearUploadClick);
      } else {
        viewHolder.cb.setChecked(syncController.pendingSynchronizers.contains(name));
        viewHolder.cb.setText(org.runnerup.common.R.string.Upload);
        viewHolder.cb.setOnLongClickListener(null);
      }
      if (mode == MODE_DETAILS) {
        viewHolder.cb.setEnabled(true);
      } else if (mode == MODE_SAVE) {
        viewHolder.cb.setEnabled(true);
      }
      viewHolder.cb.setOnCheckedChangeListener(onSendChecked);

      viewHolder.tv0.setText(tmp.getAsString("_id"));
      viewHolder.tv1.setText(name);

      return view;
    }
  }

  private void navigateToHistory() {
    // The source tab is already stored in SharedPreferences in onCreate
    // Just finish - let Android handle the back animation naturally
    finish();
  }

  private void saveActivity() {
    int sportValue = sport.getValueInt();
    ContentValues tmp = headerData;
    tmp.put(DB.ACTIVITY.COMMENT, notes.getText().toString());
    tmp.put(DB.ACTIVITY.SPORT, sportValue);
    String[] whereArgs = {Long.toString(mID)};
    mDB.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", whereArgs);

    // path simplification (reduce resolution of location entries in database)
    try {
      PathSimplifier simplifier = PathSimplifier.getPathSimplifierForSave(this);
      if (simplifier != null) {
        ArrayList<String> ids = simplifier.getNoisyLocationIDsAsStrings(mDB, mID);
        ActivityCleaner.deleteLocations(mDB, ids);
        (new ActivityCleaner()).recompute(mDB, mID);
      }
    } catch (Exception e) {
      Log.e(getClass().getName(), "Failed to simplify path: " + e.getMessage());
    }
    
    // Create automatic backup after saving activity changes (if enough time has passed)
    org.runnerup.core.util.AutomaticBackupManager.createBackupIfNeeded(this);
  }

  private OnLongClickListener clearUploadClick;
  private OnCheckedChangeListener onSendChecked;

  // Note: onClick set in reportlist_row.xml
  public void onClickAccountName(View arg0) {
    syncController.openAccountUrl(this, syncManager, (String) arg0.getTag());
  }

  private final OnClickListener saveButtonClick =
      new OnClickListener() {
        public void onClick(View v) {
          saveActivity();
          if (mode == MODE_DETAILS) {
            setEdit(false);
            requery();
            return;
          }
          uploading = true;
          syncManager.startUploading(
              (synchronizerName, status) -> {
                uploading = false;
                final Intent returnIntent = new Intent();
                int sportValue = sport.getValueInt();
                if (Sport.hasManualDistance(sportValue)) {
                  returnIntent.putExtra(
                      "MANUAL_DISTANCE", headerData.getAsDouble(DB.ACTIVITY.DISTANCE));
                }
                DetailActivity.this.setResult(RESULT_OK, returnIntent);
                DetailActivity.this.finish();
              },
              syncController.pendingSynchronizers,
              mID);
        }
      };

  private final OnClickListener discardButtonClick =
      v ->
          new AlertDialog.Builder(DetailActivity.this)
              .setTitle(org.runnerup.common.R.string.Discard)
              .setMessage(org.runnerup.common.R.string.Are_you_sure)
              .setPositiveButton(
                  org.runnerup.common.R.string.Yes,
                  (dialog, which) -> {
                    dialog.dismiss();
                    DetailActivity.this.setResult(RESULT_CANCELED);
                    DetailActivity.this.finish();
                  })
              .setNegativeButton(
                  org.runnerup.common.R.string.No,
                  // Do nothing but close the dialog
                  (dialog, which) -> dialog.dismiss())
              .show();

  private final OnClickListener resumeButtonClick =
      v -> {
        DetailActivity.this.setResult(RESULT_FIRST_USER);
        DetailActivity.this.finish();
      };

  private final OnClickListener deleteButtonClick =
      v ->
          new AlertDialog.Builder(DetailActivity.this)
              .setTitle(org.runnerup.common.R.string.Delete_activity)
              .setMessage(org.runnerup.common.R.string.Are_you_sure)
              .setPositiveButton(
                  org.runnerup.common.R.string.Yes,
                  (dialog, which) -> {
                    DBHelper.deleteActivity(mDB, mID);
                    dialog.dismiss();
                    DetailActivity.this.setResult(RESULT_OK);
                    DetailActivity.this.finish();
                  })
              .setNegativeButton(
                  org.runnerup.common.R.string.No,
                  // Do nothing but close the dialog
                  (dialog, which) -> dialog.dismiss())
              .show();

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == SyncManager.CONFIGURE_REQUEST) {
      syncManager.onActivityResult(requestCode, resultCode, data);
    }
    requery();
  }

  private void shareActivity() {
    final int[] which = {
      1 // TODO preselect tcx - choice should be remembered
    };
    final CharSequence[] items = {"gpx", "tcx"};
    new AlertDialog.Builder(this)
        .setTitle(getString(org.runnerup.common.R.string.Share_activity))
        .setPositiveButton(
            org.runnerup.common.R.string.OK,
            (dialog, w) -> {
              if (which[0] == -1) {
                dialog.dismiss();
                return;
              }

              final Context context = DetailActivity.this;
              final CharSequence fmt = items[which[0]];
              final Intent intent = new Intent(Intent.ACTION_SEND);

              if (fmt.equals("tcx")) {
                intent.setType(TCX_MIME);
              } else {
                intent.setType(GPX_MIME);
              }

              // Use of content:// (or STREAM?) instead of file:// is not supported in ES and other
              // apps
              // Solid Explorer File Manager works though
              String actType = Sport.textOf(getResources(), sport.getValueInt());
              Uri uri =
                  Uri.parse(
                      "content://"
                          + ActivityProvider.AUTHORITY
                          + "/"
                          + fmt
                          + "/"
                          + mID
                          + "/"
                          + FileNameHelper.getExportFileName(mStartTime, actType)
                          + fmt);
              intent.putExtra(Intent.EXTRA_STREAM, uri);
              context.startActivity(
                  Intent.createChooser(
                      intent, getString(org.runnerup.common.R.string.Share_activity)));
            })
        .setNegativeButton(
            org.runnerup.common.R.string.Cancel,
            (dialog, which1) -> {
              // Do nothing but close the dialog
              dialog.dismiss();
            })
        .setSingleChoiceItems(items, which[0], (dialog, w) -> which[0] = w)
        .show();
  }
  
  private void markAsUnsaved() {
    if (!hasUnsavedChanges) {
      hasUnsavedChanges = true;
      updateSaveMenuVisibility();
    }
  }
  
  private void updateSaveMenuVisibility() {
    if (saveMenuItem != null) {
      // Only show save icon in MODE_SAVE when there are unsaved changes
      saveMenuItem.setVisible(mode == MODE_SAVE && hasUnsavedChanges);
    }
  }

}
