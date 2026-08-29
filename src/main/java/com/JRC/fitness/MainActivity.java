package com.JRC.fitness;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {

    // Fling thresholds for the swipe-up-for-stats gesture.
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_MIN_DISTANCE = 100;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;

    private DataStore store;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        setContentView(R.layout.activity_main);
        store = new DataStore(this);

        Button squatButton = findViewById(R.id.homeSquatButton);
        Button jumpButton = findViewById(R.id.homeJumpButton);
        View settingsButton = findViewById(R.id.homeSettingsButton);
        View root = findViewById(R.id.mainRoot);

        squatButton.setOnClickListener(v -> openExercise(ExerciseType.SQUAT));
        jumpButton.setOnClickListener(v -> openExercise(ExerciseType.JUMP));
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        gestureDetector = new GestureDetector(this, new SwipeUpGestureListener());
        root.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }

    private void openExercise(ExerciseType type) {
        Class<?> target = store.hasCalibration(type)
                ? (type == ExerciseType.SQUAT ? SquatTrainingActivity.class : JumpTrainingActivity.class)
                : CalibrationActivity.class;
        Intent i = new Intent(this, target);
        if (target == CalibrationActivity.class) {
            i.putExtra(CalibrationActivity.EXTRA_TYPE, type.key);
        }
        startActivity(i);
    }

    private void openStats() {
        startActivity(new Intent(this, StatsActivity.class));
    }

    /** Swiping up anywhere on the home screen opens Stats & Goals. */
    private class SwipeUpGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e1 == null || e2 == null) return false;
            float diffY = e1.getY() - e2.getY(); // positive = moved upward
            float diffX = e2.getX() - e1.getX();
            if (Math.abs(diffX) < SWIPE_MAX_OFF_PATH
                    && diffY > SWIPE_MIN_DISTANCE
                    && Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY) {
                openStats();
                return true;
            }
            return false;
        }
    }
}
