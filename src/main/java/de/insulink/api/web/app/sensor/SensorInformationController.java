package de.insulink.api.web.app.sensor;

import com.google.common.collect.Maps;
import de.insulink.api.sensor.Sensor;
import de.insulink.api.sensor.SensorRepository;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class SensorInformationController extends AppRestController {
  private final SensorRepository sensorRepository;

  private SensorInformationController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, SensorRepository sensorRepository
  ) {
    super(authenticationKey, userRepository);
    this.sensorRepository = sensorRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/sensor/current/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findCurrentSensor(
    HttpServletRequest request
  ) {
    var userId = findUserId(request);
    return sensorRepository
      .findFirstByUserIdAndDiscardedAtIsNullOrderByRegisteredAtDesc(userId)
      .thenApply(sensor -> sensor.map(this::sensorInformation)
        .map(ApiResponse::success).orElseGet(() -> ApiResponse.error(1000)));
  }

  @AppEndpoint
  @RequestMapping(path = "/sensor/history/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findSensorHistory(
    HttpServletRequest request
  ) {
    var userId = findUserId(request);
    return sensorRepository.findByUserId(userId)
      .thenApply(sensor -> ApiResponse.success(Map.of("sensors",
        sensor.stream().map(this::sensorInformation).toList())));
  }

  private Map<String, Object> sensorInformation(Sensor sensor) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", sensor.id());
    information.put("data", sensor.data());
    information.put("registered_at", sensor.registeredAt());
    information.put("expires_at", sensor.expiresAt());
    // Null for a sensor still being offered. Carried in the history so a reader
    // can tell one that ran its course from one the user said was gone.
    information.put("discarded_at", sensor.discardedAt());
    return information;
  }
}