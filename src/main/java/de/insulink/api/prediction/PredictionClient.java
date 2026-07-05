package de.insulink.api.prediction;

import de.insulink.api.glucose.GlucoseEntry;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Calls the internal Python forecast service (insulink-predictor). Sends the
 * user's recent readings and returns the parsed prediction reply. Stateless —
 * the model lives in the Python sidecar; this only shuttles JSON.
 */
@RequiredArgsConstructor(staticName = "create")
public final class PredictionClient {
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private final PredictionConfiguration configuration;

  public CompletableFuture<JSONObject> predict(
    List<GlucoseEntry> readings, int horizon
  ) {
    var body = requestBody(readings, horizon).toString();
    var request = HttpRequest.newBuilder()
      .uri(URI.create(configuration.serviceUrl() + "/predict"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build();
    return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
      .thenApply(response -> new JSONObject(response.body()));
  }

  private JSONObject requestBody(List<GlucoseEntry> readings, int horizon) {
    var array = new JSONArray();
    for (var reading : readings) {
      array.put(new JSONObject().put("ts", reading.recordedAt())
        .put("mgdl", reading.value()));
    }
    return new JSONObject().put("readings", array).put("horizon_min", horizon);
  }
}
