/*
 * Copyright (C) 2024 RunnerUp
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

package org.runnerup.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import org.runnerup.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DistributionChart extends View {
  
  private static final int barColor = Color.parseColor("#53B1FC"); // Same blue as START GPS button
  private static final float borderSize = 8;
  private static final float separatorSize = 16; // Vertical spacing between bars
  private static final int minBarHeight = 24;
  private static final double chartSize = 0.60; // 60% of width for bars (left side)
  private static final float statLabelMargin = 16; // Space between chart and stats
  
  private final Paint paint = new Paint();
  private final Paint fontPaint = new Paint();
  private final Paint statPaint = new Paint();
  private final Paint statMeanPaint = new Paint();
  
  private List<Long> lapTimes = new ArrayList<>(); // Times in seconds
  private int numBins = 15; // Number of bins for distribution
  
  // Statistics
  private long minTime = 0;
  private long maxTime = 0;
  private long meanTime = 0;
  private long percentile25 = 0;
  private long percentile75 = 0;
  
  public DistributionChart(Context context) {
    super(context);
    init();
  }
  
  public DistributionChart(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }
  
  private void init() {
    paint.setStyle(Paint.Style.FILL);
    paint.setAntiAlias(true);
    paint.setColor(barColor);
    
    fontPaint.setTextSize(sp(13));
    fontPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
    fontPaint.setColor(getResources().getColor(R.color.colorTextSecondary));
    fontPaint.setStyle(Paint.Style.FILL);
    fontPaint.setTextAlign(Paint.Align.LEFT);
    
    statPaint.setTextSize(sp(15));
    statPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
    statPaint.setColor(getResources().getColor(R.color.colorText));
    statPaint.setStyle(Paint.Style.FILL);
    statPaint.setTextAlign(Paint.Align.LEFT);

    statMeanPaint.setTextSize(sp(16));
    statMeanPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
    statMeanPaint.setColor(getResources().getColor(R.color.colorAccent));
    statMeanPaint.setStyle(Paint.Style.FILL);
    statMeanPaint.setTextAlign(Paint.Align.LEFT);
  }
  
  public void setLapTimes(List<Long> times) {
    this.lapTimes = new ArrayList<>(times);
    calculateStatistics();
    invalidate();
  }
  
  private void calculateStatistics() {
    if (lapTimes.isEmpty()) {
      minTime = maxTime = meanTime = percentile25 = percentile75 = 0;
      return;
    }
    
    List<Long> sorted = new ArrayList<>(lapTimes);
    Collections.sort(sorted);
    
    minTime = sorted.get(0);
    maxTime = sorted.get(sorted.size() - 1);
    
    // Calculate mean
    long sum = 0;
    for (Long time : sorted) {
      sum += time;
    }
    meanTime = sum / sorted.size();
    
    // Calculate percentiles
    percentile25 = sorted.get(sorted.size() / 4);
    percentile75 = sorted.get(sorted.size() * 3 / 4);
  }
  
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    
    if (lapTimes.isEmpty()) {
      return;
    }
    
    canvas.drawColor(Color.TRANSPARENT);
    
    // Calculate bin distribution
    int[] bins = calculateBins();
    int maxCount = 0;
    for (int count : bins) {
      if (count > maxCount) maxCount = count;
    }
    
    if (maxCount == 0) return;
    
    float totalWidth = getWidth();
    float totalHeight = getHeight();
    float chartWidth = (float) (totalWidth * chartSize);
    float chartStartX = borderSize; // Start from left
    float chartEndX = chartStartX + chartWidth;
    float statLabelStartX = chartEndX + statLabelMargin; // Stats start after chart
    
    // Bar dimensions
    float barAreaHeight = totalHeight - 2 * borderSize;
    float barHeight = (barAreaHeight - (numBins - 1) * separatorSize) / numBins;
    barHeight = Math.max(barHeight, minBarHeight);
    
    // Draw bars from bottom (fastest) to top (slowest)
    for (int i = 0; i < numBins; i++) {
      float barTop = totalHeight - borderSize - barHeight - i * (barHeight + separatorSize);
      float barLen = (bins[i] / (float) maxCount) * chartWidth;
      
      android.graphics.RectF rect = new android.graphics.RectF(
          chartStartX,
          barTop,
          chartStartX + barLen,
          barTop + barHeight);
      
      float cornerRadius = 4f;
      canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
    }
    
    // Draw statistics labels on the right side, positioned at their corresponding Y values
    if (maxTime > minTime) {
      // Position stats using continuous reversed mapping: slowest at top, fastest at bottom

      class StatLabel {
        final String text; final float baseY; float y; final Paint paint;
        StatLabel(String text, float baseY, Paint paint) { this.text = text; this.baseY = baseY; this.y = baseY; this.paint = paint; }
      }

      java.util.ArrayList<StatLabel> labels = new java.util.ArrayList<>();

      if (minTime > 0) {
        labels.add(new StatLabel(formatTime(minTime), getYForTimeReversed(minTime, totalHeight), statPaint));
      }
      if (percentile25 > 0) {
        labels.add(new StatLabel(formatTime(percentile25), getYForTimeReversed(percentile25, totalHeight), statPaint));
      }
      if (meanTime > 0) {
        labels.add(new StatLabel(formatTime(meanTime), getYForTimeReversed(meanTime, totalHeight), statMeanPaint));
      }
      if (percentile75 > 0) {
        labels.add(new StatLabel(formatTime(percentile75), getYForTimeReversed(percentile75, totalHeight), statPaint));
      }
      if (maxTime > 0) {
        labels.add(new StatLabel(formatTime(maxTime), getYForTimeReversed(maxTime, totalHeight), statPaint));
      }

      // Resolve overlaps by nudging labels so they don't collide, preserving ordering by baseY
      java.util.Collections.sort(labels, (a, b) -> Float.compare(a.baseY, b.baseY));
      float minGap = Math.max(statPaint.getTextSize(), statMeanPaint.getTextSize()) * 0.9f; // minimum vertical separation
      for (int i = 1; i < labels.size(); i++) {
        StatLabel prev = labels.get(i - 1);
        StatLabel cur = labels.get(i);
        if (cur.y - prev.y < minGap) {
          cur.y = prev.y + minGap;
        }
      }
      // Clamp within chart area
      float topY = borderSize + statPaint.getTextSize();
      float bottomY = totalHeight - borderSize - statPaint.getTextSize();
      for (int i = labels.size() - 2; i >= 0; i--) {
        StatLabel next = labels.get(i + 1);
        StatLabel cur = labels.get(i);
        if (next.y - cur.y < minGap) {
          cur.y = next.y - minGap;
        }
      }
      for (StatLabel l : labels) {
        if (l.y < topY) l.y = topY;
        if (l.y > bottomY) l.y = bottomY;
        canvas.drawText(l.text, statLabelStartX, l.y, l.paint);
      }
    }
  }
  
  private int[] calculateBins() {
    int[] bins = new int[numBins];
    if (lapTimes.isEmpty() || minTime == maxTime) {
      return bins;
    }
    
    float binWidth = (maxTime - minTime) / (float) numBins;
    
    for (Long time : lapTimes) {
      int binIndex = (int) ((time - minTime) / binWidth);
      if (binIndex >= numBins) binIndex = numBins - 1;
      if (binIndex < 0) binIndex = 0;
      bins[binIndex]++;
    }
    
    return bins;
  }
  
  private float getYForTimeReversed(long time, float totalHeight) {
    float range = (float) (maxTime - minTime);
    if (range <= 0f) return totalHeight - borderSize;
    float normalized = (time - minTime) / range; // 0 = fastest, 1 = slowest
    float barAreaHeight = totalHeight - 2f * borderSize;
    // Slowest (1) -> top (border), Fastest (0) -> bottom (border + barAreaHeight)
    return borderSize + (1f - normalized) * barAreaHeight;
  }

  private float sp(int sp) {
    return sp * getResources().getDisplayMetrics().scaledDensity;
  }
  
  private String formatTime(long seconds) {
    if (seconds == 0) return "--";
    long hours = seconds / 3600;
    long minutes = (seconds % 3600) / 60;
    long secs = seconds % 60;
    
    if (hours > 0) {
      return String.format("%d:%02d:%02d", hours, minutes, secs);
    } else {
      return String.format("%d:%02d", minutes, secs);
    }
  }
  
  public long getMinTime() { return minTime; }
  public long getMaxTime() { return maxTime; }
  public long getMeanTime() { return meanTime; }
  public long getPercentile25() { return percentile25; }
  public long getPercentile75() { return percentile75; }
}
