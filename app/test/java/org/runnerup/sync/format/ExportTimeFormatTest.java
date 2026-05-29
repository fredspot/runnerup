package org.runnerup.sync.format;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExportTimeFormatTest {

  @Test
  public void iso8601Utc_epochZero() {
    assertEquals("1970-01-01T00:00:00Z", ExportTimeFormat.iso8601Utc(0L));
  }

  @Test
  public void runKeeper_containsYearAndTime() {
    // RunKeeper formatter uses default JVM timezone; assert stable shape only.
    String formatted = ExportTimeFormat.runKeeper(System.currentTimeMillis());
    assertTrue(formatted.matches("^[A-Za-z]{3}, \\d{2} [A-Za-z]{3} \\d{4} \\d{2}:\\d{2}:\\d{2}$"));
  }
}
