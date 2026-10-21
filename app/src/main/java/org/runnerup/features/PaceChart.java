package org.runnerup.features;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PaceChart extends View {

  public enum Metric {
    LAPS,
    AVG_HR
  }

  static final int[] PALETTE =
      new int[] {
        0xFF60A5FA, // blue
        0xFFF59E0B, // amber
        0xFF34D399, // green
        0xFFEF4444, // red
        0xFF8B5CF6, // violet
        0xFFFF7AB6 // pink
      };

  private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint axisLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private final float stackedBarWidthDp = 26f;
  private final float groupedBarWidthDp = 28f;
  private final float innerGapDp = 6f;
  private final float barGapDp = 22f;
  private final float borderDp = 12f;
  private final float yAxisDp = 36f;

  private List<Integer> binCentersSec = new ArrayList<>();
  private Map<Integer, PaceActivity.BinStats[]> yearToBins;
  private Metric metric = Metric.LAPS;

  public PaceChart(Context context) {
    super(context);
    init();
  }

  public PaceChart(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public PaceChart(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    barPaint.setStyle(Paint.Style.FILL);
    axisPaint.setStyle(Paint.Style.STROKE);
    axisPaint.setStrokeWidth(dp(1.5f));
    axisPaint.setColor(0x44FFFFFF);
    gridPaint.setStyle(Paint.Style.STROKE);
    gridPaint.setStrokeWidth(dp(1));
    gridPaint.setColor(0x22FFFFFF);
    labelPaint.setColor(0xFFFFFFFF);
    labelPaint.setTextSize(dp(13));
    labelPaint.setTextAlign(Paint.Align.CENTER);
    axisLabelPaint.setColor(0xB3FFFFFF);
    axisLabelPaint.setTextSize(dp(11));
    axisLabelPaint.setTextAlign(Paint.Align.RIGHT);
  }

  public void setBins(List<Integer> centersSec) {
    this.binCentersSec = new ArrayList<>(centersSec);
    requestLayout();
    invalidate();
  }

  public void setData(Map<Integer, PaceActivity.BinStats[]> yearToBins) {
    this.yearToBins = yearToBins;
    requestLayout();
    invalidate();
  }

  public void setMetric(Metric metric) {
    this.metric = metric;
    requestLayout();
    invalidate();
  }

  static List<Integer> sortedYears(Map<Integer, PaceActivity.BinStats[]> yearToBins) {
    if (yearToBins == null || yearToBins.isEmpty()) {
      return Collections.emptyList();
    }
    List<Integer> years = new ArrayList<>(yearToBins.keySet());
    Collections.sort(years);
    return years;
  }

  static boolean isGrouped(Metric metric) {
    return metric == Metric.AVG_HR;
  }

  static float binValue(Metric metric, PaceActivity.BinStats stats) {
    if (stats == null) {
      return 0f;
    }
    return metric == Metric.LAPS ? stats.laps : stats.avgHr();
  }

  /** Max used to scale bar height: summed years for stacked, per-year for grouped. */
  static float maxNormalizedValue(
      Metric metric, List<Integer> years, Map<Integer, PaceActivity.BinStats[]> yearToBins, int binCount) {
    return valueRange(metric, years, yearToBins, binCount)[1];
  }

  /**
   * Y-axis window [lo, hi]. Laps stay 0…max. Avg HR zooms around observed BPM so 140 vs 155 is
   * readable instead of a thin strip at the top of a 0–160 scale.
   */
  static float[] valueRange(
      Metric metric, List<Integer> years, Map<Integer, PaceActivity.BinStats[]> yearToBins, int binCount) {
    boolean grouped = isGrouped(metric);
    float minValue = Float.MAX_VALUE;
    float maxValue = 0f;
    boolean any = false;
    for (int bin = 0; bin < binCount; bin++) {
      float sum = 0f;
      for (int i = 0; i < years.size(); i++) {
        PaceActivity.BinStats[] bins = yearToBins.get(years.get(i));
        float v = binValue(metric, bins == null ? null : bins[bin]);
        if (grouped) {
          if (v > 0f) {
            any = true;
            if (v < minValue) {
              minValue = v;
            }
            if (v > maxValue) {
              maxValue = v;
            }
          }
        } else {
          sum += v;
        }
      }
      if (!grouped && sum > maxValue) {
        maxValue = sum;
      }
    }
    if (!grouped) {
      return new float[] {0f, maxValue <= 0f ? 1f : maxValue};
    }
    if (!any) {
      return new float[] {0f, 1f};
    }
    float span = maxValue - minValue;
    float pad = Math.max(8f, span * 0.2f);
    float lo = Math.max(0f, minValue - pad);
    float hi = maxValue + pad * 0.4f;
    if (hi - lo < 16f) {
      float mid = (minValue + maxValue) / 2f;
      lo = Math.max(0f, mid - 8f);
      hi = lo + 16f;
    }
    return new float[] {lo, hi};
  }

  static float heightFraction(float value, float lo, float hi) {
    float range = hi - lo;
    if (range <= 0f) {
      return 0f;
    }
    float frac = (value - lo) / range;
    if (frac < 0f) {
      return 0f;
    }
    if (frac > 1f) {
      return 1f;
    }
    return frac;
  }

  static float groupWidthPx(int yearCount, float barWidth, float innerGap, boolean grouped) {
    int n = Math.max(1, yearCount);
    if (!grouped || n == 1) {
      return barWidth;
    }
    return n * barWidth + (n - 1) * innerGap;
  }

  static float contentWidthPx(int binCount, float groupWidth, float gap) {
    if (binCount <= 0) {
      return 0f;
    }
    return binCount * groupWidth + Math.max(0, binCount - 1) * gap;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float border = dp(borderDp);
    float yAxis = dp(yAxisDp);
    float width = getWidth();
    float height = getHeight();
    float chartLeft = border + yAxis;
    float chartRight = width - border;
    float chartBottom = height - border - dp(22);
    float chartTop = border + dp(20);

    if (binCentersSec.isEmpty() || yearToBins == null || yearToBins.isEmpty()) {
      canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint);
      return;
    }

    List<Integer> years = sortedYears(yearToBins);
    int[] palette = PALETTE;
    boolean grouped = isGrouped(metric);
    float[] range = valueRange(metric, years, yearToBins, binCentersSec.size());
    float lo = range[0];
    float hi = range[1];

    drawValueAxis(canvas, lo, hi, chartLeft, chartRight, chartTop, chartBottom);

    float barWidth = dp(grouped ? groupedBarWidthDp : stackedBarWidthDp);
    float innerGap = dp(innerGapDp);
    float gap = dp(barGapDp);
    float groupWidth = groupWidthPx(years.size(), barWidth, innerGap, grouped);
    float startX = chartLeft;

    for (int bin = 0; bin < binCentersSec.size(); bin++) {
      float groupX0 = startX + bin * (groupWidth + gap);
      if (grouped) {
        drawGroupedBin(
            canvas, years, palette, bin, groupX0, barWidth, innerGap, chartTop, chartBottom, lo, hi);
      } else {
        drawStackedBin(canvas, years, palette, bin, groupX0, groupWidth, chartTop, chartBottom, lo, hi);
      }

      String label = formatPace(binCentersSec.get(bin));
      canvas.drawText(label, groupX0 + groupWidth / 2f, chartBottom + dp(16), labelPaint);
    }
  }

  private void drawValueAxis(
      Canvas canvas,
      float lo,
      float hi,
      float chartLeft,
      float chartRight,
      float chartTop,
      float chartBottom) {
    float[] ticks = new float[] {hi, (lo + hi) / 2f, lo};
    for (float tick : ticks) {
      float y = chartBottom - (chartBottom - chartTop) * heightFraction(tick, lo, hi);
      canvas.drawLine(chartLeft, y, chartRight, y, gridPaint);
      canvas.drawText(String.valueOf(Math.round(tick)), chartLeft - dp(6), y + dp(4), axisLabelPaint);
    }
    canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, axisPaint);
    canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint);
  }

  private void drawStackedBin(
      Canvas canvas,
      List<Integer> years,
      int[] palette,
      int bin,
      float x0,
      float barWidth,
      float chartTop,
      float chartBottom,
      float lo,
      float hi) {
    float x1 = x0 + barWidth;
    float yCursor = chartBottom;
    float stackedSoFar = 0f;
    for (int yi = 0; yi < years.size(); yi++) {
      PaceActivity.BinStats[] bins = yearToBins.get(years.get(yi));
      float v = binValue(metric, bins == null ? null : bins[bin]);
      if (v <= 0f) {
        continue;
      }
      stackedSoFar += v;
      float yTop = chartBottom - (chartBottom - chartTop) * heightFraction(stackedSoFar, lo, hi);
      barPaint.setColor(palette[yi % palette.length]);
      canvas.drawRect(new RectF(x0, yTop, x1, yCursor), barPaint);
      yCursor = yTop;
    }
    if (stackedSoFar > 0f) {
      drawTopLabel(canvas, String.valueOf(Math.round(stackedSoFar)), x0, barWidth, yCursor, chartTop);
    }
  }

  private void drawGroupedBin(
      Canvas canvas,
      List<Integer> years,
      int[] palette,
      int bin,
      float groupX0,
      float subBarWidth,
      float innerGap,
      float chartTop,
      float chartBottom,
      float lo,
      float hi) {
    for (int yi = 0; yi < years.size(); yi++) {
      PaceActivity.BinStats[] bins = yearToBins.get(years.get(yi));
      float v = binValue(metric, bins == null ? null : bins[bin]);
      if (v <= 0f) {
        continue;
      }
      float x0 = groupX0 + yi * (subBarWidth + innerGap);
      float x1 = x0 + subBarWidth;
      float yTop = chartBottom - (chartBottom - chartTop) * heightFraction(v, lo, hi);
      barPaint.setColor(palette[yi % palette.length]);
      canvas.drawRect(new RectF(x0, yTop, x1, chartBottom), barPaint);
      drawTopLabel(canvas, String.valueOf(Math.round(v)), x0, subBarWidth, yTop, chartTop);
    }
  }

  private void drawTopLabel(
      Canvas canvas, String topVal, float x0, float barWidth, float yTop, float chartTop) {
    float yText = yTop - dp(6);
    if (yText < chartTop + labelPaint.getTextSize()) {
      yText = chartTop + labelPaint.getTextSize();
    }
    canvas.drawText(topVal, x0 + barWidth / 2f, yText, labelPaint);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    float border = dp(borderDp);
    float yAxis = dp(yAxisDp);
    List<Integer> years = sortedYears(yearToBins);
    boolean grouped = isGrouped(metric);
    float barWidth = dp(grouped ? groupedBarWidthDp : stackedBarWidthDp);
    float innerGap = dp(innerGapDp);
    float gap = dp(barGapDp);
    int n = binCentersSec != null ? binCentersSec.size() : 0;
    float groupWidth = groupWidthPx(years.size(), barWidth, innerGap, grouped);
    float contentWidth = contentWidthPx(n, groupWidth, gap);
    int desiredWidth = (int) Math.ceil(border * 2f + yAxis + contentWidth);

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

  private float dp(float v) {
    return getResources().getDisplayMetrics().density * v;
  }
}
