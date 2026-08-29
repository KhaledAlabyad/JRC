package com.JRC.fitness;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal bar chart: one bar per value, oldest to newest, with a small
 * label under each bar and the value above it. Used on the stats screen
 * to show recent-session trends (reps or pace) without pulling in a
 * charting dependency.
 */
public class BarChartView extends View {

    private List<Float> values = new ArrayList<>();
    private List<String> labels = new ArrayList<>();
    private String valueFormat = "%.0f";

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BarChartView(Context context) {
        super(context);
        init();
    }

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint.setColor(Color.parseColor("#4CAF50"));
        axisPaint.setColor(Color.parseColor("#333333"));
        axisPaint.setStrokeWidth(2f);
        labelPaint.setColor(Color.parseColor("#888888"));
        labelPaint.setTextSize(spToPx(11));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setColor(Color.parseColor("#FFFFFF"));
        valuePaint.setTextSize(spToPx(12));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        emptyPaint.setColor(Color.parseColor("#666666"));
        emptyPaint.setTextSize(spToPx(13));
        emptyPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    /** valueFormat is a String.format pattern for the number drawn above each bar, e.g. "%.0f" or "%.2f". */
    public void setData(List<Float> values, List<String> labels, String valueFormat) {
        this.values = values != null ? values : new ArrayList<>();
        this.labels = labels != null ? labels : new ArrayList<>();
        this.valueFormat = valueFormat;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float baseline = h - 28f;

        canvas.drawLine(8f, baseline, w - 8f, baseline, axisPaint);

        if (values.isEmpty()) {
            canvas.drawText("No sessions yet", w / 2f, h / 2f, emptyPaint);
            return;
        }

        float max = 0f;
        for (float v : values) max = Math.max(max, v);
        if (max <= 0f) max = 1f;

        int n = values.size();
        float slot = (w - 16f) / n;
        float barWidth = Math.min(slot * 0.55f, 40f);

        // Reserve enough headroom above the tallest bar for its value label so it
        // never gets clipped against the top of the view (previously a fixed 20f
        // margin wasn't enough once the label's ascent was taken into account).
        Paint.FontMetrics valueMetrics = valuePaint.getFontMetrics();
        float labelHeight = -valueMetrics.ascent + valueMetrics.descent;
        float top = 12f + labelHeight + 6f; // 6f matches the gap used when drawing the label
        float usableHeight = baseline - top;

        for (int i = 0; i < n; i++) {
            float value = values.get(i);
            float barHeight = usableHeight * (value / max);
            float centerX = 8f + slot * i + slot / 2f;
            RectF rect = new RectF(centerX - barWidth / 2f, baseline - barHeight, centerX + barWidth / 2f, baseline);
            canvas.drawRoundRect(rect, 6f, 6f, barPaint);
            canvas.drawText(String.format(Locale.US, valueFormat, value), centerX, rect.top - 6f, valuePaint);
            if (i < labels.size()) {
                canvas.drawText(labels.get(i), centerX, h - 6f, labelPaint);
            }
        }
    }
}
