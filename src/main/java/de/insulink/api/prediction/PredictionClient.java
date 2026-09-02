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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Calls the internal Python forecast service (insulink-predictor). The service
 * pulls the user's glucose history from the DB itself; this only forwards the
 * user id and the freshest reading(s) not yet ingested, and returns the parsed
 * reply. Stateless — the model lives in the Python sidecar; this shuttles JSON.
 */
@RequiredArgsConstructor(staticName = "create")
public final class PredictionClient {
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private final PredictionConfiguration configuration;

  public CompletableFuture<JSONObject> predict(
    UUID userId, List<GlucoseEntry> readings, int horizon
  ) {
    return send("/predict", requestBody(userId, readings, horizon));
  }

  /**
   * Asks for the forecasts the model WOULD have made between {@code since} and
   * {@code until} (epoch ms, either null to leave the sidecar its own default),
   * one per grid bucket, so the app can score them against the readings it holds.
   * The sidecar answers with the point forecast, its band and the persistence
   * baseline for each anchor; nothing here judges any of it.
   */
  public CompletableFuture<JSONObject> backtest(
    UUID userId, int horizon, Long since, Long until
  ) {
    return send("/backtest", new JSONObject()
      .put("user_id", userId.toString())
      .put("horizon_min", horizon)
      .putOpt("since", since)
      .putOpt("until", until));
  }

  private CompletableFuture<JSONObject> send(String path, JSONObject body) {
    var request = HttpRequest.newBuilder()
      .uri(URI.create(configuration.serviceUrl() + path))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
      .build();
    return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
      .thenApply(response -> new JSONObject(response.body()));
  }

  private JSONObject requestBody(UUID userId, List<GlucoseEntry> readings, int horizon) {
    var array = new JSONArray();
    for (var reading : readings) {
      array.put(new JSONObject().put("ts", reading.recordedAt())
        .put("mgdl", reading.value()));
    }
    return new JSONObject()
      .put("user_id", userId.toString())
      .put("readings", array)
      .put("horizon_min", horizon);
  }
}
