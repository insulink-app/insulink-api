package de.insulink.api.web.app.glucose;

import de.insulink.api.glucose.GlucoseEntry;
import de.insulink.api.prediction.PredictionClient;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.request.ApiRequestBody;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class GlucosePredictionController extends AppRestController {
  private final PredictionClient predictionClient;

  private GlucosePredictionController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, PredictionClient predictionClient
  ) {
    super(authenticationKey, userRepository);
    this.predictionClient = predictionClient;
  }

  @AppEndpoint
  @RequestMapping(path = "/glucose/predict/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> predictGlucose(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var horizon = parseHorizon(body);
    if (horizon == 0) {
      return ApiResponse.error(1500, "horizon must be 30 or 60").future();
    }
    // The predictor pulls the user's glucose history from the DB itself; we only
    // forward the freshest reading(s) the debounced background sync may not have
    // written yet. Multiple readings let a client catch up after a sync outage.
    return predictionClient.predict(findUserId(request), parseLatest(body), horizon)
      .thenApply(this::predictionResponse)
      .exceptionally(_ -> ApiResponse.error(1501, "prediction unavailable"));
  }

  /**
   * Hands back the forecasts the model would have made over a past window, for the
   * app's own accuracy view. A read, like the forecast itself — and a POST for the
   * same reason: the parameters travel in the body.
   */
  @AppEndpoint
  @RequestMapping(path = "/glucose/predict/backtest/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> backtestGlucose(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var horizon = parseHorizon(body);
    if (horizon == 0) {
      return ApiResponse.error(1500, "horizon must be 30 or 60").future();
    }
    return predictionClient.backtest(findUserId(request), horizon,
      parseSince(body), parseBound(body, "until"))
      .thenApply(this::backtestResponse)
      .exceptionally(_ -> ApiResponse.error(1501, "prediction unavailable"));
  }

  /**
   * The window start as epoch ms, floored at 90 days back — the app's widest
   * analysis range, and the ceiling on how much history one request may replay.
   * Null when omitted, which leaves the sidecar its own default window.
   */
  private Long parseSince(ApiRequestBody body) {
    var since = parseBound(body, "since");
    if (since == null) {
      return null;
    }
    return Math.max(since, System.currentTimeMillis() - Duration.ofDays(90).toMillis());
  }

  /** One window bound as epoch ms, null when the app did not send it. */
  private Long parseBound(ApiRequestBody body, String key) {
    return body.has(key) ? body.getLong(key) : null;
  }

  private ApiResponse backtestResponse(JSONObject result) {
    return ApiResponse.success(Map.of(
      "horizon_min", result.getInt("horizon_min"),
      "grid_minutes", result.getInt("grid_minutes"),
      "points", result.getJSONArray("points").toList()
    ));
  }

  /** 30/60 valid (30 also the default when omitted); anything else -> 0 = reject. */
  private int parseHorizon(ApiRequestBody body) {
    if (!body.has("horizon")) {
      return 30;
    }
    var horizon = body.getInt("horizon");
    return horizon == 30 || horizon == 60 ? horizon : 0;
  }

  /**
   * The client's freshest readings from the body's {@code readings} array — each
   * a {@code {"value", "time"}} pair. Absent is valid: an empty list lets the
   * predictor anchor to the DB tip.
   */
  private List<GlucoseEntry> parseLatest(ApiRequestBody body) {
    if (!body.has("readings")) {
      return List.of();
    }
    return body.getObjectList("readings").stream()
      .map(reading -> GlucoseEntry.create(
        null, null, reading.getFloat("value"), reading.getLong("time")))
      .toList();
  }

  private ApiResponse predictionResponse(JSONObject result) {
    return ApiResponse.success(Map.of(
      "horizon_min", result.getInt("horizon_min"),
      "generated_at", result.getLong("generated_at"),
      "curve", result.getJSONArray("curve").toList()
    ));
  }
}
