package de.insulink.api.web.app.sport;

import de.insulink.api.sport.SportMeasurement;
import de.insulink.api.sport.SportMeasurementRepository;
import de.insulink.api.sport.SportMeasurementType;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.request.ApiRequestBody;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The user's body/activity measurements (steps, distance, calories, weight).
 * {@code find} returns them all; {@code sync} replaces the user's set with the
 * app's complete current list (the app owns deletions).
 */
@RestController
public final class SportMeasurementController extends AppRestController {
  private final SportMeasurementRepository measurementRepository;

  private SportMeasurementController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository,
    SportMeasurementRepository measurementRepository
  ) {
    super(authenticationKey, userRepository);
    this.measurementRepository = measurementRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/sport/measurements/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findMeasurements(
    HttpServletRequest request
  ) {
    return measurementRepository.findByUserId(findUserId(request))
      .thenApply(entries -> entries.stream().map(this::information).toList())
      .thenApply(entries -> ApiResponse.success(Map.of("entries", entries)));
  }

  @AppEndpoint
  @RequestMapping(path = "/sport/measurements/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncMeasurements(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var fresh = ApiRequestBody.of(payload, response).getObjectList("entries")
      .stream().map(entry -> measurement(userId, entry)).toList();
    return measurementRepository.findByUserId(userId).thenCompose(existing ->
      SportCollection.create(measurementRepository).replace(existing, fresh));
  }

  private SportMeasurement measurement(UUID userId, ApiRequestBody entry) {
    return SportMeasurement.create(UUID.randomUUID(), userId,
      SportMeasurementType.valueOf(entry.getString("type")),
      entry.getDouble("value"), entry.getLong("time"));
  }

  private Map<String, Object> information(SportMeasurement measurement) {
    return Map.of("type", measurement.type().name(),
      "value", measurement.value(), "time", measurement.recordedAt());
  }
}
