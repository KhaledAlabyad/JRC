package com.JRC.fitness;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CalibrationActivity extends Activity implements SensorEventListener {

    public static final String EXTRA_TYPE = "exercise_type";
    private static final int PERMISSION_REQUEST_AUDIO = 2;
    private static final int AUDIO_WINDOW_MS = 20;
    private static final int PRE_ROLL_SECONDS = 3; // jump only: give the user time to get into position

    private enum Stage { READY, COUNTDOWN, RECORDING, DONE }

    private ExerciseType type;
    private DataStore store;
    private Stage stage = Stage.READY;

    private TextView titleText;
    private TextView statusText;
    private TextView countText;
    private Button actionButton;
    private Button continueButton;

    // Squat capture
    private SensorManager sensorManager;
    private Sensor accelerometer;

    // Jump capture
    private AudioAmplitudeSource audioSource;
    private final Handler countdownHandler = new Handler();

    private final List<Float> values = new ArrayList<>();
    private final List<Long> times = new ArrayList<>();
    // Zero-crossing rate per window, parallel to values/times (JUMP only). Used to
    // learn the frequency "shape" of a real rep sound so unrelated noises (finger
    // snaps, taps, claps) don't get counted even if they're loud enough to.
    private final List<Float> zcrValues = new ArrayList<>();
    private long recordStartTime;

    // live rough count during recording, just to show progress toward the target
    private RepDetector liveDetector;
    private int liveCount = 0;

    private CalibrationData result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        setContentView(R.layout.activity_calibration);

        String typeKey = getIntent().getStringExtra(EXTRA_TYPE);
        type = ExerciseType.JUMP.key.equals(typeKey) ? ExerciseType.JUMP : ExerciseType.SQUAT;
        store = new DataStore(this);

        titleText = findViewById(R.id.calibTitle);
        statusText = findViewById(R.id.calibStatus);
        countText = findViewById(R.id.calibCount);
        actionButton = findViewById(R.id.calibActionButton);
        continueButton = findViewById(R.id.calibContinueButton);

        titleText.setText("Calibrate: " + type.label);
        renderReady();

        actionButton.setOnClickListener(v -> onActionClicked());
        continueButton.setOnClickListener(v -> goToTraining());

        if (type == ExerciseType.SQUAT) {
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
        }
    }

    private void onActionClicked() {
        if (stage == Stage.READY) {
            if (type == ExerciseType.JUMP) {
                startPreRollCountdown();
            } else {
                startRecording();
            }
        } else if (stage == Stage.RECORDING) {
            stopRecordingAndAnalyze();
        } else if (stage == Stage.DONE) {
            // DONE -> recalibrate from scratch
            values.clear();
            times.clear();
            zcrValues.clear();
            liveCount = 0;
            renderReady();
        }
        // COUNTDOWN: button is disabled, nothing to do.
    }

    private void renderReady() {
        stage = Stage.READY;
        statusText.setText("Perform exactly " + type.calibrationReps + " " + type.label.toLowerCase()
                + " at your normal pace after you tap Start. Tap Stop as soon as you finish the last rep.");
        countText.setText("");
        actionButton.setText("Start");
        actionButton.setEnabled(true);
        continueButton.setVisibility(View.GONE);
    }

    /** Jump rope only: 3-2-1 countdown so the user has time to get into position before we start listening. */
    private void startPreRollCountdown() {
        stage = Stage.COUNTDOWN;
        actionButton.setEnabled(false);
        continueButton.setVisibility(View.GONE);
        countdownHandler.post(new Runnable() {
            int remaining = PRE_ROLL_SECONDS;

            @Override
            public void run() {
                if (stage != Stage.COUNTDOWN) return; // cancelled (e.g. activity finishing)
                if (remaining <= 0) {
                    startRecording();
                    return;
                }
                statusText.setText("Get ready...");
                countText.setText(String.valueOf(remaining));
                remaining--;
                countdownHandler.postDelayed(this, 1000);
            }
        });
    }

    private void startRecording() {
        values.clear();
        times.clear();
        zcrValues.clear();
        liveCount = 0;
        recordStartTime = System.currentTimeMillis();
        stage = Stage.RECORDING;
        statusText.setText("Recording... perform your " + type.calibrationReps + " reps now.");
        actionButton.setText("Stop");
        actionButton.setEnabled(true);
        continueButton.setVisibility(View.GONE);
        updateCount();

        if (type == ExerciseType.SQUAT) {
            // Generous provisional band just to show a live count; real thresholds
            // are computed from the recorded data once Stop is tapped.
            liveDetector = new RepDetector(8.5f, 11.0f, 300, 0.25f, ts -> {
                liveCount++;
                runOnUiThread(this::updateCount);
            });
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
        } else {
            startAudioCapture();
        }
    }

    private void stopRecordingAndAnalyze() {
        stage = Stage.DONE;
        if (type == ExerciseType.SQUAT) {
            if (sensorManager != null) sensorManager.unregisterListener(this);
        } else {
            stopAudioCapture();
        }

        long duration = System.currentTimeMillis() - recordStartTime;
        result = (type == ExerciseType.SQUAT)
                ? analyzeSquat(values, times, duration, type.calibrationReps)
                : analyzeJump(values, times, zcrValues, duration, type.calibrationReps);

        store.saveCalibration(type, result);

        statusText.setText("Calibration saved for " + type.label + ". You can redo this any time from the "
                + type.label + " screen.");
        countText.setText("Captured " + values.size() + " samples over " + (duration / 1000) + "s.");
        actionButton.setText("Recalibrate");
        continueButton.setVisibility(View.VISIBLE);
    }

    private void updateCount() {
        countText.setText(liveCount + " / " + type.calibrationReps + " (approx.)");
    }

    private void goToTraining() {
        Class<?> target = (type == ExerciseType.SQUAT) ? SquatTrainingActivity.class : JumpTrainingActivity.class;
        startActivity(new Intent(this, target));
        finish();
    }

    // ---------- Squat sensor capture ----------

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (stage != Stage.RECORDING || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        long now = System.currentTimeMillis();
        values.add(magnitude);
        times.add(now);
        if (liveDetector != null) liveDetector.feed(magnitude, now);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ---------- Jump audio capture ----------

    private void startAudioCapture() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_AUDIO);
            return;
        }
        // No ZCR gate yet during calibration itself - we don't know the band until
        // we've captured and analyzed this very recording - so the live count is
        // amplitude-only and approximate, same as before.
        liveDetector = new RepDetector(3000, 8000, 250, 0.4f, ts -> {
            liveCount++;
            runOnUiThread(this::updateCount);
        });
        audioSource = new AudioAmplitudeSource(AUDIO_WINDOW_MS, (amplitude, zcr, ts) -> {
            if (stage != Stage.RECORDING) return;
            values.add(amplitude);
            times.add(ts);
            zcrValues.add(zcr);
            if (liveDetector != null) liveDetector.feed(amplitude, ts);
        });
        if (!audioSource.start()) {
            statusText.setText("Couldn't access the microphone. Check app permissions and try again.");
            stage = Stage.READY;
            actionButton.setText("Start");
        }
    }

    private void stopAudioCapture() {
        if (audioSource != null) {
            audioSource.stop();
            audioSource = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAudioCapture();
            } else {
                statusText.setText("Microphone permission is required to calibrate jump rope.");
                stage = Stage.READY;
                actionButton.setText("Start");
            }
        }
    }

    // ---------- Analysis ----------

    private static CalibrationData analyzeSquat(List<Float> values, List<Long> times, long durationMs, int reps) {
        if (values.isEmpty()) {
            // Fallback to safe, wide defaults if nothing was captured.
            return new CalibrationData(8.0f, 11.5f, 400);
        }
        // Baseline = resting magnitude, sampled from the first ~400ms before movement typically starts.
        float baseline = averageWithin(values, times, times.get(0), 400);
        if (Float.isNaN(baseline)) baseline = 9.8f;

        List<Float> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        float min = percentile(sorted, 5);
        float max = percentile(sorted, 95);

        float low = baseline - 0.5f * Math.max(0.8f, (baseline - min));
        float high = baseline + 0.35f * Math.max(0.8f, (max - baseline));
        // Safety margins so the band never collapses to noise width.
        if (baseline - low < 0.6f) low = baseline - 0.6f;
        if (high - baseline < 0.6f) high = baseline + 0.6f;

        long minInterval = repIntervalMs(durationMs, reps, 120);
        return new CalibrationData(low, high, minInterval);
    }

    private static CalibrationData analyzeJump(List<Float> values, List<Long> times, List<Float> zcrValues,
                                                 long durationMs, int reps) {
        if (values.isEmpty()) {
            return new CalibrationData(3000, 9000, 250, 0f, 0f);
        }
        List<Float> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        float floor = percentile(sorted, 30); // quiet baseline between reps
        float peak = percentile(sorted, 90);  // rep impact / sound spike

        float span = Math.max(500f, peak - floor);
        float high = floor + 0.5f * span;
        float low = floor + 0.15f * span;

        // Learn the frequency "shape" of a real rep sound: collect the zero-crossing
        // rate only from windows loud enough to be a candidate impact (foot landing
        // or rope hitting the floor - both fall in roughly the same broadband, low-ZCR
        // range), then take a robust 10th-90th percentile band from those. A quiet
        // window between reps, or a spectrally different sound like a finger snap,
        // won't be part of this band.
        List<Float> impactZcr = new ArrayList<>();
        for (int i = 0; i < values.size() && i < zcrValues.size(); i++) {
            if (values.get(i) >= high) {
                impactZcr.add(zcrValues.get(i));
            }
        }
        float zcrLow = 0f;
        float zcrHigh = 0f;
        if (!impactZcr.isEmpty()) {
            Collections.sort(impactZcr);
            zcrLow = percentile(impactZcr, 10);
            zcrHigh = percentile(impactZcr, 90);
        }

        // The two sounds of a single jump (foot + rope) are usually much closer
        // together in time than two separate jumps, so use a higher floor here than
        // for squats to reliably coalesce them into one counted rep.
        long minInterval = repIntervalMs(durationMs, reps, 150);
        return new CalibrationData(low, high, minInterval, zcrLow, zcrHigh);
    }

    private static long repIntervalMs(long durationMs, int reps, long floorMs) {
        if (reps <= 0) return Math.max(floorMs, 300);
        long avgPeriod = durationMs / reps;
        long minInterval = (long) (avgPeriod * 0.4);
        if (minInterval < floorMs) minInterval = floorMs;
        if (minInterval > 1200) minInterval = 1200;
        return minInterval;
    }

    private static float percentile(List<Float> sortedAscending, int p) {
        if (sortedAscending.isEmpty()) return Float.NaN;
        int idx = (int) Math.round((p / 100.0) * (sortedAscending.size() - 1));
        idx = Math.max(0, Math.min(sortedAscending.size() - 1, idx));
        return sortedAscending.get(idx);
    }

    private static float averageWithin(List<Float> values, List<Long> times, long start, long windowMs) {
        double sum = 0;
        int count = 0;
        for (int i = 0; i < values.size(); i++) {
            if (times.get(i) - start <= windowMs) {
                sum += values.get(i);
                count++;
            } else {
                break;
            }
        }
        return count == 0 ? Float.NaN : (float) (sum / count);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stage = Stage.DONE; // stop any in-flight countdown callback from doing anything
        countdownHandler.removeCallbacksAndMessages(null);
        if (sensorManager != null) sensorManager.unregisterListener(this);
        stopAudioCapture();
    }
}
