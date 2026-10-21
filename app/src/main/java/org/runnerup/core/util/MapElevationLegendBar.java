package org.runnerup.core.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Horizontal color ramp matching {@link MapElevationColor} (blue low → red high). */
public class MapElevationLegendBar extends View {

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private int lastWidth = -1;

  public MapElevationLegendBar(Context context) {
    super(context);
  }

  public MapElevationLegendBar(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public MapElevationLegendBar(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    int w = getWidth();
    int h = getHeight();
    if (w <= 0 || h <= 0) {
      return;
    }
    if (lastWidth != w) {
      int n = 16;
      int[] colors = new int[n];
      for (int i = 0; i < n; i++) {
        colors[i] = MapElevationColor.mix(i / (double) (n - 1));
      }
      paint.setShader(
          new LinearGradient(0, 0, w, 0, colors, null, Shader.TileMode.CLAMP));
      lastWidth = w;
    }
    float r = h / 2f;
    canvas.drawRoundRect(0, 0, w, h, r, r, paint);
  }
}
