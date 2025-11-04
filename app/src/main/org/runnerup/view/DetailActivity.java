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

package org.runnerup.view;

import static org.runnerup.BuildConfig.USING_OSMDROID;
import static org.runnerup.content.ActivityProvider.GPX_MIME;
import static org.runnerup.content.ActivityProvider.TCX_MIME;

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
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
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
import java.util.Locale;
import java.util.Map;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.content.ActivityProvider;
import org.runnerup.db.ActivityCleaner;
import org.runnerup.db.DBHelper;
import org.runnerup.db.PathSimplifier;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer;
import org.runnerup.export.Synchronizer.Feature;
import org.runnerup.util.Bitfield;
import org.runnerup.util.FileNameHelper;
import org.runnerup.util.Formatter;
import org.runnerup.util.GraphWrapper;
import org.runnerup.util.MapWrapper;
import org.runnerup.util.SafeParse;
import org.runnerup.widget.SpinnerInterface.OnSetValueListener;
import org.runnerup.widget.TitleSpinner;
import org.runnerup.widget.WidgetUtil;
import org.runnerup.workout.Intensity;
import org.runnerup.workout.Sport;

public class DetailActivity extends AppCompatActivity implements Constants {

  private long mID = 0;
  private SQLiteDatabase mDB = null;
  private final HashSet<String> pendingSynchronizers = new HashSet<>();
  private final HashSet<String> alreadySynched = new HashSet<>();
  private final Map<String, String> synchedExternalId = new HashMap<>();

  private boolean lapHrPresent = false;
  private ContentValues[] laps = null;
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
  private View injuryButton = null;
  private View injuryCardBefore = null;
  private View injuryCardDuring = null;
  private View injuryCardAfter = null;
  private LinearLayout injuryIconsBefore = null;
  private LinearLayout injuryIconsDuring = null;
  private LinearLayout injuryIconsAfter = null;
  private View rootView;
  private View mapTab;
  private View graphTab;

  private MapWrapper mapWrapper = null;
  private GraphWrapper graphWrapper = null;

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
    sport = findViewById(R.id.summary_sport);
    sport.setOnSetValueListener(new OnSetValueListener() {
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

    manualDistance = findViewById(R.id.summary_manual_distance);
    manualDistance.setOnSetValueListener(new OnSetValueListener() {
        @Override
        public String preSetValue(String newValue) throws IllegalArgumentException {
          double dist = SafeParse.parseDouble(newValue, 0); // convert to meters
          headerData.put(DB.ACTIVITY.DISTANCE, dist);
          updateHeader(headerData, /* fromManualDistance= */true);
          return newValue;
        }

        @Override
        public int preSetValue(int newValue) throws IllegalArgumentException {
          return newValue;
        }
      });
    notes = findViewById(R.id.notes_text);
    injuryButton = findViewById(R.id.injury_button);
    injuryCardBefore = findViewById(R.id.injury_card_before);
    injuryCardDuring = findViewById(R.id.injury_card_during);
    injuryCardAfter = findViewById(R.id.injury_card_after);
    injuryIconsBefore = findViewById(R.id.injury_icons_before);
    injuryIconsDuring = findViewById(R.id.injury_icons_during);
    injuryIconsAfter = findViewById(R.id.injury_icons_after);

    if (USING_OSMDROID || BuildConfig.MAPBOX_ENABLED > 0) {
      Object mapView = findViewById(R.id.mapview);
      mapWrapper = new MapWrapper(this, mDB, mID, formatter, mapView);
      mapWrapper.onCreate(savedInstanceState);
    }

    saveButton.setOnClickListener(saveButtonClick);
    uploadButton.setOnClickListener(uploadButtonClick);
    if (injuryButton != null) {
      injuryButton.setOnClickListener(v -> openInjuryEditor());
    }
    
    // Set click listeners on injury cards to open editor with specific phase
    if (injuryCardBefore != null) {
      injuryCardBefore.setOnClickListener(v -> openInjuryEditor(DB.ACTIVITY_INJURY.PHASE_BEFORE));
    }
    if (injuryCardDuring != null) {
      injuryCardDuring.setOnClickListener(v -> openInjuryEditor(DB.ACTIVITY_INJURY.PHASE_DURING));
    }
    if (injuryCardAfter != null) {
      injuryCardAfter.setOnClickListener(v -> openInjuryEditor(DB.ACTIVITY_INJURY.PHASE_AFTER));
    }

    uploadButton.setVisibility(View.GONE);

    TabHost th = findViewById(R.id.tabhost);
    th.setup();
    TabSpec tabSpec = th.newTabSpec("overview");
    tabSpec.setIndicator(
        WidgetUtil.createHoloTabIndicator(this, "Overview"));
    tabSpec.setContent(R.id.tab_main);
    th.addTab(tabSpec);

    tabSpec = th.newTabSpec("laps");
    tabSpec.setIndicator(
        WidgetUtil.createHoloTabIndicator(this, getString(org.runnerup.common.R.string.Laps)));
    tabSpec.setContent(R.id.tab_lap);
    th.addTab(tabSpec);

    if (USING_OSMDROID || BuildConfig.MAPBOX_ENABLED > 0) {
      tabSpec = th.newTabSpec("map");
      tabSpec.setIndicator(
          WidgetUtil.createHoloTabIndicator(this, getString(org.runnerup.common.R.string.Map)));
      tabSpec.setContent(R.id.tab_map);
      th.addTab(tabSpec);
      mapTab = th.getTabWidget().getChildTabViewAt(2);
    }

    tabSpec = th.newTabSpec("graph");
    tabSpec.setIndicator(
        WidgetUtil.createHoloTabIndicator(this, getString(org.runnerup.common.R.string.Graph)));
    tabSpec.setContent(R.id.tab_graph);
    th.addTab(tabSpec);
    // Get graph tab (cannot hardcode index due to optional map tab and removed upload tab).
    int graphTabIndex = th.getTabWidget().getChildCount() - 1;
    graphTab = th.getTabWidget().getChildTabViewAt(graphTabIndex);

    // Upload tab removed
    // tabSpec = th.newTabSpec("share");
    // tabSpec.setIndicator(
    //     WidgetUtil.createHoloTabIndicator(this, getString(org.runnerup.common.R.string.Upload)));
    // tabSpec.setContent(R.id.tab_upload);
    // th.addTab(tabSpec);

    fillHeaderData();
    requery();

    {
      ListView lv = findViewById(R.id.laplist);
      LapListAdapter adapter = new LapListAdapter();
      adapters.add(adapter);
      lv.setAdapter(adapter);
    }
    // Upload tab removed - no longer initialize report list
    // {
    //   ListView lv = findViewById(R.id.report_list);
    //   ReportListAdapter adapter = new ReportListAdapter();
    //   adapters.add(adapter);
    //   lv.setAdapter(adapter);
    // }

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
                  resumeButtonClick.onClick(resumeButton);
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

    LinearLayout graphTabLayout = findViewById(R.id.tab_graph);
    LinearLayout hrzonesBarLayout = findViewById(R.id.hrzonesBarLayout);
    boolean use_distance_as_x = !Sport.isWithoutGps(sport.getValueInt());
    graphWrapper = new GraphWrapper(this, graphTabLayout, hrzonesBarLayout,
                                    formatter, mDB, mID, use_distance_as_x);

    if (this.mode == MODE_SAVE) {
      // Hide buttons (they will be removed from UI)
      View buttonsLayout = findViewById(R.id.buttons);
      buttonsLayout.setVisibility(View.GONE);
      
      resumeButton.setOnClickListener(resumeButtonClick);
      discardButton.setOnClickListener(discardButtonClick);
      setEdit(true);
      
      // Auto-save the activity
      autoSaveActivity();
    } else if (this.mode == MODE_DETAILS) {
      resumeButton.setVisibility(View.GONE);
      discardButton.setVisibility(View.GONE);
      setEdit(false);
    }
    
    // Initial render of injury icons
    renderInjuryIcons();
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
    if (graphWrapper != null) {
      boolean use_distance_as_x = !Sport.isWithoutGps(sportValue);
      graphWrapper.setUseDistanceAsX(use_distance_as_x);
    }
  }

  private void setUploadVisibility() {
    boolean enabled = !pendingSynchronizers.isEmpty();
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
                new ActivityCleaner().recompute(mDB, mID);
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
    if (mapWrapper != null) {
      mapWrapper.onResume();
    }
    renderInjuryIcons();
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
            DB.LAP.AVG_HR
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
      alreadySynched.clear();
      synchedExternalId.clear();
      pendingSynchronizers.clear();
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
            alreadySynched.add(name);
            if (tmp.containsKey(DB.EXPORT.STATUS)
                && tmp.getAsInteger(DB.EXPORT.STATUS)
                    == Synchronizer.ExternalIdStatus.getInt(Synchronizer.ExternalIdStatus.OK)) {
              String url =
                  syncManager
                      .getSynchronizerByName(name)
                      .getActivityUrl(synchedExternalId.get(name));
              if (url != null) {
                synchedExternalId.put(name, tmp.getAsString(DB.EXPORT.EXTERNAL_ID));
              }
            }
          } else if (tmp.containsKey(DB.ACCOUNT.FLAGS)
              && Bitfield.test(tmp.getAsLong(DB.ACCOUNT.FLAGS), DB.ACCOUNT.FLAG_UPLOAD)) {
            pendingSynchronizers.add(name);
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
    double d = 0;
    if (data.containsKey(DB.ACTIVITY.DISTANCE)) {
      d = data.getAsDouble(DB.ACTIVITY.DISTANCE);
      String s = formatter.formatDistance(Formatter.Format.TXT_SHORT, (long) d);
      activityDistance.setText(s);
      if (!fromManualDistance) {
        /**
         * IF !fromManualDistance (e.g. from database)
         *   update the manual distance field in case (if it might be needed)
         * ELSE
         *   fromManualDistance=true
         *   e.g. from spinner, don't update or else it will recurse
         */
        int distance = (int)d;
        manualDistance.setValue(Long.toString(distance));
        manualDistance.setValue(distance);
      }
    } else {
      activityDistance.setText("");
    }

    long t = 0;
    if (data.containsKey(DB.ACTIVITY.TIME)) {
      t = data.getAsInteger(DB.ACTIVITY.TIME);
      activityTime.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, t));
    } else {
      activityTime.setText("");
    }

    if (t != 0) {
      activityPace.setVisibility(View.VISIBLE);
      activityPaceSeparator.setVisibility(View.VISIBLE);
      activityPace.setText(
          formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, d / t));
    } else {
      activityPace.setVisibility(View.GONE);
      activityPaceSeparator.setVisibility(View.GONE);
    }

    if (data.containsKey(DB.ACTIVITY.SPORT)) {
      sport.setValue(data.getAsInteger(DB.ACTIVITY.SPORT));
    }
  }

  private class ViewHolderLapList {
    private TextView tv0;
    private TextView tv1;
    private TextView tv2;
    private TextView tv3;
    private TextView tv4;
    private TextView tvHr;
  }

  private class LapListAdapter extends BaseAdapter {

    @Override
    public int getCount() {
      return laps.length;
    }

    @Override
    public Object getItem(int position) {
      return laps[position];
    }

    @Override
    public long getItemId(int position) {
      return laps[position].getAsLong("_id");
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View view = convertView;
      ViewHolderLapList viewHolder;

      if (view == null) {
        viewHolder = new ViewHolderLapList();
        LayoutInflater inflater = LayoutInflater.from(DetailActivity.this);
        view = inflater.inflate(R.layout.laplist_row, parent, false);
        viewHolder.tv0 = view.findViewById(R.id.lap_list_type);
        viewHolder.tv1 = view.findViewById(R.id.lap_list_id);
        viewHolder.tv2 = view.findViewById(R.id.lap_list_distance);
        viewHolder.tv3 = view.findViewById(R.id.lap_list_time);
        viewHolder.tv4 = view.findViewById(R.id.lap_list_pace);
        viewHolder.tvHr = view.findViewById(R.id.lap_list_hr);

        view.setTag(viewHolder);
      } else {
        viewHolder = (ViewHolderLapList) view.getTag();
      }
      int i = laps[position].getAsInteger(DB.LAP.INTENSITY);
      Intensity intensity = Intensity.values()[i];
      switch (intensity) {
        case ACTIVE:
          viewHolder.tv0.setText("");
          break;
        case COOLDOWN:
        case RESTING:
        case RECOVERY:
        case WARMUP:
        case REPEAT:
          viewHolder.tv0.setText(
              String.format(
                  Locale.getDefault(), "(%s)", getResources().getString(intensity.getTextId())));
        default:
          break;
      }
      viewHolder.tv1.setText(laps[position].getAsString("_id"));
      double d =
          laps[position].containsKey(DB.LAP.DISTANCE)
              ? laps[position].getAsDouble(DB.LAP.DISTANCE)
              : 0;
      viewHolder.tv2.setText(formatter.formatDistance(Formatter.Format.TXT_LONG, (long) d));
      long t = laps[position].containsKey(DB.LAP.TIME) ? laps[position].getAsLong(DB.LAP.TIME) : 0;
      viewHolder.tv3.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, t));
      if (t != 0) {
        viewHolder.tv4.setText(
            formatter.formatVelocityByPreferredUnit(Formatter.Format.TXT_LONG, d / t));
      } else {
        viewHolder.tv4.setText("");
      }
      int hr =
          laps[position].containsKey(DB.LAP.AVG_HR)
              ? laps[position].getAsInteger(DB.LAP.AVG_HR)
              : 0;
      if (hr > 0) {
        viewHolder.tvHr.setVisibility(View.VISIBLE);
        // Use CUE_LONG instead of TXT_LONG to include unit
        viewHolder.tvHr.setText(formatter.formatHeartRate(Formatter.Format.CUE_LONG, hr));
      } else if (lapHrPresent) {
        viewHolder.tvHr.setVisibility(View.INVISIBLE);
      } else {
        viewHolder.tvHr.setVisibility(View.GONE);
      }

      return view;
    }
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
      if (alreadySynched.contains(name)) {
        viewHolder.cb.setChecked(true);
        if (synchedExternalId.containsKey(name)) {
          // Indicate Clickable label
          viewHolder.tv1.setTextColor(Color.BLUE);
        }
        viewHolder.cb.setText(org.runnerup.common.R.string.Uploaded);
        viewHolder.cb.setOnLongClickListener(clearUploadClick);
      } else {
        viewHolder.cb.setChecked(pendingSynchronizers.contains(name));
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
  }

  private final OnLongClickListener clearUploadClick =
      arg0 -> {
        final String name = (String) arg0.getTag();
        new AlertDialog.Builder(DetailActivity.this)
            .setTitle("Clear upload for " + name)
            .setMessage(org.runnerup.common.R.string.Are_you_sure)
            .setPositiveButton(
                org.runnerup.common.R.string.Yes,
                (dialog, which) -> {
                  dialog.dismiss();
                  syncManager.clearUpload(name, mID);
                  requery();
                })
            .setNegativeButton(
                org.runnerup.common.R.string.No,
                // Do nothing but close the dialog
                (dialog, which) -> dialog.dismiss())
            .show();
        return false;
      };

  // Note: onClick set in reportlist_row.xml
  public void onClickAccountName(View arg0) {
    final String name = (String) arg0.getTag();
    if (synchedExternalId.containsKey(name) && !TextUtils.isEmpty(synchedExternalId.get(name))) {
      String url =
          syncManager.getSynchronizerByName(name).getActivityUrl(synchedExternalId.get(name));
      if (url != null) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent);
      }
    }
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
              pendingSynchronizers,
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

  private final OnClickListener uploadButtonClick =
      v -> {
        uploading = true;
        syncManager.startUploading(
            (synchronizerName, status) -> {
              uploading = false;
              requery();
            },
            pendingSynchronizers,
            mID);
      };

  private final OnCheckedChangeListener onSendChecked =
      new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
          final String name = (String) arg0.getTag();
          if (alreadySynched.contains(name)) {
            // Only accept long clicks
            arg0.setChecked(true);
          } else {
            if (arg1) {
              pendingSynchronizers.add((String) arg0.getTag());
            } else {
              //noinspection SuspiciousMethodCalls
              pendingSynchronizers.remove(arg0.getTag());
            }
            if (mode == MODE_DETAILS) {
              setUploadVisibility();
            }
          }
        }
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

  private void openInjuryEditor() {
    openInjuryEditor(DB.ACTIVITY_INJURY.PHASE_BEFORE);
  }

  private void openInjuryEditor(int phase) {
    Intent i = new Intent(this, InjuryEditorActivity.class);
    i.putExtra(InjuryEditorActivity.EXTRA_ACTIVITY_ID, mID);
    i.putExtra(InjuryEditorActivity.EXTRA_PHASE, phase);
    startActivity(i);
  }

  private void renderInjuryIcons() {
    if (injuryIconsBefore == null) return;
    injuryIconsBefore.removeAllViews();
    injuryIconsDuring.removeAllViews();
    injuryIconsAfter.removeAllViews();

    // Query max pain per zone per phase
    String sql = "SELECT " + DB.ACTIVITY_INJURY.PHASE + ", " + DB.ACTIVITY_INJURY.ZONE + ", MAX(" + DB.ACTIVITY_INJURY.PAIN + ")" +
        " FROM " + DB.ACTIVITY_INJURY.TABLE +
        " WHERE " + DB.ACTIVITY_INJURY.ACTIVITY_ID + " = ?" +
        " GROUP BY " + DB.ACTIVITY_INJURY.PHASE + ", " + DB.ACTIVITY_INJURY.ZONE;
    int[][] maxByPhaseZone = new int[3][4];
    for (int p=0;p<3;p++) for (int z=0;z<4;z++) maxByPhaseZone[p][z] = -1;
    try (Cursor c = mDB.rawQuery(sql, new String[]{ String.valueOf(mID) })) {
      while (c.moveToNext()) {
        int phase = c.getInt(0);
        int zone = c.getInt(1);
        int maxPain = c.getInt(2);
        if (phase>=0 && phase<3 && zone>=0 && zone<4) maxByPhaseZone[phase][zone] = maxPain;
      }
    }

    addZoneIconRow(injuryIconsBefore, maxByPhaseZone[DB.ACTIVITY_INJURY.PHASE_BEFORE]);
    addZoneIconRow(injuryIconsDuring, maxByPhaseZone[DB.ACTIVITY_INJURY.PHASE_DURING]);
    addZoneIconRow(injuryIconsAfter, maxByPhaseZone[DB.ACTIVITY_INJURY.PHASE_AFTER]);
  }

  private void addZoneIconRow(LinearLayout container, int[] zonePains) {
    int[] icons = new int[] { R.drawable.ic_zone_knee_24dp, R.drawable.ic_zone_calves_24dp, R.drawable.ic_zone_ankle_foot_24dp, R.drawable.ic_zone_hip_24dp };
    for (int z=0; z<4; z++) {
      int pain = zonePains[z];
      if (pain < 0) continue; // no icon if nothing selected
      android.widget.ImageView iv = new android.widget.ImageView(this);
      iv.setImageResource(icons[z]);
      int color = injuryColorForPain(pain);
      iv.setColorFilter(color);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.setMarginEnd(16);
      iv.setLayoutParams(lp);
      container.addView(iv);
    }
  }

  private int injuryColorForPain(int pain) {
    if (pain <= 0) return 0xFFFFFFFF; // white
    if (pain >= 10) return 0xFF8B0000; // dark red
    // smooth interpolate from light yellow (1) to dark red (10)
    // start: #FFF9C4 (light yellow), end: #8B0000
    int start = 0xFFFFF9C4;
    int end = 0xFF8B0000;
    float t = pain / 10f;
    int sr=(start>>16)&0xFF, sg=(start>>8)&0xFF, sb=start&0xFF;
    int er=(end>>16)&0xFF, eg=(end>>8)&0xFF, eb=end&0xFF;
    int r = (int)(sr + (er - sr) * t);
    int g = (int)(sg + (eg - sg) * t);
    int b = (int)(sb + (eb - sb) * t);
    return 0xFF000000 | (r<<16) | (g<<8) | b;
  }
}
