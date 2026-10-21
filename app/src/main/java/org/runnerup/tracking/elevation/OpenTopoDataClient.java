package org.runnerup.tracking.elevation;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * OpenTopoData public DEM. {@code copernicus30m} is not on the public API; Mapzen is a global ~30 m
 * dataset that is.
 */
public final class OpenTopoDataClient implements ElevationLookup {

  static final String ENDPOINT = "https://api.opentopodata.org/v1/mapzen";
  static final int MAX_LOCATIONS = 100;
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  private final OkHttpClient client;

  public OpenTopoDataClient() {
    this(
        new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build());
  }

  public OpenTopoDataClient(OkHttpClient client) {
    this.client = client;
  }

  @Override
  public double[] lookup(List<LatLng> locations) throws IOException {
    if (locations == null || locations.isEmpty()) {
      return new double[0];
    }
    if (locations.size() > MAX_LOCATIONS) {
      throw new IOException("OpenTopoData allows at most " + MAX_LOCATIONS + " locations");
    }
    String json = "{\"locations\":\"" + formatLocations(locations) + "\"}";
    RequestBody body = RequestBody.create(json, JSON);
    Request request =
        new Request.Builder()
            .url(ENDPOINT)
            .header("User-Agent", "RunnerUp (Android)")
            .post(body)
            .build();
    try (Response response = client.newCall(request).execute()) {
      ResponseBody responseBody = response.body();
      String payload = responseBody == null ? "" : responseBody.string();
      if (!response.isSuccessful()) {
        throw new IOException("OpenTopoData HTTP " + response.code() + ": " + payload);
      }
      if (payload.isEmpty()) {
        throw new IOException("OpenTopoData empty body");
      }
      return parseElevations(payload, locations.size());
    }
  }

  static String formatLocations(List<LatLng> locations) {
    StringBuilder path = new StringBuilder();
    for (int i = 0; i < locations.size(); i++) {
      if (i > 0) {
        path.append('|');
      }
      LatLng p = locations.get(i);
      path.append(String.format(Locale.US, "%.6f,%.6f", p.latitude, p.longitude));
    }
    return path.toString();
  }

  static double[] parseElevations(String json, int expectedCount) throws IOException {
    double[] elevations = new double[expectedCount];
    for (int i = 0; i < expectedCount; i++) {
      elevations[i] = Double.NaN;
    }
    int pos = 0;
    for (int i = 0; i < expectedCount; i++) {
      int key = json.indexOf("\"elevation\"", pos);
      if (key < 0) {
        break;
      }
      int colon = json.indexOf(':', key + 11);
      if (colon < 0) {
        break;
      }
      int start = colon + 1;
      while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
        start++;
      }
      pos = start;
      if (json.startsWith("null", start)) {
        pos = start + 4;
        continue;
      }
      int end = start;
      while (end < json.length()) {
        char c = json.charAt(end);
        if ((c >= '0' && c <= '9') || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E') {
          end++;
        } else {
          break;
        }
      }
      if (end == start) {
        throw new IOException("OpenTopoData parse error");
      }
      try {
        elevations[i] = Double.parseDouble(json.substring(start, end));
      } catch (NumberFormatException e) {
        throw new IOException("OpenTopoData parse error", e);
      }
      pos = end;
    }
    return elevations;
  }
}
