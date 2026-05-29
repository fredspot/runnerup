package org.runnerup.features;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.IOException;
import org.json.JSONException;
import org.runnerup.R;
import org.runnerup.core.util.ViewUtil;
import org.runnerup.core.workout.RepeatStep;
import org.runnerup.core.workout.Step;
import org.runnerup.core.workout.Workout;
import org.runnerup.core.workout.WorkoutSerializer;

public class CreateAdvancedWorkout extends AppCompatActivity {

  private Workout advancedWorkout = null;
  private WorkoutEditorStepsAdapter advancedWorkoutStepsAdapter = null;
  private String workoutName = "";
  private boolean workoutExists = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

    setContentView(R.layout.create_advanced_workout);

    Intent intent = getIntent();
    workoutName = intent.getStringExtra(ManageWorkoutsActivity.WORKOUT_NAME);
    if (workoutName == null) {
      workoutName = "";
    }
    workoutExists = intent.getBooleanExtra(ManageWorkoutsActivity.WORKOUT_EXISTS, false);

    Toolbar toolbar = findViewById(R.id.workout_editor_toolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(workoutName);
    }

    RecyclerView advancedStepList = findViewById(R.id.new_advnced_workout_steps);
    advancedStepList.setLayoutManager(new LinearLayoutManager(this));
    advancedWorkoutStepsAdapter = new WorkoutEditorStepsAdapter(this, onWorkoutChanged);
    advancedWorkoutStepsAdapter.attachToRecyclerView(advancedStepList);
    advancedStepList.setAdapter(advancedWorkoutStepsAdapter);

    try {
      createAdvancedWorkout(workoutName, workoutExists);
    } catch (Exception e) {
      handleWorkoutFileException(e);
      return;
    }

    ViewUtil.Insets(findViewById(R.id.create_advanced_workout_view), true);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.workout_editor_menu, menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuItem discard = menu.findItem(R.id.menu_workout_discard);
    if (discard != null) {
      discard.setVisible(!workoutExists);
    }
    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.menu_workout_add_step) {
      addStep();
      return true;
    }
    if (id == R.id.menu_workout_add_repeat) {
      addRepeat();
      return true;
    }
    if (id == R.id.menu_workout_rename) {
      showRenameDialog();
      return true;
    }
    if (id == R.id.menu_workout_save) {
      saveAndFinish();
      return true;
    }
    if (id == R.id.menu_workout_discard) {
      confirmDiscard();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public boolean onSupportNavigateUp() {
    finish();
    return true;
  }

  private void updateToolbarTitle() {
    if (getSupportActionBar() != null) {
      getSupportActionBar().setTitle(workoutName);
    }
  }

  private void createAdvancedWorkout(String name, boolean exists)
      throws JSONException, IOException {
    if (exists) {
      advancedWorkout = WorkoutSerializer.readFile(getApplicationContext(), name);
    } else {
      advancedWorkout = new Workout();
      WorkoutSerializer.writeFile(getApplicationContext(), name, advancedWorkout);
    }
    refreshStepList();
  }

  private void refreshStepList() {
    if (advancedWorkout != null) {
      advancedWorkoutStepsAdapter.setWorkout(advancedWorkout);
    }
  }

  private final Runnable onWorkoutChanged =
      () -> {
        if (advancedWorkout == null) {
          return;
        }
        Context ctx = getApplicationContext();
        try {
          WorkoutSerializer.writeFile(ctx, workoutName, advancedWorkout);
        } catch (Exception ex) {
          new AlertDialog.Builder(CreateAdvancedWorkout.this, R.style.AlertDialogTheme)
              .setTitle(org.runnerup.common.R.string.Failed_to_load_workout)
              .setMessage("" + ex)
              .setPositiveButton(
                  org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
              .show();
        }
      };

  private void addStep() {
    if (advancedWorkout == null) {
      return;
    }
    advancedWorkout.addStep(new Step());
    refreshStepList();
  }

  private void addRepeat() {
    if (advancedWorkout == null) {
      return;
    }
    advancedWorkout.addStep(new RepeatStep());
    refreshStepList();
  }

  private void saveAndFinish() {
    if (advancedWorkout == null) {
      return;
    }
    try {
      WorkoutSerializer.writeFile(getApplicationContext(), workoutName, advancedWorkout);
      finish();
    } catch (Exception e) {
      handleWorkoutFileException(e);
    }
  }

  private void showRenameDialog() {
    final EditText input = new EditText(this);
    input.setText(workoutName);
    input.setSelection(input.getText().length());
    new AlertDialog.Builder(this, R.style.AlertDialogTheme)
        .setTitle(R.string.Rename_workout)
        .setMessage(org.runnerup.common.R.string.Set_workout_name)
        .setView(input)
        .setPositiveButton(
            org.runnerup.common.R.string.OK,
            (dialog, which) -> {
              String newName = input.getText().toString().trim();
              if (newName.isEmpty()) {
                return;
              }
              if (newName.equals(workoutName)) {
                return;
              }
              renameWorkout(newName);
            })
        .setNegativeButton(
            org.runnerup.common.R.string.Cancel, (dialog, which) -> dialog.dismiss())
        .show();
  }

  private void renameWorkout(String newName) {
    Context ctx = getApplicationContext();
    File oldFile = WorkoutSerializer.getFile(ctx, workoutName);
    File newFile = WorkoutSerializer.getFile(ctx, newName);
    if (newFile.exists()) {
      Toast.makeText(this, R.string.Workout_name_exists, Toast.LENGTH_LONG).show();
      return;
    }
    if (!oldFile.renameTo(newFile)) {
      Toast.makeText(
              this,
              getString(org.runnerup.common.R.string.Failed_to_create_workout),
              Toast.LENGTH_LONG)
          .show();
      return;
    }
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
    String prefKey = getString(R.string.pref_advanced_workout);
    if (workoutName.contentEquals(pref.getString(prefKey, ""))) {
      pref.edit().putString(prefKey, newName).apply();
    }
    workoutName = newName;
    updateToolbarTitle();
    onWorkoutChanged.run();
  }

  private void handleWorkoutFileException(Exception e) {
    new AlertDialog.Builder(CreateAdvancedWorkout.this, R.style.AlertDialogTheme)
        .setTitle(getString(org.runnerup.common.R.string.Failed_to_create_workout))
        .setMessage(e.toString())
        .setPositiveButton(org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
        .show();
  }

  private void confirmDiscard() {
    new AlertDialog.Builder(CreateAdvancedWorkout.this, R.style.AlertDialogTheme)
        .setTitle(org.runnerup.common.R.string.Delete_workout)
        .setMessage(org.runnerup.common.R.string.Are_you_sure)
        .setPositiveButton(
            org.runnerup.common.R.string.Yes,
            (dialog, which) -> {
              dialog.dismiss();
              File f = WorkoutSerializer.getFile(getApplicationContext(), workoutName);
              //noinspection ResultOfMethodCallIgnored
              f.delete();
              finish();
            })
        .setNegativeButton(org.runnerup.common.R.string.No, (dialog, which) -> dialog.dismiss())
        .show();
  }
}
