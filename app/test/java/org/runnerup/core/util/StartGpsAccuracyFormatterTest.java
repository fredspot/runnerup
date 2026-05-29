package org.runnerup.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import org.junit.Test;
import org.runnerup.tracking.Tracker;

public class StartGpsAccuracyFormatterTest {

  @Test
  public void format_returnsEmptyForNonPositiveAccuracy() {
    Context ctx = mock(Context.class);
    Formatter formatter = mock(Formatter.class);
    assertEquals("", StartGpsAccuracyFormatter.format(ctx, formatter, null, -1f));
    assertEquals("", StartGpsAccuracyFormatter.format(ctx, formatter, null, 0f));
  }

  @Test
  public void format_includesAccuracyWhenPositive() {
    Context ctx = mock(Context.class);
    when(ctx.getString(any(Integer.class))).thenReturn("GPS %1$s");
    Formatter formatter = mock(Formatter.class);
    when(formatter.formatElevation(any(Formatter.Format.class), anyDouble())).thenReturn("12 m");
    String result = StartGpsAccuracyFormatter.format(ctx, formatter, (Tracker) null, 12f);
    assertTrue(result.length() > 0);
    assertTrue(result.contains("12 m"));
  }
}
