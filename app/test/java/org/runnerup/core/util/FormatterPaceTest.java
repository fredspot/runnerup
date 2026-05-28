package org.runnerup.core.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FormatterPaceTest {

  @Test
  public void formatElapsedTimeHms_zeroShowsDash() {
    assertTrue(Formatter.formatElapsedTimeHms(0).contains("--"));
  }

  @Test
  public void formatElapsedTimeHms_formatsMinutes() {
    String formatted = Formatter.formatElapsedTimeHms(125);
    assertTrue(formatted.contains("2:05"));
    assertFalse(formatted.contains("--"));
  }
}
