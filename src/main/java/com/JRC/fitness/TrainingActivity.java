package com.JRC.fitness;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;

/**
 * Shared behaviour for a single-exercise training window:
 *  - starts counting immediately on the first real rep (no manual "begin" step)
 *  - live rep count (with the goal shown inline, smaller, e.g. "24 / 50")
 *  - tapping the rep count pauses/resumes counting (color changes to indicate
 *    the paused state)
 *  - saves a Session to history and returns to the matching Stats tab when
 *    the user stops
 *
 * Subclasses (SquatTrainingActivity / JumpTrainingActivity) only wire up the
 * actual sensor and build a RepDetector from the saved CalibrationData.
 */
public abstract class TrainingActivity extends Activity {

    private static final String COLOR_NORMAL = "#FFFFFF";
    private static final String COLOR_PAUSED = "#FFF59D"; // light yellow

    protected DataStore store;
    protected CalibrationData calibration;
    protected RepDetector detector;

    private int repCount = 0;
    private long sessionStartTime = 0; // 0 until first rep arrives
    private boolean stopped = false;
    private boolean paused = false;
    private long pauseStartTime = 0;

    private TextView titleText;
    private TextView repCountText;
    private TextView elapsedText;
    private Button stopButton;
    private Button recalibrateButton;
    private ToneGenerator toneGenerator;

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
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        setContentView(R.layout.activity_training);
        store = new DataStore(this);
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        } catch (RuntimeException e) {
            toneGenerator = null; // some devices/emulators refuse to allocate this; beeps just won't play
        }

        titleText = findViewById(R.id.trainTitle);
        repCountText = findViewById(R.id.trainRepCount);
        elapsedText = findViewById(R.id.trainElapsed);
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
        repCountText.setOnClickListener(v -> togglePause());

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

        refreshStats();
        startSensors();
        tickHandler.postDelayed(ticker, 250);
    }

    /** Subclasses call this from their sensor callback for every candidate rep. */
    protected void onRepDetected(long timestampMillis) {
        if (paused) return; // counting is paused; ignore incoming reps entirely
        if (sessionStartTime == 0) {
            sessionStartTime = timestampMillis;
        }
        repCount++;
        maybeBeep();
        runOnUiThread(this::refreshStats);
    }

    private void maybeBeep() {
        int every = store.getBeepInterval(getType());
        if (every > 0 && repCount % every == 0 && toneGenerator != null) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
        }
    }

    private void togglePause() {
        if (stopped) return;
        paused = !paused;
        if (paused) {
            pauseStartTime = System.currentTimeMillis();
            tickHandler.removeCallbacks(ticker);
            repCountText.setTextColor(Color.parseColor(COLOR_PAUSED));
        } else {
            // Shift the session start forward by however long we were paused so
            // the elapsed time display doesn't jump when we resume.
            if (sessionStartTime != 0) {
                sessionStartTime += (System.currentTimeMillis() - pauseStartTime);
            }
            repCountText.setTextColor(Color.parseColor(COLOR_NORMAL));
            tickHandler.postDelayed(ticker, 250);
            refreshStats();
        }
    }

    private void refreshStats() {
        int goalReps = store.getGoalReps(getType());
        if (goalReps > 0) {
            SpannableString combined = new SpannableString(repCount + " / " + goalReps);
            int slashIndex = String.valueOf(repCount).length();
            combined.setSpan(new RelativeSizeSpan(0.32f), slashIndex, combined.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            combined.setSpan(new ForegroundColorSpan(Color.parseColor("#888888")), slashIndex, combined.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            repCountText.setText(combined);
        } else {
            repCountText.setText(String.valueOf(repCount));
        }

        long elapsedMs = sessionStartTime == 0 ? 0 : System.currentTimeMillis() - sessionStartTime;
        elapsedText.setText(formatElapsed(elapsedMs));
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
        Intent i = new Intent(this, StatsActivity.class);
        i.putExtra(StatsActivity.EXTRA_TYPE, getType().key);
        startActivity(i);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopped = true;
        tickHandler.removeCallbacks(ticker);
        stopSensors();
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }
}
