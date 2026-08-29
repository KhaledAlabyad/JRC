package com.JRC.fitness;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Personalized detection thresholds derived from a calibration session.
 *
 * For SQUAT: low/high form a hysteresis band around resting accelerometer
 * magnitude (~9.8). A rep is only counted after magnitude dips below `low`
 * and then rises back above `high`.
 *
 * For JUMP: high/low form a hysteresis band on microphone amplitude
 * (getMaxAmplitude()). A rep is counted on the rising edge above `high`,
 * and the detector re-arms only after amplitude falls back below `low`.
 *
 * minRepIntervalMs is a debounce: any candidate rep faster than this after
 * the previous one is ignored, since it's almost always sensor noise. For
 * JUMP it also doubles as the window that coalesces a single jump's two
 * sounds (foot landing + rope hitting the floor) into one counted rep.
 *
 * zcrLow/zcrHigh (JUMP only) are the accepted zero-crossing-rate band for a
 * genuine rep sound, learned from the calibration recording. A loud but
 * spectrally different sound - a finger snap, a clap, a door slam - will
 * usually fall outside this band and gets ignored even if it's loud enough
 * to cross the amplitude threshold. zcrHigh of 0 means "no band recorded"
 * (older calibration, or SQUAT), in which case the check is skipped.
 */
public class CalibrationData {
    public float low;
    public float high;
    public long minRepIntervalMs;
    public long calibratedAtMillis;
    public float zcrLow;
    public float zcrHigh;

    public CalibrationData() {}

    public CalibrationData(float low, float high, long minRepIntervalMs) {
        this(low, high, minRepIntervalMs, 0f, 0f);
    }

    public CalibrationData(float low, float high, long minRepIntervalMs, float zcrLow, float zcrHigh) {
        this.low = low;
        this.high = high;
        this.minRepIntervalMs = minRepIntervalMs;
        this.zcrLow = zcrLow;
        this.zcrHigh = zcrHigh;
        this.calibratedAtMillis = System.currentTimeMillis();
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("low", low);
        o.put("high", high);
        o.put("minRepIntervalMs", minRepIntervalMs);
        o.put("calibratedAtMillis", calibratedAtMillis);
        o.put("zcrLow", zcrLow);
        o.put("zcrHigh", zcrHigh);
        return o;
    }

    static CalibrationData fromJson(JSONObject o) throws JSONException {
        CalibrationData c = new CalibrationData();
        c.low = (float) o.optDouble("low", 0);
        c.high = (float) o.optDouble("high", 0);
        c.minRepIntervalMs = o.optLong("minRepIntervalMs", 300);
        c.calibratedAtMillis = o.optLong("calibratedAtMillis", 0);
        c.zcrLow = (float) o.optDouble("zcrLow", 0);
        c.zcrHigh = (float) o.optDouble("zcrHigh", 0);
        return c;
    }
}
