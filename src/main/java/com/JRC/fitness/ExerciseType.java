package com.JRC.fitness;

public enum ExerciseType {
    SQUAT("squat", "Squats", 10),
    JUMP("jump", "Jump Rope", 20);

    public final String key;
    public final String label;
    public final int calibrationReps; // predefined rep count performed during calibration

    ExerciseType(String key, String label, int calibrationReps) {
        this.key = key;
        this.label = label;
        this.calibrationReps = calibrationReps;
    }
}
