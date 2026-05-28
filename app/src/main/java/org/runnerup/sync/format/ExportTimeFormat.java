package org.runnerup.sync.format;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Shared timestamp formatting for activity export formats. */
public final class ExportTimeFormat {

  private static final SimpleDateFormat ISO_8601_UTC;
  private static final SimpleDateFormat RUNKEEPER;

  static {
    ISO_8601_UTC = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    ISO_8601_UTC.setTimeZone(TimeZone.getTimeZone("UTC"));
    RUNKEEPER = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
  }

  private ExportTimeFormat() {}

  /** GPX/TCX UTC ISO-8601 from epoch milliseconds. */
  public static String iso8601Utc(long epochMillis) {
    return ISO_8601_UTC.format(new Date(epochMillis));
  }

  /** RunKeeper API start_time format from epoch milliseconds. */
  public static String runKeeper(long epochMillis) {
    return RUNKEEPER.format(new Date(epochMillis));
  }
}
