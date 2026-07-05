package de.insulink.api.web.app.glucose;

import de.insulink.api.glucose.GlucoseEntry;
import de.insulink.api.glucose.GlucoseRepository;
import de.insulink.api.prediction.PredictionClient;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import jakarta.servlet.http.HttpServletRequest;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class GlucosePredictionController extends AppRestController {
  // The forecast needs at most ~1h of trailing readings for its lags/windows; 2h
  // gives margin. Longer histories only add rows the model ignores.
  private static final long HISTORY_WINDOW_MS = 2 * 60 * 60 * 1000L;

  private final GlucoseRepository glucoseRepository;
  private final PredictionClient predictionClient;

  private GlucosePredictionController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, GlucoseRepository glucoseRepository,
    PredictionClient predictionClient
  ) {
    super(authenticationKey, userRepository);
    this.glucoseRepository = glucoseRepository;
    this.predictionClient = predictionClient;
  }

  @AppEndpoint
  @RequestMapping(path = "/glucose/predict/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> predictGlucose(HttpServletRequest request) {
    var horizon = parseHorizon(request.getParameter("horizon"));
    if (horizon == 0) {
      return ApiResponse.error(1500, "horizon must be 30 or 60").future();
    }
    // The client's freshest reading, passed inline because the background report
    // that syncs it to the DB is debounced and can arrive AFTER this request —
    // so without it the forecast would anchor to the previous reading.
    var latest = parseLatest(request);
    return glucoseRepository.findByUserId(findUserId(request))
      .thenApply(entries -> recentReadings(entries, latest))
      .thenCompose(readings -> predictionClient.predict(readings, horizon))
      .thenApply(this::predictionResponse)
      .exceptionally(_ -> ApiResponse.error(1501, "prediction unavailable"));
  }

  /** 30/60 valid (30 also the default); anything else -> 0 = reject. */
  private int parseHorizon(String value) {
    if ("60".equals(value)) {
      return 60;
    }
    return value == null || "30".equals(value) ? 30 : 0;
  }

  /** The client's current reading from the value/time query params, or null. */
  private GlucoseEntry parseLatest(HttpServletRequest request) {
    var value = request.getParameter("value");
    var time = request.getParameter("time");
    if (value == null || time == null) {
      return null;
    }
    try {
      return GlucoseEntry.create(null, null, Float.parseFloat(value), Long.parseLong(time));
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private List<GlucoseEntry> recentReadings(List<GlucoseEntry> entries, GlucoseEntry latest) {
    var combined = new ArrayList<>(entries);
    if (latest != null
      && entries.stream().noneMatch(entry -> entry.recordedAt() == latest.recordedAt())) {
      combined.add(latest);
    }
    var newest = combined.stream().mapToLong(GlucoseEntry::recordedAt).max().orElse(0L);
    return combined.stream()
      .filter(entry -> entry.recordedAt() >= newest - HISTORY_WINDOW_MS)
      .sorted(Comparator.comparingLong(GlucoseEntry::recordedAt))
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
