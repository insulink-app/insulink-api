package de.insulink.api.web.app.pump;

import de.insulink.api.pump.Pump;
import de.insulink.api.pump.PumpRepository;
import de.insulink.api.pump.PumpType;
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
 * Records a pump the app has paired, so the account can hand it back after the
 * app's local storage is lost.
 *
 * <p>The {@code data} field is an opaque blob the app writes and only the app
 * understands. That is deliberate and not laziness: the pod protocol lives in the
 * app under AGPL-3.0, and porting any of it here would make this API an AGPL
 * network service obliged to publish its source. The server stores bytes.
 */
@RestController
public final class PumpRegistrationController extends AppRestController {
  private final PumpRepository pumpRepository;

  private PumpRegistrationController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, PumpRepository pumpRepository
  ) {
    super(authenticationKey, userRepository);
    this.pumpRepository = pumpRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/pump/register/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> registerPump(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var userId = findUserId(request);
    var type = PumpType.valueOf(body.getString("type"));
    var data = body.getString("data");
    var expiresAt = body.getLong("expires_at");
    return pumpRepository.generateAvailableId(UUID::randomUUID)
      // Never discarded: a pod is only just being registered.
      .thenApply(id -> Pump.create(id, userId, type, data,
        System.currentTimeMillis(), expiresAt, null))
      .thenCompose(pumpRepository::save)
      .thenApply(this::registeredResponse);
  }

  private ApiResponse registeredResponse(Pump pump) {
    return ApiResponse.success(Map.of("pump_id", pump.id()));
  }
}
