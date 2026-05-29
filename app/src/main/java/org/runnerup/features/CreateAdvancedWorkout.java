package org.runnerup.features;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.IOException;
import org.json.JSONException;
import org.runnerup.R;
import org.runnerup.core.util.ViewUtil;
import org.runnerup.ui.common.widget.TitleSpinner;
import org.runnerup.core.workout.RepeatStep;
import org.runnerup.core.workout.Step;
import org.runnerup.core.workout.Workout;
import org.runnerup.core.workout.WorkoutSerializer;

public class CreateAdvancedWorkout extends AppCompatActivity {

  private Workout advancedWorkout = null;
  private TitleSpinner advancedWorkoutSpinner = null;
  private WorkoutEditorStepsAdapter advancedWorkoutStepsAdapter = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

    setContentView(R.layout.create_advanced_workout);

    Intent intent = getIntent();
    String advWorkoutName = intent.getStringExtra(ManageWorkoutsActivity.WORKOUT_NAME);
    boolean workoutExists = intent.getBooleanExtra(ManageWorkoutsActivity.WORKOUT_EXISTS, false);

    advancedWorkoutSpinner = findViewById(R.id.new_workout_spinner);
    advancedWorkoutSpinner.setValue(advWorkoutName);
    advancedWorkoutSpinner.setEnabled(false);

    RecyclerView advancedStepList = findViewById(R.id.new_advnced_workout_steps);
    advancedStepList.setLayoutManager(new LinearLayoutManager(this));
    advancedWorkoutStepsAdapter = new WorkoutEditorStepsAdapter(this, onWorkoutChanged);
    advancedStepList.setAdapter(advancedWorkoutStepsAdapter);
    advancedWorkoutStepsAdapter.attachToRecyclerView(advancedStepList);

    Button addStepButton = findViewById(R.id.add_step_button);
    addStepButton.setOnClickListener(addStepButtonClick);

    Button addRepeatButton = findViewById(R.id.add_repeat_button);
    addRepeatButton.setOnClickListener(addRepeatStepButtonClick);

    Button saveWorkoutButton = findViewById(R.id.workout_save_button);
    saveWorkoutButton.setOnClickListener(saveWorkoutButtonClick);

    Button discardWorkoutButton = findViewById(R.id.workout_discard_button);
    discardWorkoutButton.setOnClickListener(discardWorkoutButtonClick);

    if (workoutExists) {
      discardWorkoutButton.setVisibility(View.GONE);
    }

    try {
      createAdvancedWorkout(advWorkoutName, workoutExists);
    } catch (Exception e) {
      handleWorkoutFileException(e);
    }

    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar()
          .setBackgroundDrawable(
              androidx.core.content.ContextCompat.getDrawable(this, R.color.backgroundPrimary));
    }

    ViewUtil.Insets(findViewById(R.id.create_advanced_workout_view), true);
  }

  @Override
  public boolean onSupportNavigateUp() {
    finish();
    return true;
  }

  private void createAdvancedWorkout(String name, boolean workoutExists)
      throws JSONException, IOException {
    if (workoutExists) {
      advancedWorkout = WorkoutSerializer.readFile(getApplicationContext(), name);
    } else {
      advancedWorkout = new Workout();
      WorkoutSerializer.writeFile(getApplicationContext(), name, advancedWorkout);
    }
    refreshStepList();
  }

  private void refreshStepList() {
    advancedWorkoutStepsAdapter.setWorkout(advancedWorkout);
  }

  private final Runnable onWorkoutChanged =
      () -> {
        String advWorkoutName = advancedWorkoutSpinner.getValue().toString();
        if (advancedWorkout != null) {
          Context ctx = getApplicationContext();
          try {
            WorkoutSerializer.writeFile(ctx, advWorkoutName, advancedWorkout);
          } catch (Exception ex) {
            new AlertDialog.Builder(CreateAdvancedWorkout.this)
                .setTitle(org.runnerup.common.R.string.Failed_to_load_workout)
                .setMessage("" + ex)
                .setPositiveButton(
                    org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
                .show();
          }
        }
      };

  private final View.OnClickListener addStepButtonClick =
      v -> {
        advancedWorkout.addStep(new Step());
        refreshStepList();
      };

  private final View.OnClickListener addRepeatStepButtonClick =
      view -> {
        advancedWorkout.addStep(new RepeatStep());
        refreshStepList();
      };

  private final View.OnClickListener saveWorkoutButtonClick =
      v -> {
        try {
          String advWorkoutName = advancedWorkoutSpinner.getValue().toString();
          WorkoutSerializer.writeFile(getApplicationContext(), advWorkoutName, advancedWorkout);
          finish();
        } catch (Exception e) {
          handleWorkoutFileException(e);
        }
      };

  private void handleWorkoutFileException(Exception e) {
    new AlertDialog.Builder(CreateAdvancedWorkout.this)
        .setTitle(getString(org.runnerup.common.R.string.Failed_to_create_workout))
        .setMessage(e.toString())
        .setPositiveButton(org.runnerup.common.R.string.OK, (dialog, which) -> dialog.dismiss())
        .show();
  }

  private final View.OnClickListener discardWorkoutButtonClick =
      view ->
          new AlertDialog.Builder(CreateAdvancedWorkout.this)
              .setTitle(org.runnerup.common.R.string.Delete_workout)
              .setMessage(org.runnerup.common.R.string.Are_you_sure)
              .setPositiveButton(
                  org.runnerup.common.R.string.Yes,
                  (dialog, which) -> {
                    dialog.dismiss();
                    String name = advancedWorkoutSpinner.getValue().toString();
                    File f = WorkoutSerializer.getFile(getApplicationContext(), name);
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                    finish();
                  })
              .setNegativeButton(
                  org.runnerup.common.R.string.No, (dialog, which) -> dialog.dismiss())
              .show();
}
