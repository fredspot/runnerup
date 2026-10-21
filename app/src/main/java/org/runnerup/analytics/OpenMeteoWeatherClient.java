package org.runnerup.analytics;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Fetches 24-hour temperature (`temperature_2m`) and relative humidity (`relative_humidity_2m`)
 * for a given UTC date and coordinate from Open-Meteo (archive API with forecast fallback for
 * recent days).
 */
public class OpenMeteoWeatherClient {

  private static final String ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive";
  private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";

  private final OkHttpClient httpClient;

  public OpenMeteoWeatherClient() {
    this(
        new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build());
  }

  public OpenMeteoWeatherClient(OkHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public DailyWeather24h fetchDay(String dateUtc, int latCell, int lonCell) throws IOException {
    double lat = DailyWeather24h.cellCenterDeg(latCell);
    double lon = DailyWeather24h.cellCenterDeg(lonCell);
    DailyWeather24h fromArchive = tryFetch(ARCHIVE_URL, dateUtc, latCell, lonCell, lat, lon);
    if (fromArchive != null && hasAnyHour(fromArchive)) {
      return fromArchive;
    }
    return tryFetch(FORECAST_URL, dateUtc, latCell, lonCell, lat, lon);
  }

  private DailyWeather24h tryFetch(
      String baseUrl, String dateUtc, int latCell, int lonCell, double lat, double lon)
      throws IOException {
    String url =
        String.format(
            Locale.US,
            "%s?latitude=%.2f&longitude=%.2f&start_date=%s&end_date=%s"
                + "&hourly=temperature_2m,relative_humidity_2m&timezone=UTC",
            baseUrl,
            lat,
            lon,
            dateUtc,
            dateUtc);
    Request req =
        new Request.Builder().url(url).header("User-Agent", "RunnerUp-Android").get().build();
    try (Response resp = httpClient.newCall(req).execute()) {
      if (!resp.isSuccessful()) return null;
      ResponseBody body = resp.body();
      if (body == null) return null;
      return parseResponse(dateUtc, latCell, lonCell, body.string());
    }
  }

  public static DailyWeather24h parseResponse(
      String dateUtc, int latCell, int lonCell, String json) {
    try {
      JSONObject root = new JSONObject(json);
      JSONObject hourly = root.optJSONObject("hourly");
      if (hourly == null) return null;
      JSONArray tempsJson = hourly.optJSONArray("temperature_2m");
      JSONArray humsJson = hourly.optJSONArray("relative_humidity_2m");
      if (tempsJson == null || humsJson == null) return null;
      double[] temps = new double[DailyWeather24h.HOURS_PER_DAY];
      double[] hums = new double[DailyWeather24h.HOURS_PER_DAY];
      for (int i = 0; i < DailyWeather24h.HOURS_PER_DAY; i++) {
        temps[i] =
            i < tempsJson.length() && !tempsJson.isNull(i)
                ? tempsJson.optDouble(i, Double.NaN)
                : Double.NaN;
        hums[i] =
            i < humsJson.length() && !humsJson.isNull(i)
                ? humsJson.optDouble(i, Double.NaN)
                : Double.NaN;
      }
      DailyWeather24h day = new DailyWeather24h(dateUtc, latCell, lonCell, temps, hums);
      return hasAnyHour(day) ? day : null;
    } catch (JSONException e) {
      return null;
    }
  }

  private static boolean hasAnyHour(DailyWeather24h day) {
    for (int i = 0; i < DailyWeather24h.HOURS_PER_DAY; i++) {
      if (!Double.isNaN(day.hourlyTempC[i])) return true;
    }
    return false;
  }
}
