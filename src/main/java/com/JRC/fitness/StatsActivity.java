package com.JRC.fitness;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stats & goals screen, split into a Squats tab and a Jump Rope tab.
 * Each tab shows summary cards, a bar chart of recent sessions, a goal
 * editor, and a readable (light-on-dark) list of recent sessions.
 */
public class StatsActivity extends Activity {

    public static final String EXTRA_TYPE = "exercise_type";

    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final long WEEK_MS = 7 * DAY_MS;

    private DataStore store;

    private Button tabSquatButton;
    private Button tabJumpButton;
    private View squatPanel;
    private View jumpPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        setContentView(R.layout.activity_stats);
        store = new DataStore(this);

        tabSquatButton = findViewById(R.id.tabSquatButton);
        tabJumpButton = findViewById(R.id.tabJumpButton);
        squatPanel = findViewById(R.id.squatPanel);
        jumpPanel = findViewById(R.id.jumpPanel);

        tabSquatButton.setOnClickListener(v -> selectTab(true));
        tabJumpButton.setOnClickListener(v -> selectTab(false));

        populatePanel(ExerciseType.SQUAT);
        populatePanel(ExerciseType.JUMP);

        // Land on whichever exercise's tab the caller just finished a session for.
        String requestedType = getIntent().getStringExtra(EXTRA_TYPE);
        selectTab(!ExerciseType.JUMP.key.equals(requestedType));
    }

    private void selectTab(boolean squat) {
        squatPanel.setVisibility(squat ? View.VISIBLE : View.GONE);
        jumpPanel.setVisibility(squat ? View.GONE : View.VISIBLE);
        tabSquatButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor(squat ? "#4CAF50" : "#2A2A2A")));
        tabSquatButton.setTextColor(Color.parseColor(squat ? "#FFFFFF" : "#CCCCCC"));
        tabJumpButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor(squat ? "#2A2A2A" : "#4CAF50")));
        tabJumpButton.setTextColor(Color.parseColor(squat ? "#CCCCCC" : "#FFFFFF"));
    }

    private void populatePanel(ExerciseType type) {
        boolean squat = type == ExerciseType.SQUAT;
        List<Session> sessions = store.getSessions(type); // newest first

        int avgPerDayId = squat ? R.id.squatAvgPerDay : R.id.jumpAvgPerDay;
        int weekChangeId = squat ? R.id.squatWeekChange : R.id.jumpWeekChange;
        int avgRepsId = squat ? R.id.squatAvgReps : R.id.jumpAvgReps;
        int chartId = squat ? R.id.squatChart : R.id.jumpChart;
        int goalInputId = squat ? R.id.statsSquatGoalInput : R.id.statsJumpGoalInput;
        int goalSaveId = squat ? R.id.statsSquatGoalSave : R.id.statsJumpGoalSave;
        int containerId = squat ? R.id.squatSessionsContainer : R.id.jumpSessionsContainer;

        int totalReps = 0;
        double bestPace = 0;
        Set<String> trainingDays = new HashSet<>();
        SimpleDateFormat dayKeyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        for (Session s : sessions) {
            totalReps += s.reps;
            bestPace = Math.max(bestPace, s.repsPerSecond());
            trainingDays.add(dayKeyFmt.format(s.timestampMillis));
        }
        double avgReps = sessions.isEmpty() ? 0 : totalReps / (double) sessions.size();
        double avgPerDay = trainingDays.isEmpty() ? 0 : totalReps / (double) trainingDays.size();

        ((TextView) findViewById(avgPerDayId)).setText(String.format(Locale.US, "%.1f", avgPerDay));
        ((TextView) findViewById(weekChangeId)).setText(weekOverWeekChangeText(sessions));
        ((TextView) findViewById(avgRepsId)).setText(String.format(Locale.US, "%.1f", avgReps));

        // Best pace card only exists for Jump Rope now.
        if (!squat) {
            TextView bestPaceText = findViewById(R.id.jumpBestPace);
            if (bestPaceText != null) {
                bestPaceText.setText(String.format(Locale.US, "%.2f", bestPace));
            }
        }

        // Chart wants oldest -> newest, last 8 sessions.
        List<Session> chronological = new ArrayList<>(sessions);
        Collections.reverse(chronological);
        int from = Math.max(0, chronological.size() - 8);
        List<Session> recent = chronological.subList(from, chronological.size());
        List<Float> repsSeries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        SimpleDateFormat dayFmt = new SimpleDateFormat("M/d", Locale.US);
        for (Session s : recent) {
            repsSeries.add((float) s.reps);
            labels.add(dayFmt.format(s.timestampMillis));
        }
        ((BarChartView) findViewById(chartId)).setData(repsSeries, labels, "%.0f");

        EditText goalInput = findViewById(goalInputId);
        int goal = store.getGoalReps(type);
        goalInput.setText(goal > 0 ? String.valueOf(goal) : "");
        findViewById(goalSaveId).setOnClickListener(v -> {
            String text = goalInput.getText().toString().trim();
            int newGoal = 0;
            if (!text.isEmpty()) {
                try {
                    newGoal = Math.max(0, Integer.parseInt(text));
                } catch (NumberFormatException ignored) {
                }
            }
            store.setGoalReps(type, newGoal);
        });

        buildSessionRows(sessions, (LinearLayout) findViewById(containerId));
    }

    /** Total reps in the last 7 days vs the 7 days before that, as a signed percentage string. */
    private static String weekOverWeekChangeText(List<Session> sessions) {
        long now = System.currentTimeMillis();
        long thisWeekStart = now - WEEK_MS;
        long lastWeekStart = now - 2 * WEEK_MS;

        int thisWeekReps = 0;
        int lastWeekReps = 0;
        for (Session s : sessions) {
            if (s.timestampMillis >= thisWeekStart) {
                thisWeekReps += s.reps;
            } else if (s.timestampMillis >= lastWeekStart) {
                lastWeekReps += s.reps;
            }
        }

        if (lastWeekReps == 0) {
            return thisWeekReps > 0 ? "New" : "—";
        }
        double percent = ((thisWeekReps - lastWeekReps) / (double) lastWeekReps) * 100.0;
        return String.format(Locale.US, "%s%.0f%%", percent >= 0 ? "+" : "", percent);
    }

    private void buildSessionRows(List<Session> sessions, LinearLayout container) {
        container.removeAllViews();
        if (sessions.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No sessions yet — finish a training session to see it here.");
            empty.setTextColor(Color.parseColor("#888888"));
            empty.setTextSize(13);
            empty.setPadding(0, 8, 0, 8);
            container.addView(empty);
            return;
        }

        SimpleDateFormat fmt = new SimpleDateFormat("MMM d, HH:mm", Locale.US);
        int shown = 0;
        for (Session s : sessions) {
            if (shown >= 20) break;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(16, 12, 16, 12);
            row.setBackgroundColor(Color.parseColor(shown % 2 == 0 ? "#1A1A1A" : "#161616"));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = 4;
            row.setLayoutParams(rowParams);

            TextView primary = new TextView(this);
            primary.setText(String.format(Locale.US, "%d reps  ·  %s  ·  %.2f reps/s",
                    s.reps, formatDuration(s.durationMs), s.repsPerSecond()));
            primary.setTextColor(Color.parseColor("#FFFFFF"));
            primary.setTextSize(15);

            TextView secondary = new TextView(this);
            secondary.setText(fmt.format(s.timestampMillis));
            secondary.setTextColor(Color.parseColor("#888888"));
            secondary.setTextSize(12);
            secondary.setPadding(0, 2, 0, 0);

            row.addView(primary);
            row.addView(secondary);
            container.addView(row);
            shown++;
        }
    }

    private static String formatDuration(long ms) {
        long totalSec = ms / 1000;
        return String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60);
    }
}
