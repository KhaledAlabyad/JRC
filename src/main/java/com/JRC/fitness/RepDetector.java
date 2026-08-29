package com.JRC.fitness;

/**
 * Generic edge-triggered rep counter with hysteresis + debounce.
 *
 * A rep fires when the smoothed signal rises above `high` after having
 * dropped below `low` since the last rep (armed -> triggered), and the
 * candidate rep is discarded if it comes sooner than `minIntervalMs`
 * after the previous one. This single mechanism fixes both reported bugs:
 *  - squats counting from tiny vibration (band was too narrow / no debounce)
 *  - jumps not counting reliably (level-triggered instead of edge-triggered,
 *    fixed threshold instead of a calibrated one)
 *
 * Values are smoothed with a simple exponential moving average before
 * being compared to the thresholds, which further rejects sensor jitter.
 *
 * An optional FeatureGate can be supplied (used for jump rope) to also
 * validate a second signal - zero-crossing rate, in that case - before a
 * threshold crossing is allowed to fire. A rejected spike leaves the
 * detector still armed, so it doesn't block a genuine rep shortly after.
 * `minIntervalMs` doubles as the coalescing window that merges the two
 * sounds a single jump produces (foot landing + rope hitting the floor)
 * into one counted rep, since the second sound normally arrives well
 * inside that window.
 */
public class RepDetector {
    public interface Listener {
        void onRep(long timestampMillis);
    }

    /** Validates a secondary feature (e.g. zero-crossing rate) alongside the amplitude threshold. */
    public interface FeatureGate {
        boolean accept(float auxFeature);
    }

    private final float low;
    private final float high;
    private final long minIntervalMs;
    private final float smoothingAlpha;
    private final Listener listener;
    private final FeatureGate gate;

    private boolean armed = true; // true once signal has dropped below `low`
    private float smoothed = Float.NaN;
    private long lastRepTime = 0L;

    public RepDetector(float low, float high, long minIntervalMs, float smoothingAlpha, Listener listener) {
        this(low, high, minIntervalMs, smoothingAlpha, null, listener);
    }

    public RepDetector(float low, float high, long minIntervalMs, float smoothingAlpha, FeatureGate gate, Listener listener) {
        this.low = low;
        this.high = high;
        this.minIntervalMs = minIntervalMs;
        this.smoothingAlpha = smoothingAlpha;
        this.gate = gate;
        this.listener = listener;
    }

    /** Feed one raw reading with no secondary feature to validate. Returns true if a rep was counted. */
    public boolean feed(float rawValue, long timestampMillis) {
        return feed(rawValue, Float.NaN, timestampMillis);
    }

    /** Feed one raw reading plus a secondary feature (e.g. zero-crossing rate) checked by the FeatureGate, if any. */
    public boolean feed(float rawValue, float auxFeature, long timestampMillis) {
        if (Float.isNaN(smoothed)) {
            smoothed = rawValue;
        } else {
            smoothed += smoothingAlpha * (rawValue - smoothed);
        }

        if (smoothed <= low) {
            armed = true;
        } else if (armed && smoothed >= high) {
            if (gate != null && !Float.isNaN(auxFeature) && !gate.accept(auxFeature)) {
                // Doesn't look like the calibrated sound (e.g. a finger snap): ignore this
                // spike entirely but stay armed so a genuine rep can still fire right after.
                return false;
            }
            armed = false;
            if (timestampMillis - lastRepTime >= minIntervalMs) {
                lastRepTime = timestampMillis;
                if (listener != null) listener.onRep(timestampMillis);
                return true;
            }
        }
        return false;
    }

    public float getSmoothed() {
        return smoothed;
    }
}
