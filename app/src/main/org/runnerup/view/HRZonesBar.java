/*
 * Copyright (C) 2014 git@fabieng.eu
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
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import org.runnerup.R;

public class HRZonesBar extends View {

  private static final int zoneColor = Color.parseColor("#53B1FC"); // Same blue as START GPS button

  // Single blue color for all zones (same as START GPS button)

  private static final float borderSize = 8; // Border around the chart
  private static final float separatorSize = 24; // Separator between two zones
  private static final int minBarHeight = 90;
  private static final int maxBarHeight = 150;
  private static final double chartSize = 0.85;

  private final Paint paint = new Paint();
  private final Paint fontPaint = new Paint();

  private double[] hrzData = null;

  public HRZonesBar(Context ctx) {
    super(ctx);
  }

  public void pushHrzData(double[] data) {
    this.hrzData = data;
  }

  public void onDraw(Canvas canvas) {
    if (hrzData == null) {
      return;
    }

    // calculate bar height and chart offset
    AppCompatActivity activity = (AppCompatActivity) getContext();
    LinearLayout buttons = activity.findViewById(R.id.buttons);

    int actualHeight = getHeight() - buttons.getHeight();
    float calculatedBarHeight =
        (actualHeight - 2 * borderSize - (hrzData.length - 1) * separatorSize)
            / hrzData.length; // Height of the bar
    calculatedBarHeight = calculatedBarHeight > maxBarHeight ? maxBarHeight : calculatedBarHeight;
    int topOffset = getTop();

    float totalWidth = getWidth();
    if (totalWidth <= 0 || calculatedBarHeight < 10) {
      Log.e(getClass().getName(), "Not enough space to display the heart-rate zone bar");
      activity.findViewById(R.id.hrzonesBarLayout).setVisibility(View.GONE);
      return;
    }

    // Font size and style - modern and readable (85% of 56)
    int fontSize = 48;
    fontPaint.setTextSize(fontSize);
    fontPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
    fontPaint.setColor(getResources().getColor(org.runnerup.R.color.colorText));
    fontPaint.setStyle(Paint.Style.FILL);
    fontPaint.setTextAlign(Paint.Align.LEFT);
    fontPaint.setFakeBoldText(true);

      paint.setStrokeWidth(2);
      paint.setStyle(Paint.Style.FILL);
      paint.setAntiAlias(true);

    canvas.drawColor(Color.TRANSPARENT);

    // calculate sum for percentage calculation
    double sum = 0;
    for (double aHrzData : hrzData) {
      sum += aHrzData;
    }

    // do the drawing
    for (int i = 0; i < hrzData.length; i++) {
      paint.setColor(zoneColor);

      // calculate per cent value of Zone duration
      double hrzPart = hrzData[i] / sum;
      float percent = Math.round((float) hrzPart * 100);

      // calculate text and bar length
      String zoneName = getResources().getString(org.runnerup.common.R.string.Zone) + " " + i;
      float textLen = fontPaint.measureText(zoneName);
      String percentText = percent + "%";
      float percentTextLen = fontPaint.measureText(percentText);
      
      // Reserve space for labels and add margins
      float labelAreaWidth = textLen + percentTextLen + 6 * borderSize;
      float chartWidth = (float) ((totalWidth - labelAreaWidth) * chartSize);
      float barLen = (float) (chartWidth * hrzPart);

      // elements x-offset with margin between labels and bars (more space)
      float zoneOffset = borderSize;
      float barOffset = zoneOffset + textLen + 4 * borderSize;
      float percentOffset = barOffset + chartWidth + 2 * borderSize;
      
      // Center text vertically on the bar - account for separatorSize
      float barTop = topOffset + i * calculatedBarHeight + (i + 1) * (borderSize + separatorSize);
      float y = barTop + calculatedBarHeight / 2 + fontSize / 3;

      // draw actual values and bars
      canvas.drawText(zoneName, zoneOffset, y, fontPaint);
      canvas.drawText(percent + "%", percentOffset, y, fontPaint);

      if (hrzPart >= 0) {
        // Draw rounded rectangles for modern look - use the barTop already calculated
        android.graphics.RectF rect = new android.graphics.RectF(
            barOffset,
            barTop,
            barOffset + barLen,
            barTop + calculatedBarHeight);
        
        // Draw rounded corners
        float cornerRadius = 6f;
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
        
        // Add subtle border/outline
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.argb(50, 255, 255, 255)); // Semi-transparent white border
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
        paint.setStyle(Paint.Style.FILL);
      }
    }
  }
}
