package com.JRC.fitness;

import org.json.JSONException;
import org.json.JSONObject;

public class Session {
    public String type;          // ExerciseType.key
    public long timestampMillis; // when session ended
    public int reps;
    public long durationMs;      // from first detected rep to stop

    public Session() {}

    public Session(String type, long timestampMillis, int reps, long durationMs) {
        this.type = type;
        this.timestampMillis = timestampMillis;
        this.reps = reps;
        this.durationMs = durationMs;
    }

    public double repsPerSecond() {
        if (durationMs <= 0) return 0;
        return reps / (durationMs / 1000.0);
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", type);
        o.put("timestampMillis", timestampMillis);
        o.put("reps", reps);
        o.put("durationMs", durationMs);
        return o;
    }

    static Session fromJson(JSONObject o) throws JSONException {
        Session s = new Session();
        s.type = o.optString("type");
        s.timestampMillis = o.optLong("timestampMillis");
        s.reps = o.optInt("reps");
        s.durationMs = o.optLong("durationMs");
        return s;
    }
}
