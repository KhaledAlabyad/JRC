package com.JRC.fitness;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StatsActivity extends Activity {

    private DataStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        store = new DataStore(this);

        TextView squatSummary = findViewById(R.id.statsSquatSummary);
        TextView jumpSummary = findViewById(R.id.statsJumpSummary);
        squatSummary.setText(buildSummary(ExerciseType.SQUAT));
        jumpSummary.setText(buildSummary(ExerciseType.JUMP));

        EditText squatGoalInput = findViewById(R.id.statsSquatGoalInput);
        EditText jumpGoalInput = findViewById(R.id.statsJumpGoalInput);
        Button squatGoalSave = findViewById(R.id.statsSquatGoalSave);
        Button jumpGoalSave = findViewById(R.id.statsJumpGoalSave);

        int squatGoal = store.getGoalReps(ExerciseType.SQUAT);
        int jumpGoal = store.getGoalReps(ExerciseType.JUMP);
        if (squatGoal > 0) squatGoalInput.setText(String.valueOf(squatGoal));
        if (jumpGoal > 0) jumpGoalInput.setText(String.valueOf(jumpGoal));

        squatGoalSave.setOnClickListener(v -> saveGoal(ExerciseType.SQUAT, squatGoalInput));
        jumpGoalSave.setOnClickListener(v -> saveGoal(ExerciseType.JUMP, jumpGoalInput));

        ListView historyList = findViewById(R.id.statsHistoryList);
        List<Session> all = store.getSessions(null);
        List<String> rows = new ArrayList<>();
        SimpleDateFormat fmt = new SimpleDateFormat("MMM d, HH:mm", Locale.US);
        int shown = 0;
        for (Session s : all) {
            if (shown >= 30) break;
            String label = "squat".equals(s.type) ? ExerciseType.SQUAT.label : ExerciseType.JUMP.label;
            rows.add(String.format(Locale.US, "%s  -  %d reps  -  %s  -  %.2f reps/s  -  %s",
                    label, s.reps, formatDuration(s.durationMs), s.repsPerSecond(), fmt.format(s.timestampMillis)));
            shown++;
        }
        if (rows.isEmpty()) rows.add("No sessions yet — finish a training session to see it here.");
        historyList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
    }

    private void saveGoal(ExerciseType type, EditText input) {
        String text = input.getText().toString().trim();
        int goal = 0;
        if (!text.isEmpty()) {
            try {
                goal = Math.max(0, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
            }
        }
        store.setGoalReps(type, goal);
    }

    private String buildSummary(ExerciseType type) {
        List<Session> sessions = store.getSessions(type);
        if (sessions.isEmpty()) {
            return type.label + ": no sessions yet.";
        }
        int totalReps = 0;
        double bestPace = 0;
        for (Session s : sessions) {
            totalReps += s.reps;
            bestPace = Math.max(bestPace, s.repsPerSecond());
        }
        double avgReps = totalReps / (double) sessions.size();
        return String.format(Locale.US,
                "%s: %d sessions, %d total reps, avg %.1f reps/session, best pace %.2f reps/s",
                type.label, sessions.size(), totalReps, avgReps, bestPace);
    }

    private static String formatDuration(long ms) {
        long totalSec = ms / 1000;
        return String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60);
    }
}
