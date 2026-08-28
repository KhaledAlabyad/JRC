package com.JRC.fitness;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener {

    private TextView textSquats;
    private TextView textJumps;

    // Squat Tracking
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private int squatCount = 0;
    private boolean isSquattingDown = false;
    private static final float GRAVITY_THRESHOLD_LOW = 8.0f;
    private static final float GRAVITY_THRESHOLD_HIGH = 11.0f;

    // Jump Rope Tracking
    private MediaRecorder mediaRecorder;
    private int jumpCount = 0;
    private final Handler audioHandler = new Handler();
    private static final int SOUND_SPIKE_THRESHOLD = 15000;
    private static final int PERMISSION_REQUEST_AUDIO = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Links to your XML UI

        textSquats = findViewById(R.id.textSquats);
        textJumps = findViewById(R.id.textJumps);

        // Setup Squat Sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // Request Audio Permission (if not already granted)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_AUDIO);
        } else {
            startAudioListening();
        }
    }

    // Handles the user tapping "Allow" or "Deny" on the permission popup
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAudioListening();
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            double magnitude = Math.sqrt(x * x + y * y + z * z);

            if (magnitude < GRAVITY_THRESHOLD_LOW && !isSquattingDown) {
                isSquattingDown = true;
            } else if (magnitude > GRAVITY_THRESHOLD_HIGH && isSquattingDown) {
                isSquattingDown = false;
                squatCount++;
                
                // Update UI on the main thread
                runOnUiThread(() -> textSquats.setText(String.valueOf(squatCount)));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void startAudioListening() {
        try {
            mediaRecorder = new MediaRecorder(this);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile("/dev/null");
            mediaRecorder.prepare();
            mediaRecorder.start();
            pollAudioSpikes();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void pollAudioSpikes() {
        audioHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mediaRecorder != null) {
                    int amplitude = mediaRecorder.getMaxAmplitude();
                    if (amplitude > SOUND_SPIKE_THRESHOLD) {
                        jumpCount++;
                        textJumps.setText(String.valueOf(jumpCount)); // Handler runs on main thread
                    }
                }
                audioHandler.postDelayed(this, 150); // Poll every 150ms
            }
        }, 150);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
        }
    }
}
