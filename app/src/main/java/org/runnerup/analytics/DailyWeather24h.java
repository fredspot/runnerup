package org.runnerup.analytics;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 24 hourly temperature (°C) and relative humidity (%) values for one UTC day at a ~11 km grid cell.
 */
public final class DailyWeather24h {

  public static final int HOURS_PER_DAY = 24;

  public final String dateUtc;
  public final int latCell;
  public final int lonCell;
  public final double[] hourlyTempC;
  public final double[] hourlyHumidityPct;

  public DailyWeather24h(
      String dateUtc,
      int latCell,
      int lonCell,
      double[] hourlyTempC,
      double[] hourlyHumidityPct) {
    this.dateUtc = dateUtc;
    this.latCell = latCell;
    this.lonCell = lonCell;
    this.hourlyTempC = copyOrFill(hourlyTempC);
    this.hourlyHumidityPct = copyOrFill(hourlyHumidityPct);
  }

  private static double[] copyOrFill(double[] values) {
    double[] out = new double[HOURS_PER_DAY];
    for (int i = 0; i < HOURS_PER_DAY; i++) {
      out[i] = values != null && i < values.length ? values[i] : Double.NaN;
    }
    return out;
  }

  public static int toCell(double deg) {
    return (int) Math.round(deg * 10.0);
  }

  public static double cellCenterDeg(int cell) {
    return cell / 10.0;
  }

  public static String utcDate(long epochSeconds) {
    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
    return fmt.format(new Date(epochSeconds * 1000L));
  }

  public static int utcHour(long epochSeconds) {
    long secondsInDay = ((epochSeconds % 86400L) + 86400L) % 86400L;
    return (int) (secondsInDay / 3600L);
  }

  /**
   * Average temperature and relative humidity across the UTC hours touched by {@code [startSec,
   * startSec + durationSec]}.
   */
  public HourMatch matchWindow(long startSec, double durationSec) {
    int startHour = utcHour(startSec);
    long endSec = startSec + Math.max(0L, Math.round(durationSec));
    int endHour = utcHour(endSec);
    double tempSum = 0.0;
    int tempN = 0;
    double humSum = 0.0;
    int humN = 0;
    int h = startHour;
    for (int step = 0; step < HOURS_PER_DAY; step++) {
      double t = hourlyTempC[h];
      if (!Double.isNaN(t)) {
        tempSum += t;
        tempN++;
      }
      double rh = hourlyHumidityPct[h];
      if (!Double.isNaN(rh)) {
        humSum += rh;
        humN++;
      }
      if (h == endHour) break;
      h = (h + 1) % HOURS_PER_DAY;
    }
    if (tempN == 0) {
      for (double t : hourlyTempC) {
        if (!Double.isNaN(t)) {
          tempSum += t;
          tempN++;
        }
      }
    }
    if (humN == 0) {
      for (double rh : hourlyHumidityPct) {
        if (!Double.isNaN(rh)) {
          humSum += rh;
          humN++;
        }
      }
    }
    Double temp = tempN > 0 ? tempSum / tempN : null;
    Double hum = humN > 0 ? humSum / humN : null;
    return new HourMatch(temp, hum, startHour);
  }

  public static String encode24(double[] values) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < HOURS_PER_DAY; i++) {
      if (i > 0) sb.append(',');
      double v = values != null && i < values.length ? values[i] : Double.NaN;
      if (Double.isNaN(v)) {
        sb.append("");
      } else {
        sb.append(String.format(Locale.US, "%.1f", v));
      }
    }
    return sb.toString();
  }

  public static double[] decode24(String csv) {
    double[] out = new double[HOURS_PER_DAY];
    for (int i = 0; i < HOURS_PER_DAY; i++) out[i] = Double.NaN;
    if (csv == null || csv.isEmpty()) return out;
    String[] parts = csv.split(",", -1);
    int n = Math.min(HOURS_PER_DAY, parts.length);
    for (int i = 0; i < n; i++) {
      String p = parts[i].trim();
      if (!p.isEmpty()) {
        try {
          out[i] = Double.parseDouble(p);
        } catch (NumberFormatException ignored) {
        }
      }
    }
    return out;
  }

  public static final class HourMatch {
    public final Double temperatureC;
    public final Double humidityPct;
    public final int utcHour;

    public HourMatch(Double temperatureC, Double humidityPct, int utcHour) {
      this.temperatureC = temperatureC;
      this.humidityPct = humidityPct;
      this.utcHour = utcHour;
    }
  }
}
