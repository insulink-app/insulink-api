package de.insulink.api.web.app.sensor;

import de.insulink.api.sensor.Sensor;
import de.insulink.api.sensor.SensorRepository;
import de.insulink.api.sensor.SensorType;
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

@RestController
public final class SensorRegistrationController extends AppRestController {
  private final SensorRepository sensorRepository;

  private SensorRegistrationController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, SensorRepository sensorRepository
  ) {
    super(authenticationKey, userRepository);
    this.sensorRepository = sensorRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/sensor/register/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> registerSensor(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var userId = findUserId(request);
    var type = SensorType.valueOf(body.getString("type"));
    var data = body.getString("data");
    var expiresAt = body.getLong("expires_at");
    return sensorRepository.generateAvailableId(UUID::randomUUID)
      // Never discarded: a sensor is only just being registered.
      .thenApply(id -> Sensor.create(id, userId, type, data,
        System.currentTimeMillis(), expiresAt, null))
      .thenCompose(sensorRepository::save)
      .thenApply(sensor -> ApiResponse.success(Map.of("sensor_id", sensor.id())));
  }
}