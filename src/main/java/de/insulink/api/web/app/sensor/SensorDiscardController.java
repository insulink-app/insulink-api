package de.insulink.api.web.app.sensor;

import de.insulink.api.sensor.Sensor;
import de.insulink.api.sensor.SensorRepository;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Marks a stored sensor as gone, so it stops being offered back to the app.
 *
 * The mirror of {@code /pump/discard/}, and it exists for the same reason: the
 * record is what lets a reinstalled app pick up a sensor still on the body, so
 * once that sensor is off it has nothing left to offer and would be suggested at
 * every launch with no way to say no. Dismissing the offer used to write a flag
 * on the DEVICE, which is no use at all in the one situation the offer is for:
 * a fresh install has no local flags.
 *
 * The row is KEPT. It is the user's sensor history and belongs in the log
 * whatever happened to the hardware; what has to stop is only the offering,
 * which {@code findFirstByUserIdAndDiscardedAtIsNullOrderByRegisteredAtDesc}
 * does by skipping it.
 */
@RestController
public final class SensorDiscardController extends AppRestController {
  private final SensorRepository sensorRepository;

  private SensorDiscardController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, SensorRepository sensorRepository
  ) {
    super(authenticationKey, userRepository);
    this.sensorRepository = sensorRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/sensor/discard/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> discardSensor(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var userId = findUserId(request);
    var sensorId = body.getUUID("sensor_id");
    return sensorRepository.findById(sensorId)
      .thenCompose(sensor -> discard(sensor, userId));
  }

  private CompletableFuture<ApiResponse> discard(
    Optional<Sensor> sensor, UUID userId
  ) {
    return sensor.isEmpty() ?
      ApiResponse.error(1000).future() :
      discardOwned(sensor.get(), userId);
  }

  /**
   * Marks the record, refusing a sensor that belongs to somebody else so a
   * guessed id cannot retire another account's sensor.
   */
  private CompletableFuture<ApiResponse> discardOwned(
    Sensor sensor, UUID userId
  ) {
    if (!sensor.userId().equals(userId)) {
      return ApiResponse.error(1001).future();
    }
    sensor.discard(System.currentTimeMillis());
    return sensorRepository.save(sensor).thenApply(_ -> ApiResponse.success());
  }
}
