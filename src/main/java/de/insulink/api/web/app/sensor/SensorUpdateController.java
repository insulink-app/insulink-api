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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class SensorUpdateController extends AppRestController {
  private final SensorRepository sensorRepository;

  private SensorUpdateController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, SensorRepository sensorRepository
  ) {
    super(authenticationKey, userRepository);
    this.sensorRepository = sensorRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/sensor/update/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> updateSensor(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var userId = findUserId(request);
    var sensorId = body.getUUID("sensor_id");
    var data = body.getString("data");
    return sensorRepository.findById(sensorId)
      .thenCompose(sensor -> sensor.isEmpty() ?
        ApiResponse.error(1000).future() :
        updateSensor(sensor.get(), data, userId));
  }

  private CompletableFuture<ApiResponse> updateSensor(
    Sensor sensor, String data, UUID userId
  ) {
    if (!sensor.userId().equals(userId)) {
      return ApiResponse.error(1001).future();
    }
    sensor.updateData(data);
    return sensorRepository.save(sensor).thenApply(_ -> ApiResponse.success());
  }
}