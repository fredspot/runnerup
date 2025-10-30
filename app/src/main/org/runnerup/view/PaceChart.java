package org.runnerup.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class PaceChart extends View {

  public enum Metric { LAPS, AVG_HR }

  private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private final float barWidthDp = 18f;
  private final float barGapDp = 18f;
  private final float borderDp = 16f;

  private List<Integer> binCentersSec = new ArrayList<>(); // e.g., 240, 250, ...
  private Map<Integer, PaceActivity.BinStats[]> yearToBins; // year -> bins
  private Metric metric = Metric.LAPS;

  public PaceChart(Context context) { super(context); init(); }
  public PaceChart(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
  public PaceChart(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

  private void init() {
    barPaint.setStyle(Paint.Style.FILL);
    axisPaint.setStyle(Paint.Style.STROKE);
    axisPaint.setStrokeWidth(dp(1));
    axisPaint.setColor(0x22FFFFFF);
    labelPaint.setColor(0xFFFFFFFF);
    labelPaint.setTextSize(dp(12));
  }

  public void setBins(List<Integer> centersSec) {
    this.binCentersSec = new ArrayList<>(centersSec);
    invalidate();
  }

  public void setData(Map<Integer, PaceActivity.BinStats[]> yearToBins) {
    this.yearToBins = yearToBins;
    invalidate();
  }

  public void setMetric(Metric metric) {
    this.metric = metric;
    invalidate();
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float border = dp(borderDp);
    float width = getWidth();
    float height = getHeight();
    float chartLeft = border;
    float chartRight = width - border;
    float chartBottom = height - border - dp(18); // leave room for x labels
    float chartTop = border + dp(8);

    // Draw baseline
    canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint);

    if (binCentersSec.isEmpty() || yearToBins == null || yearToBins.isEmpty()) return;

    // Determine year order and colors
    List<Integer> years = new ArrayList<>(yearToBins.keySet());
    Collections.sort(years);
    int[] palette = getPalette();

    // Compute per-bin max to normalize heights
    float maxValue = 0f;
    for (int bin = 0; bin < binCentersSec.size(); bin++) {
      float sum = 0f;
      for (int i = 0; i < years.size(); i++) {
        PaceActivity.BinStats s = yearToBins.get(years.get(i))[bin];
        float v = metric == Metric.LAPS ? s.laps : s.avgHr();
        sum += v;
      }
      if (sum > maxValue) maxValue = sum;
    }
    if (maxValue <= 0f) maxValue = 1f;

    float barWidth = dp(barWidthDp);
    float gap = dp(barGapDp);
    float totalBarsWidth = binCentersSec.size() * barWidth + (binCentersSec.size() - 1) * gap;
    float startX = chartLeft; // draw from left; width provided by onMeasure enables scroll

    for (int bin = 0; bin < binCentersSec.size(); bin++) {
      float x0 = startX + bin * (barWidth + gap);
      float x1 = x0 + barWidth;

      // Stacked up from bottom
      float yCursor = chartBottom;
      float stackedSoFar = 0f;
      for (int yi = 0; yi < years.size(); yi++) {
        int year = years.get(yi);
        PaceActivity.BinStats s = yearToBins.get(year)[bin];
        float v = metric == Metric.LAPS ? s.laps : s.avgHr();
        if (v <= 0f) continue;
        stackedSoFar += v;
        float heightFrac = stackedSoFar / maxValue;
        float yTop = chartBottom - (chartBottom - chartTop) * heightFrac;
        barPaint.setColor(palette[yi % palette.length]);
        canvas.drawRect(new RectF(x0, yTop, x1, yCursor), barPaint);
        yCursor = yTop;
      }

      // Value label at top of the total bar
      if (stackedSoFar > 0f) {
        String topVal = metric == Metric.LAPS ? String.valueOf(Math.round(stackedSoFar)) : String.valueOf(Math.round(stackedSoFar));
        float textWTop = labelPaint.measureText(topVal);
        float yText = yCursor - dp(4);
        if (yText < chartTop + labelPaint.getTextSize()) yText = chartTop + labelPaint.getTextSize();
        canvas.drawText(topVal, x0 + (barWidth - textWTop) / 2f, yText, labelPaint);
      }

      // X label under each bar (e.g., 4:00, 4:10, ...)
      String label = formatPace(binCentersSec.get(bin));
      float textW = labelPaint.measureText(label);
      canvas.drawText(label, x0 + (barWidth - textW) / 2f, chartBottom + dp(14), labelPaint);
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    float border = dp(borderDp);
    float barWidth = dp(barWidthDp);
    float gap = dp(barGapDp);
    int n = binCentersSec != null ? binCentersSec.size() : 0;
    float contentWidth = n > 0 ? (n * barWidth + Math.max(0, n - 1) * gap) : 0f;
    int desiredWidth = (int) Math.ceil(border * 2f + contentWidth);

    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    int heightSize = MeasureSpec.getSize(heightMeasureSpec);

    int measuredWidth = (widthMode == MeasureSpec.EXACTLY) ? widthSize : desiredWidth;
    setMeasuredDimension(measuredWidth, heightSize);
  }

  private String formatPace(int sec) {
    int m = sec / 60;
    int s = sec % 60;
    return String.format("%d:%02d", m, s);
  }

  private float dp(float v) { return getResources().getDisplayMetrics().density * v; }

  private int[] getPalette() {
    return new int[] {
        0xFF60A5FA, // blue
        0xFFF59E0B, // amber
        0xFF34D399, // green
        0xFFEF4444, // red
        0xFF8B5CF6, // violet
        0xFFFF7AB6  // pink
    };
  }
}


