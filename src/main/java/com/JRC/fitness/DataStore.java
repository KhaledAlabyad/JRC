package com.JRC.fitness;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Single place for all persisted app state: per-exercise calibration,
 * session history, and per-exercise goals. Kept as plain SharedPreferences
 * + JSON to avoid pulling in a database for what is a small amount of data.
 */
public class DataStore {
    private static final String PREFS = "jrc_fitness_prefs";
    private static final String KEY_CALIBRATION_PREFIX = "calibration_";
    private static final String KEY_SESSIONS = "sessions";
    private static final String KEY_GOAL_REPS_PREFIX = "goal_reps_";
    private static final String KEY_GOAL_PACE_PREFIX = "goal_pace_"; // reps/sec

    private final SharedPreferences prefs;

    public DataStore(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------- Calibration ----------

    public boolean hasCalibration(ExerciseType type) {
        return prefs.contains(KEY_CALIBRATION_PREFIX + type.key);
    }

    public CalibrationData getCalibration(ExerciseType type) {
        String raw = prefs.getString(KEY_CALIBRATION_PREFIX + type.key, null);
        if (raw == null) return null;
        try {
            return CalibrationData.fromJson(new JSONObject(raw));
        } catch (JSONException e) {
            return null;
        }
    }

    public void saveCalibration(ExerciseType type, CalibrationData data) {
        try {
            prefs.edit()
                    .putString(KEY_CALIBRATION_PREFIX + type.key, data.toJson().toString())
                    .apply();
        } catch (JSONException ignored) {
        }
    }

    // ---------- Sessions ----------

    public void addSession(Session session) {
        List<Session> all = getSessions(null);
        all.add(session);
        JSONArray arr = new JSONArray();
        try {
            for (Session s : all) arr.put(s.toJson());
            prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    /** Pass an ExerciseType to filter, or null for all sessions, newest first. */
    public List<Session> getSessions(ExerciseType filter) {
        List<Session> result = new ArrayList<>();
        String raw = prefs.getString(KEY_SESSIONS, null);
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    Session s = Session.fromJson(arr.getJSONObject(i));
                    if (filter == null || filter.key.equals(s.type)) {
                        result.add(s);
                    }
                }
            } catch (JSONException ignored) {
            }
        }
        // newest first
        java.util.Collections.reverse(result);
        return result;
    }

    // ---------- Goals ----------

    public int getGoalReps(ExerciseType type) {
        return prefs.getInt(KEY_GOAL_REPS_PREFIX + type.key, 0); // 0 = no goal set
    }

    public void setGoalReps(ExerciseType type, int reps) {
        prefs.edit().putInt(KEY_GOAL_REPS_PREFIX + type.key, reps).apply();
    }

    public float getGoalPace(ExerciseType type) {
        return prefs.getFloat(KEY_GOAL_PACE_PREFIX + type.key, 0f); // reps/sec, 0 = no goal
    }

    public void setGoalPace(ExerciseType type, float repsPerSecond) {
        prefs.edit().putFloat(KEY_GOAL_PACE_PREFIX + type.key, repsPerSecond).apply();
    }
}
