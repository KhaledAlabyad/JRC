package com.JRC.fitness;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class SquatTrainingActivity extends TrainingActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;

    @Override
    protected ExerciseType getType() {
        return ExerciseType.SQUAT;
    }

    @Override
    protected void startSensors() {
        detector = new RepDetector(calibration.low, calibration.high, calibration.minRepIntervalMs, 0.25f,
                this::onRepDetected);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void stopSensors() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER || detector == null) return;
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        detector.feed(magnitude, System.currentTimeMillis());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
