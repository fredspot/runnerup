/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.features;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.runnerup.R;

/**
 * Per-week kilometres dot chart. One column per Monday-Sunday week.
 *
 * <p>No axis, no grid: just a faint connecting polyline, an accent-coloured area underneath, a
 * filled dot for each week (larger and brighter for the current in-progress week), the week's km
 * value as digits above the dot, and a small Monday-date label below.
 *
 * <p>Sizes itself wide enough that {@link android.widget.HorizontalScrollView} can scroll
 * horizontally when there are more weeks than fit on screen — same pattern as {@link PaceChart}.
 */
public class WeeklyKmChart extends View {

  public static final class WeekPoint {
    public final long weekStartMillis;
    public final double km;
    public final boolean isCurrent;

    public WeekPoint(long weekStartMillis, double km, boolean isCurrent) {
      this.weekStartMillis = weekStartMillis;
      this.km = km;
      this.isCurrent = isCurrent;
    }
  }

  // Layout constants in dp / sp; converted lazily via dp()/sp().
  private static final float COLUMN_WIDTH_DP = 46f;
  private static final float DOT_RADIUS_DP = 5f;
  private static final float DOT_RADIUS_CURRENT_DP = 7f;
  private static final float LINE_STROKE_DP = 2f;
  private static final float DIGIT_TEXT_SP = 15f;
  private static final float DATE_TEXT_SP = 12f;
  private static final float TOP_PADDING_DP = 32f; // room for digit above the highest dot
  private static final float BOTTOM_PADDING_DP = 28f; // room for date label
  private static final float SIDE_PADDING_DP = 16f;

  /**
   * Multiplier applied to the data's max km to get the chart's visual ceiling. The extra headroom
   * compresses the apparent vertical gap between weeks so a typical "52 km vs 20 km" range no
   * longer looks dramatic — exact values are still readable as digits on each dot.
   */
  private static final double Y_HEADROOM_MULTIPLIER = 1.85;

  private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint dotHollowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint digitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint dateLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private WeekPoint[] points = new WeekPoint[0];
  private double maxKm = 1.0;

  private final SimpleDateFormat dateLabelFormat =
      new SimpleDateFormat("MMM d", Locale.getDefault());

  public WeeklyKmChart(Context context) {
    super(context);
    init(context);
  }

  public WeeklyKmChart(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  public WeeklyKmChart(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context);
  }

  private void init(Context context) {
    int accent = ContextCompat.getColor(context, R.color.colorAccent);
    int textPrimary = ContextCompat.getColor(context, R.color.colorText);
    int textSecondary = ContextCompat.getColor(context, R.color.colorTextSecondary);

    linePaint.setStyle(Paint.Style.STROKE);
    linePaint.setStrokeWidth(dp(LINE_STROKE_DP));
    linePaint.setStrokeJoin(Paint.Join.ROUND);
    linePaint.setStrokeCap(Paint.Cap.ROUND);
    linePaint.setColor(withAlpha(accent, 0xC0));

    areaPaint.setStyle(Paint.Style.FILL);
    areaPaint.setColor(withAlpha(accent, 0x33));

    dotPaint.setStyle(Paint.Style.FILL);
    dotPaint.setColor(accent);

    dotHollowPaint.setStyle(Paint.Style.STROKE);
    dotHollowPaint.setStrokeWidth(dp(1.5f));
    dotHollowPaint.setColor(withAlpha(textSecondary, 0xAA));

    digitPaint.setColor(textPrimary);
    digitPaint.setTextSize(sp(DIGIT_TEXT_SP));
    digitPaint.setTextAlign(Paint.Align.CENTER);
    digitPaint.setFakeBoldText(true);

    dateLabelPaint.setColor(textSecondary);
    dateLabelPaint.setTextSize(sp(DATE_TEXT_SP));
    dateLabelPaint.setTextAlign(Paint.Align.CENTER);
  }

  /** Replace the displayed series. {@code maxKm} controls vertical scaling. */
  public void setData(WeekPoint[] pts, double maxKm) {
    this.points = pts == null ? new WeekPoint[0] : pts;
    // Guard against zero / negative; otherwise division below explodes.
    this.maxKm = maxKm > 0 ? maxKm : 1.0;
    requestLayout();
    invalidate();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int n = points.length;
    float contentWidth = n * dp(COLUMN_WIDTH_DP);
    int desiredWidth = (int) Math.ceil(2 * dp(SIDE_PADDING_DP) + contentWidth);

    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    int measuredWidth;
    if (widthMode == MeasureSpec.EXACTLY) {
      measuredWidth = widthSize;
    } else if (widthMode == MeasureSpec.AT_MOST) {
      measuredWidth = Math.min(desiredWidth, widthSize);
    } else {
      measuredWidth = desiredWidth;
    }

    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    int heightSize = MeasureSpec.getSize(heightMeasureSpec);
    int measuredHeight;
    if (heightMode == MeasureSpec.UNSPECIFIED) {
      measuredHeight = (int) dp(240f);
    } else {
      measuredHeight = heightSize;
    }

    setMeasuredDimension(measuredWidth, measuredHeight);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int n = points.length;
    if (n == 0) {
      return;
    }

    float width = getWidth();
    float height = getHeight();
    float top = dp(TOP_PADDING_DP);
    float bottom = height - dp(BOTTOM_PADDING_DP);
    float drawHeight = bottom - top;
    if (drawHeight <= 0) {
      return;
    }
    float sidePad = dp(SIDE_PADDING_DP);
    float columnWidth = (width - 2 * sidePad) / n;

    // Compress the vertical scale by giving the highest dot some headroom; the digit on each dot
    // is the ground truth so the visual compression cannot mislead.
    double effectiveMaxKm = maxKm * Y_HEADROOM_MULTIPLIER;
    if (effectiveMaxKm <= 0) effectiveMaxKm = 1;
    float[] xs = new float[n];
    float[] ys = new float[n];
    for (int i = 0; i < n; i++) {
      xs[i] = sidePad + (i + 0.5f) * columnWidth;
      double frac = points[i].km / effectiveMaxKm;
      if (frac < 0) frac = 0;
      if (frac > 1) frac = 1;
      ys[i] = (float) (top + (1.0 - frac) * drawHeight);
    }

    // Smoothed Catmull-Rom-style bezier path through every dot; reused for both the line stroke
    // and the bottom edge of the area fill so they stay in lockstep.
    if (n >= 2) {
      Path curve = buildSmoothPath(xs, ys);
      // Area fill: re-walk the same curve, then close down along the baseline.
      Path fill = new Path(curve);
      fill.lineTo(xs[n - 1], bottom);
      fill.lineTo(xs[0], bottom);
      fill.close();
      canvas.drawPath(fill, areaPaint);

      canvas.drawPath(curve, linePaint);
    }

    // Dots + digits + date labels.
    float digitTextHeight = digitPaint.getTextSize();
    float dateLabelBaseline = height - dp(8f);
    for (int i = 0; i < n; i++) {
      WeekPoint p = points[i];
      float r = dp(p.isCurrent ? DOT_RADIUS_CURRENT_DP : DOT_RADIUS_DP);
      if (p.km > 0) {
        canvas.drawCircle(xs[i], ys[i], r, dotPaint);
      } else {
        // Hollow dot so the week is still acknowledged but visually distinct from a real run.
        canvas.drawCircle(xs[i], ys[i], r, dotHollowPaint);
      }

      if (p.km > 0) {
        String digit = formatKm(p.km);
        float digitY = ys[i] - r - dp(6f);
        // Keep digits inside the chart area.
        if (digitY < top + digitTextHeight) {
          digitY = top + digitTextHeight;
        }
        canvas.drawText(digit, xs[i], digitY, digitPaint);
      }

      String label = dateLabelFormat.format(new Date(p.weekStartMillis));
      canvas.drawText(label, xs[i], dateLabelBaseline, dateLabelPaint);
    }
  }

  /**
   * Build a cubic-bezier path that passes through every {@code (xs[i], ys[i])} dot using
   * Catmull-Rom-style control points. Tension {@code 0} would give straight lines; values near
   * {@code 1/6} reproduce a uniform Catmull-Rom curve and look like a gentle natural-spline.
   */
  private static Path buildSmoothPath(float[] xs, float[] ys) {
    int n = xs.length;
    Path p = new Path();
    p.moveTo(xs[0], ys[0]);
    if (n == 2) {
      p.lineTo(xs[1], ys[1]);
      return p;
    }
    final float tension = 0.18f;
    for (int i = 0; i < n - 1; i++) {
      float p0x = xs[Math.max(0, i - 1)];
      float p0y = ys[Math.max(0, i - 1)];
      float p1x = xs[i];
      float p1y = ys[i];
      float p2x = xs[i + 1];
      float p2y = ys[i + 1];
      float p3x = xs[Math.min(n - 1, i + 2)];
      float p3y = ys[Math.min(n - 1, i + 2)];

      float c1x = p1x + (p2x - p0x) * tension;
      float c1y = p1y + (p2y - p0y) * tension;
      float c2x = p2x - (p3x - p1x) * tension;
      float c2y = p2y - (p3y - p1y) * tension;

      p.cubicTo(c1x, c1y, c2x, c2y, p2x, p2y);
    }
    return p;
  }

  private String formatKm(double km) {
    if (Math.abs(km - Math.rint(km)) < 0.05) {
      return String.format(Locale.getDefault(), "%.0f", km);
    }
    return String.format(Locale.getDefault(), "%.1f", km);
  }

  private float dp(float v) {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
  }

  private float sp(float v) {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
  }

  private static int withAlpha(int color, int alpha) {
    return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
  }
}
