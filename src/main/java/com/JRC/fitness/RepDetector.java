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
 */
public class RepDetector {
    public interface Listener {
        void onRep(long timestampMillis);
    }

    private final float low;
    private final float high;
    private final long minIntervalMs;
    private final float smoothingAlpha;
    private final Listener listener;

    private boolean armed = true; // true once signal has dropped below `low`
    private float smoothed = Float.NaN;
    private long lastRepTime = 0L;

    public RepDetector(float low, float high, long minIntervalMs, float smoothingAlpha, Listener listener) {
        this.low = low;
        this.high = high;
        this.minIntervalMs = minIntervalMs;
        this.smoothingAlpha = smoothingAlpha;
        this.listener = listener;
    }

    /** Feed one raw reading. Returns true if a rep was counted. */
    public boolean feed(float rawValue, long timestampMillis) {
        if (Float.isNaN(smoothed)) {
            smoothed = rawValue;
        } else {
            smoothed += smoothingAlpha * (rawValue - smoothed);
        }

        if (smoothed <= low) {
            armed = true;
        } else if (armed && smoothed >= high) {
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
