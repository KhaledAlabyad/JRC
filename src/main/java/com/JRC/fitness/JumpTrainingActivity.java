package com.JRC.fitness;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Handler;
import android.widget.Toast;

import java.io.File;

public class JumpTrainingActivity extends TrainingActivity {

    private static final int PERMISSION_REQUEST_AUDIO = 3;
    private static final int AUDIO_POLL_MS = 60;

    private MediaRecorder mediaRecorder;
    private final Handler audioHandler = new Handler();
    private Runnable audioPoller;
    private boolean running = false;

    @Override
    protected ExerciseType getType() {
        return ExerciseType.JUMP;
    }

    @Override
    protected void startSensors() {
        detector = new RepDetector(calibration.low, calibration.high, calibration.minRepIntervalMs, 0.4f,
                this::onRepDetected);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_AUDIO);
            return;
        }
        beginRecording();
    }

    private void beginRecording() {
        try {
            File tmp = new File(getCacheDir(), "training_audio.3gp");
            mediaRecorder = new MediaRecorder(this);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(tmp.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
            running = true;

            audioPoller = new Runnable() {
                @Override
                public void run() {
                    if (running && mediaRecorder != null) {
                        int amplitude = mediaRecorder.getMaxAmplitude();
                        if (detector != null) detector.feed(amplitude, System.currentTimeMillis());
                        audioHandler.postDelayed(this, AUDIO_POLL_MS);
                    }
                }
            };
            audioHandler.postDelayed(audioPoller, AUDIO_POLL_MS);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Couldn't access the microphone.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void stopSensors() {
        running = false;
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
                beginRecording();
            } else {
                Toast.makeText(this, "Microphone permission is required to count jumps.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
