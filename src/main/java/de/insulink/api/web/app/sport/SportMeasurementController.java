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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * The user's body/activity measurements (steps, distance, calories, weight).
 * {@code find} returns them all; {@code sync} merges the pushed entries by natural
 * key (type + timestamp) — inserting new ones and updating changed ones without
 * deleting, so an import never destroys existing history; {@code delete} removes
 * exactly the entries the app names.
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
      MeasurementSync.create(measurementRepository).merge(existing, fresh));
  }

  /**
   * Deletes the named entries for the user — each identified by its type and
   * timestamp — and nothing else, so removing one entry never touches the rest.
   */
  @AppEndpoint
  @RequestMapping(path = "/sport/measurements/delete/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> deleteMeasurements(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var targets = ApiRequestBody.of(payload, response).getObjectList("entries")
      .stream().map(this::deletionKey).collect(Collectors.toSet());
    return measurementRepository.findByUserId(userId).thenCompose(existing ->
      MeasurementSync.create(measurementRepository).remove(existing, targets));
  }

  private MeasurementSync.MeasurementKey deletionKey(ApiRequestBody entry) {
    return MeasurementSync.MeasurementKey.of(
      SportMeasurementType.valueOf(entry.getString("type")), entry.getLong("time"));
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
