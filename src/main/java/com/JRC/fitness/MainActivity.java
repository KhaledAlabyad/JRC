package com.JRC.fitness;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class MainActivity extends Activity {

    private DataStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new DataStore(this);

        Button squatButton = findViewById(R.id.homeSquatButton);
        Button jumpButton = findViewById(R.id.homeJumpButton);
        Button statsButton = findViewById(R.id.homeStatsButton);

        squatButton.setOnClickListener(v -> openExercise(ExerciseType.SQUAT));
        jumpButton.setOnClickListener(v -> openExercise(ExerciseType.JUMP));
        statsButton.setOnClickListener(v -> startActivity(new Intent(this, StatsActivity.class)));
    }

    private void openExercise(ExerciseType type) {
        Class<?> target = store.hasCalibration(type)
                ? (type == ExerciseType.SQUAT ? SquatTrainingActivity.class : JumpTrainingActivity.class)
                : CalibrationActivity.class;
        Intent i = new Intent(this, target);
        if (target == CalibrationActivity.class) {
            i.putExtra(CalibrationActivity.EXTRA_TYPE, type.key);
        }
        startActivity(i);
    }
}
