package de.insulink.api.web.app.health;

import de.insulink.api.health.Hba1cReading;
import de.insulink.api.health.Hba1cReadingRepository;
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
import java.util.stream.Collectors;

/**
 * The user's laboratory HbA1c readings. {@code find} returns them all;
 * {@code sync} merges the pushed readings by timestamp — inserting new ones and
 * correcting changed ones without deleting, so a device holding only part of the
 * history never destroys the rest; {@code delete} removes exactly the readings
 * the app names.
 */
@RestController
public final class Hba1cController extends AppRestController {
  private final Hba1cReadingRepository readingRepository;

  private Hba1cController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository,
    Hba1cReadingRepository readingRepository
  ) {
    super(authenticationKey, userRepository);
    this.readingRepository = readingRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/health/hba1c/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findReadings(HttpServletRequest request) {
    return readingRepository.findByUserId(findUserId(request))
      .thenApply(readings -> readings.stream().map(this::information).toList())
      .thenApply(readings -> ApiResponse.success(Map.of("readings", readings)));
  }

  @AppEndpoint
  @RequestMapping(path = "/health/hba1c/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncReadings(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var fresh = ApiRequestBody.of(payload, response).getObjectList("readings")
      .stream().map(reading -> reading(userId, reading)).toList();
    return readingRepository.findByUserId(userId).thenCompose(existing ->
      Hba1cSync.create(readingRepository).merge(existing, fresh));
  }

  /**
   * Deletes the named readings for the user — each identified by the instant it
   * was measured — and nothing else, so removing one never touches the rest.
   */
  @AppEndpoint
  @RequestMapping(path = "/health/hba1c/delete/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> deleteReadings(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var targets = ApiRequestBody.of(payload, response).getObjectList("readings")
      .stream().map(reading -> reading.getLong("time")).collect(Collectors.toSet());
    return readingRepository.findByUserId(userId).thenCompose(existing ->
      Hba1cSync.create(readingRepository).remove(existing, targets));
  }

  private Hba1cReading reading(UUID userId, ApiRequestBody reading) {
    return Hba1cReading.create(UUID.randomUUID(), userId,
      reading.getDouble("percent"), reading.getLong("time"));
  }

  private Map<String, Object> information(Hba1cReading reading) {
    return Map.of("percent", reading.percent(), "time", reading.recordedAt());
  }
}
