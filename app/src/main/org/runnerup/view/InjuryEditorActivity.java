package org.runnerup.view;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.widget.TitleSpinner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.runnerup.common.util.Constants.DB;

public class InjuryEditorActivity extends AppCompatActivity implements Constants {
  public static final String EXTRA_ACTIVITY_ID = "activity_id";
  public static final String EXTRA_PHASE = "phase";

  private long activityId;
  private int currentPhase;
  private int currentZone = DB.TENDON.ZONE_KNEE;
  private SQLiteDatabase db;
  private TitleSpinner phaseSpinner;
  private TitleSpinner zoneSpinner;
  private ListView tendonList;
  private TendonAdapter adapter;
  private final ArrayList<TendonRow> rows = new ArrayList<>();
  private final Map<Long, Integer> existingInjuries = new HashMap<>(); // tendonId -> pain

  private static class TendonRow {
    long tendonId;
    String name;
    String description;
    int zone;
    int pain; // 0 means not selected
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.injury_editor);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    db = DBHelper.getWritableDatabase(this);
    activityId = getIntent().getLongExtra(EXTRA_ACTIVITY_ID, -1);
    if (activityId < 0) {
      finish();
      return;
    }

    int initialPhase = getIntent().getIntExtra(EXTRA_PHASE, DB.ACTIVITY_INJURY.PHASE_BEFORE);
    if (initialPhase < 0) initialPhase = DB.ACTIVITY_INJURY.PHASE_BEFORE;
    if (initialPhase > 2) initialPhase = DB.ACTIVITY_INJURY.PHASE_AFTER;
    currentPhase = initialPhase;

    phaseSpinner = findViewById(R.id.phase_spinner);
    zoneSpinner = findViewById(R.id.zone_spinner);
    tendonList = findViewById(R.id.tendon_list);

    setupPhaseSpinner();
    setupZoneSpinner();
    loadExistingInjuries();
    loadTendonsForZone();
    setupAdapter();
  }

  private void setupPhaseSpinner() {
    String[] phases = {"Before", "During", "After"};
    phaseSpinner.setArrayEntries(phases);
    phaseSpinner.setViewSelection(currentPhase);
    phaseSpinner.setViewValue(currentPhase);
    phaseSpinner.setViewOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
        if (position >= 0 && position <= 2 && position != currentPhase) {
          saveCurrentInjuries();
          currentPhase = position;
          loadExistingInjuries();
          loadTendonsForZone();
          adapter.notifyDataSetChanged();
        }
      }
      @Override
      public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    });
  }

  private void setupZoneSpinner() {
    String[] zones = {"Knee", "Calves", "Ankle/Foot", "Hip"};
    final int[] zoneValues = {DB.TENDON.ZONE_KNEE, DB.TENDON.ZONE_CALVES,
        DB.TENDON.ZONE_ANKLE_FOOT, DB.TENDON.ZONE_HIP};
    zoneSpinner.setArrayEntries(zones);
    // Find current zone index
    int zoneIndex = 0;
    for (int i = 0; i < zoneValues.length; i++) {
      if (zoneValues[i] == currentZone) {
        zoneIndex = i;
        break;
      }
    }
    zoneSpinner.setViewSelection(zoneIndex);
    zoneSpinner.setViewValue(zoneIndex);
    zoneSpinner.setViewOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
        if (position >= 0 && position < zoneValues.length && zoneValues[position] != currentZone) {
          saveCurrentInjuries();
          currentZone = zoneValues[position];
          loadExistingInjuries();
          loadTendonsForZone();
          adapter.notifyDataSetChanged();
        }
      }
      @Override
      public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    });
  }

  private void loadExistingInjuries() {
    existingInjuries.clear();
    String[] cols = {DB.ACTIVITY_INJURY.TENDON_ID, DB.ACTIVITY_INJURY.PAIN};
    String where = DB.ACTIVITY_INJURY.ACTIVITY_ID + " = ? AND " +
        DB.ACTIVITY_INJURY.PHASE + " = ? AND " +
        DB.ACTIVITY_INJURY.ZONE + " = ?";
    String[] args = {String.valueOf(activityId), String.valueOf(currentPhase),
        String.valueOf(currentZone)};
    try (Cursor c = db.query(DB.ACTIVITY_INJURY.TABLE, cols, where, args, null, null, null)) {
      while (c.moveToNext()) {
        long tendonId = c.getLong(0);
        int pain = c.getInt(1);
        existingInjuries.put(tendonId, pain);
      }
    }
  }

  private void loadTendonsForZone() {
    rows.clear();
    String[] cols = {DB.PRIMARY_KEY, DB.TENDON.NAME, DB.TENDON.DESCRIPTION};
    String where = DB.TENDON.ZONE + " = ? AND " + DB.TENDON.ACTIVE + " = 1";
    String[] args = {String.valueOf(currentZone)};
    try (Cursor c = db.query(DB.TENDON.TABLE, cols, where, args, null, null, DB.TENDON.NAME)) {
      while (c.moveToNext()) {
        TendonRow row = new TendonRow();
        row.tendonId = c.getLong(0);
        row.name = c.getString(1);
        row.description = c.getString(2);
        row.zone = currentZone;
        row.pain = existingInjuries.getOrDefault(row.tendonId, 0);
        rows.add(row);
      }
    }
  }

  private void setupAdapter() {
    adapter = new TendonAdapter();
    tendonList.setAdapter(adapter);
  }

  private void saveCurrentInjuries() {
    if (db == null || activityId < 0) {
      return;
    }
    try {
      // Delete existing injuries for this phase/zone
      String where = DB.ACTIVITY_INJURY.ACTIVITY_ID + " = ? AND " +
          DB.ACTIVITY_INJURY.PHASE + " = ? AND " +
          DB.ACTIVITY_INJURY.ZONE + " = ?";
      String[] args = {String.valueOf(activityId), String.valueOf(currentPhase),
          String.valueOf(currentZone)};
      db.delete(DB.ACTIVITY_INJURY.TABLE, where, args);

      // Insert injuries with pain > 0
      for (TendonRow row : rows) {
        if (row.pain > 0) {
          ContentValues v = new ContentValues();
          v.put(DB.ACTIVITY_INJURY.ACTIVITY_ID, activityId);
          v.put(DB.ACTIVITY_INJURY.PHASE, currentPhase);
          v.put(DB.ACTIVITY_INJURY.ZONE, currentZone);
          v.put(DB.ACTIVITY_INJURY.TENDON_ID, row.tendonId);
          v.put(DB.ACTIVITY_INJURY.PAIN, row.pain);
          v.put(DB.ACTIVITY_INJURY.CREATED_AT, System.currentTimeMillis() / 1000);
          db.insert(DB.ACTIVITY_INJURY.TABLE, null, v);
        }
      }
    } catch (Exception e) {
      android.util.Log.e("InjuryEditorActivity", "Error saving injuries", e);
    }
  }

  @Override
  public boolean onSupportNavigateUp() {
    saveCurrentInjuries();
    finish();
    return true;
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (db != null && !isFinishing()) {
      saveCurrentInjuries();
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    // Don't close db here - it's a shared singleton
    // DBHelper.closeDB(db);
  }

  private class TendonAdapter extends BaseAdapter {
    @Override
    public int getCount() {
      return rows.size();
    }

    @Override
    public Object getItem(int position) {
      return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
      return rows.get(position).tendonId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        convertView = LayoutInflater.from(InjuryEditorActivity.this)
            .inflate(R.layout.injury_tendon_row, parent, false);
      }

      TendonRow row = rows.get(position);
      TextView nameView = convertView.findViewById(R.id.tendon_name);
      TextView descView = convertView.findViewById(R.id.tendon_desc);
      SeekBar painSeek = convertView.findViewById(R.id.tendon_pain_seek);
      TextView painVal = convertView.findViewById(R.id.tendon_pain_val);

      nameView.setText(row.name);
      if (row.description != null && !row.description.isEmpty()) {
        descView.setText(row.description);
        descView.setVisibility(View.VISIBLE);
      } else {
        descView.setVisibility(View.GONE);
      }

      painSeek.setMax(10);
      painSeek.setProgress(row.pain);
      updatePainText(painVal, row.pain);

      painSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
          if (fromUser) {
            row.pain = progress;
            updatePainText(painVal, progress);
            saveCurrentInjuries();
          }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
      });

      return convertView;
    }

    private void updatePainText(TextView tv, int pain) {
      if (pain == 0) {
        tv.setText("-");
      } else {
        tv.setText(String.valueOf(pain));
      }
    }
  }
}
