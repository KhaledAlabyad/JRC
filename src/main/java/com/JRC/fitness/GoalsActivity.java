package com.JRC.fitness;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

public class GoalsActivity extends Activity {

    private DataStore store;
    private EditText squatInput;
    private EditText jumpInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        setContentView(R.layout.activity_goals);
        store = new DataStore(this);

        squatInput = findViewById(R.id.goalsSquatInput);
        jumpInput = findViewById(R.id.goalsJumpInput);

        int squatGoal = store.getGoalReps(ExerciseType.SQUAT);
        int jumpGoal = store.getGoalReps(ExerciseType.JUMP);
        squatInput.setText(squatGoal > 0 ? String.valueOf(squatGoal) : "");
        jumpInput.setText(jumpGoal > 0 ? String.valueOf(jumpGoal) : "");

        findViewById(R.id.goalsSaveButton).setOnClickListener(v -> {
            store.setGoalReps(ExerciseType.SQUAT, parseNonNegative(squatInput));
            store.setGoalReps(ExerciseType.JUMP, parseNonNegative(jumpInput));
            Toast.makeText(this, "Goals saved", Toast.LENGTH_SHORT).show();
        });
    }

    private static int parseNonNegative(EditText input) {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return 0;
        try {
            return Math.max(0, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
