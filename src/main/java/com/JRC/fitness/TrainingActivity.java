package com.JRC.fitness;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;

/**
 * Shared behaviour for a single-exercise training window:
 *  - waits ("Get ready") until the first real rep is detected, then starts the clock
 *    (auto-start, no manual "begin" step)
 *  - live rep count, elapsed time, reps/sec
 *  - live progress against a saved goal, if one is set
 *  - saves a Session to history and returns to Main when the user stops
 *
 * Subclasses (SquatTrainingActivity / JumpTrainingActivity) only wire up the
 * actual sensor and build a RepDetector from the saved CalibrationData.
 */
public abstract class TrainingActivity extends Activity {

    protected DataStore store;
    protected CalibrationData calibration;
    protected RepDetector detector;

    private int repCount = 0;
    private long sessionStartTime = 0; // 0 until first rep arrives
    private boolean stopped = false;

    private TextView titleText;
    private TextView statusText;
    private TextView repCountText;
    private TextView elapsedText;
    private TextView paceText;
    private TextView goalText;
    private Button stopButton;
    private Button recalibrateButton;

    private final Handler tickHandler = new Handler();
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshStats();
            if (!stopped) tickHandler.postDelayed(this, 250);
        }
    };

    protected abstract ExerciseType getType();
    protected abstract void startSensors();
    protected abstract void stopSensors();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_training);
        store = new DataStore(this);

        titleText = findViewById(R.id.trainTitle);
        statusText = findViewById(R.id.trainStatus);
        repCountText = findViewById(R.id.trainRepCount);
        elapsedText = findViewById(R.id.trainElapsed);
        paceText = findViewById(R.id.trainPace);
        goalText = findViewById(R.id.trainGoal);
        stopButton = findViewById(R.id.trainStopButton);
        recalibrateButton = findViewById(R.id.trainRecalibrateButton);

        titleText.setText(getType().label);
        stopButton.setOnClickListener(v -> finishSession());
        recalibrateButton.setOnClickListener(v -> {
            Intent i = new Intent(this, CalibrationActivity.class);
            i.putExtra(CalibrationActivity.EXTRA_TYPE, getType().key);
            startActivity(i);
            finish();
        });

        calibration = store.getCalibration(getType());
        if (calibration == null) {
            // Safety net: shouldn't normally happen since Main routes through
            // calibration first, but avoid a crash if it does.
            Intent i = new Intent(this, CalibrationActivity.class);
            i.putExtra(CalibrationActivity.EXTRA_TYPE, getType().key);
            startActivity(i);
            finish();
            return;
        }

        showGoal();
        statusText.setText("Get ready — start your " + getType().label.toLowerCase() + " to begin the session.");
        startSensors();
        tickHandler.postDelayed(ticker, 250);
    }

    /** Subclasses call this from their sensor callback for every candidate rep. */
    protected void onRepDetected(long timestampMillis) {
        if (sessionStartTime == 0) {
            sessionStartTime = timestampMillis;
            runOnUiThread(() -> statusText.setText("Session started!"));
        }
        repCount++;
        runOnUiThread(this::refreshStats);
    }

    private void refreshStats() {
        repCountText.setText(String.valueOf(repCount));
        long elapsedMs = sessionStartTime == 0 ? 0 : System.currentTimeMillis() - sessionStartTime;
        elapsedText.setText(formatElapsed(elapsedMs));
        double pace = elapsedMs > 0 ? repCount / (elapsedMs / 1000.0) : 0;
        paceText.setText(String.format(Locale.US, "%.2f reps/sec", pace));
        showGoal();
    }

    private void showGoal() {
        int goalReps = store.getGoalReps(getType());
        if (goalReps > 0) {
            goalText.setText("Goal: " + repCount + " / " + goalReps + " reps");
            goalText.setVisibility(View.VISIBLE);
        } else {
            goalText.setVisibility(View.GONE);
        }
    }

    private static String formatElapsed(long ms) {
        long totalSec = ms / 1000;
        long m = totalSec / 60;
        long s = totalSec % 60;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private void finishSession() {
        if (stopped) return;
        stopped = true;
        stopSensors();
        tickHandler.removeCallbacks(ticker);

        if (repCount > 0 && sessionStartTime > 0) {
            long durationMs = System.currentTimeMillis() - sessionStartTime;
            store.addSession(new Session(getType().key, System.currentTimeMillis(), repCount, durationMs));
        }
        startActivity(new Intent(this, StatsActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopped = true;
        tickHandler.removeCallbacks(ticker);
        stopSensors();
    }
}
