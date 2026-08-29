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
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CalibrationActivity extends Activity implements SensorEventListener {

    public static final String EXTRA_TYPE = "exercise_type";
    private static final int PERMISSION_REQUEST_AUDIO = 2;
    private static final int AUDIO_POLL_MS = 60;

    private enum Stage { READY, RECORDING, DONE }

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
    private MediaRecorder mediaRecorder;
    private final Handler audioHandler = new Handler();
    private Runnable audioPoller;

    private final List<Float> values = new ArrayList<>();
    private final List<Long> times = new ArrayList<>();
    private long recordStartTime;

    // live rough count during recording, just to show progress toward the target
    private RepDetector liveDetector;
    private int liveCount = 0;

    private CalibrationData result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
            startRecording();
        } else if (stage == Stage.RECORDING) {
            stopRecordingAndAnalyze();
        } else {
            // DONE -> recalibrate from scratch
            values.clear();
            times.clear();
            liveCount = 0;
            renderReady();
        }
    }

    private void renderReady() {
        stage = Stage.READY;
        statusText.setText("Perform exactly " + type.calibrationReps + " " + type.label.toLowerCase()
                + " at your normal pace after you tap Start. Tap Stop as soon as you finish the last rep.");
        countText.setText("");
        actionButton.setText("Start");
        continueButton.setVisibility(View.GONE);
    }

    private void startRecording() {
        values.clear();
        times.clear();
        liveCount = 0;
        recordStartTime = System.currentTimeMillis();
        stage = Stage.RECORDING;
        statusText.setText("Recording... perform your " + type.calibrationReps + " reps now.");
        actionButton.setText("Stop");
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
                : analyzeJump(values, times, duration, type.calibrationReps);

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
        try {
            File tmp = new File(getCacheDir(), "calibration_audio.3gp");
            mediaRecorder = new MediaRecorder(this);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(tmp.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();

            liveDetector = new RepDetector(3000, 8000, 250, 0.4f, ts -> {
                liveCount++;
                runOnUiThread(this::updateCount);
            });

            audioPoller = new Runnable() {
                @Override
                public void run() {
                    if (mediaRecorder != null && stage == Stage.RECORDING) {
                        int amplitude = mediaRecorder.getMaxAmplitude();
                        long now = System.currentTimeMillis();
                        values.add((float) amplitude);
                        times.add(now);
                        if (liveDetector != null) liveDetector.feed(amplitude, now);
                        audioHandler.postDelayed(this, AUDIO_POLL_MS);
                    }
                }
            };
            audioHandler.postDelayed(audioPoller, AUDIO_POLL_MS);
        } catch (Exception e) {
            e.printStackTrace();
            statusText.setText("Couldn't access the microphone. Check app permissions and try again.");
            stage = Stage.READY;
            actionButton.setText("Start");
        }
    }

    private void stopAudioCapture() {
        if (audioPoller != null) audioHandler.removeCallbacks(audioPoller);
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception ignored) {
            }
            mediaRecorder.release();
            mediaRecorder = null;
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

        long minInterval = repIntervalMs(durationMs, reps);
        return new CalibrationData(low, high, minInterval);
    }

    private static CalibrationData analyzeJump(List<Float> values, List<Long> times, long durationMs, int reps) {
        if (values.isEmpty()) {
            return new CalibrationData(3000, 9000, 250);
        }
        List<Float> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        float floor = percentile(sorted, 30); // quiet baseline between reps
        float peak = percentile(sorted, 90);  // rep impact / sound spike

        float span = Math.max(500f, peak - floor);
        float high = floor + 0.5f * span;
        float low = floor + 0.15f * span;

        long minInterval = repIntervalMs(durationMs, reps);
        return new CalibrationData(low, high, minInterval);
    }

    private static long repIntervalMs(long durationMs, int reps) {
        if (reps <= 0) return 300;
        long avgPeriod = durationMs / reps;
        long minInterval = (long) (avgPeriod * 0.4);
        if (minInterval < 120) minInterval = 120;
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
        if (sensorManager != null) sensorManager.unregisterListener(this);
        stopAudioCapture();
    }
}
