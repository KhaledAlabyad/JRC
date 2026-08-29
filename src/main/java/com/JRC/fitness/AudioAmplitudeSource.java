package com.JRC.fitness;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/**
 * Reads raw 16-bit PCM audio directly (instead of going through
 * MediaRecorder -> an encoder -> getMaxAmplitude()) and reports, per short
 * time window, both the peak absolute sample value and a zero-crossing
 * rate (ZCR).
 *
 * This is the main jump-accuracy fix: MediaRecorder's amplitude is derived
 * from a compressed (AMR-NB) stream, which smears/delays transient sounds
 * like a rope hitting the floor and behaves inconsistently across devices.
 * Reading PCM straight from the mic gives a fast, consistent peak reading
 * that RepDetector can trigger on reliably.
 *
 * ZCR (crossings of zero per sample, roughly proportional to dominant
 * frequency) lets RepDetector tell a broadband, low-frequency thud - a foot
 * landing or the rope hitting the floor - apart from a short, sharp,
 * higher-frequency transient like a finger snap or a tap on the table, so
 * those don't get counted as reps.
 */
public class AudioAmplitudeSource {

    public interface Listener {
        void onAmplitude(float amplitude, float zeroCrossingRate, long timestampMillis);
    }

    private static final int SAMPLE_RATE = 16000;
    private final int windowMs;
    private final Listener listener;

    private AudioRecord audioRecord;
    private Thread captureThread;
    private volatile boolean running = false;

    public AudioAmplitudeSource(int windowMs, Listener listener) {
        this.windowMs = windowMs;
        this.listener = listener;
    }

    /** Returns false if the mic couldn't be opened (permission missing/denied, device busy, etc). */
    @SuppressLint("MissingPermission") // caller is required to have checked RECORD_AUDIO already
    public boolean start() {
        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) return false;

        int samplesPerWindow = Math.max(1, SAMPLE_RATE * windowMs / 1000);
        int bufferBytes = Math.max(minBuf, samplesPerWindow * 2 * 4);

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release();
                audioRecord = null;
                return false;
            }
            audioRecord.startRecording();
        } catch (Exception e) {
            audioRecord = null;
            return false;
        }

        running = true;
        captureThread = new Thread(() -> captureLoop(samplesPerWindow), "audio-amplitude");
        captureThread.start();
        return true;
    }

    private void captureLoop(int samplesPerWindow) {
        short[] buffer = new short[samplesPerWindow];
        while (running && audioRecord != null) {
            int read = audioRecord.read(buffer, 0, buffer.length);
            if (read > 0) {
                int peak = 0;
                int crossings = 0;
                for (int i = 0; i < read; i++) {
                    int abs = Math.abs(buffer[i]);
                    if (abs > peak) peak = abs;
                    if (i > 0 && ((buffer[i - 1] >= 0) != (buffer[i] >= 0))) {
                        crossings++;
                    }
                }
                float zcr = read > 1 ? crossings / (float) (read - 1) : 0f;
                long now = System.currentTimeMillis();
                if (listener != null) listener.onAmplitude(peak, zcr, now);
            }
        }
    }

    public void stop() {
        running = false;
        if (captureThread != null) {
            try {
                captureThread.join(300);
            } catch (InterruptedException ignored) {
            }
            captureThread = null;
        }
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (Exception ignored) {
            }
            audioRecord.release();
            audioRecord = null;
        }
    }
}
