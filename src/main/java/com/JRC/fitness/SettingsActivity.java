package com.JRC.fitness;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private DataStore store;
    private EditText squatBeepInput;
    private EditText jumpBeepInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        setContentView(R.layout.activity_settings);
        store = new DataStore(this);

        findViewById(R.id.settingsUserGuideButton).setOnClickListener(
                v -> startActivity(new Intent(this, UserGuideActivity.class)));
        findViewById(R.id.settingsGoalsButton).setOnClickListener(
                v -> startActivity(new Intent(this, GoalsActivity.class)));
        findViewById(R.id.settingsAboutButton).setOnClickListener(
                v -> startActivity(new Intent(this, AboutDevActivity.class)));

        squatBeepInput = findViewById(R.id.settingsSquatBeepInput);
        jumpBeepInput = findViewById(R.id.settingsJumpBeepInput);

        int squatBeep = store.getBeepInterval(ExerciseType.SQUAT);
        int jumpBeep = store.getBeepInterval(ExerciseType.JUMP);
        squatBeepInput.setText(squatBeep > 0 ? String.valueOf(squatBeep) : "");
        jumpBeepInput.setText(jumpBeep > 0 ? String.valueOf(jumpBeep) : "");

        findViewById(R.id.settingsSaveBeepButton).setOnClickListener(v -> saveBeepSettings());
    }

    private void saveBeepSettings() {
        store.setBeepInterval(ExerciseType.SQUAT, parseNonNegative(squatBeepInput));
        store.setBeepInterval(ExerciseType.JUMP, parseNonNegative(jumpBeepInput));
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
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
