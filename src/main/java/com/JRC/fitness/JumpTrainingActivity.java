package com.JRC.fitness;

import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;

public class JumpTrainingActivity extends TrainingActivity {

    private static final int PERMISSION_REQUEST_AUDIO = 3;
    private static final int AUDIO_WINDOW_MS = 20;

    private AudioAmplitudeSource audioSource;

    @Override
    protected ExerciseType getType() {
        return ExerciseType.JUMP;
    }

    @Override
    protected void startSensors() {
        RepDetector.FeatureGate zcrGate = null;
        if (calibration.zcrHigh > 0) {
            // Widen the learned band a bit so both companion sounds of a jump
            // (foot landing and rope hitting the floor) pass comfortably, while
            // a spectrally distinct sound like a finger snap still gets rejected.
            float span = Math.max(0.02f, calibration.zcrHigh - calibration.zcrLow);
            float margin = span * 0.5f;
            float bandLow = Math.max(0f, calibration.zcrLow - margin);
            float bandHigh = calibration.zcrHigh + margin;
            zcrGate = zcr -> zcr >= bandLow && zcr <= bandHigh;
        }
        detector = new RepDetector(calibration.low, calibration.high, calibration.minRepIntervalMs, 0.4f,
                zcrGate, this::onRepDetected);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_AUDIO);
            return;
        }
        beginRecording();
    }

    private void beginRecording() {
        audioSource = new AudioAmplitudeSource(AUDIO_WINDOW_MS, (amplitude, zcr, ts) -> {
            if (detector != null) detector.feed(amplitude, zcr, ts);
        });
        if (!audioSource.start()) {
            Toast.makeText(this, "Couldn't access the microphone.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void stopSensors() {
        if (audioSource != null) {
            audioSource.stop();
            audioSource = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                beginRecording();
            } else {
                Toast.makeText(this, "Microphone permission is required to count jumps.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
