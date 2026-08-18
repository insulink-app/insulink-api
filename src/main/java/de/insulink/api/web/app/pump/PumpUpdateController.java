package de.insulink.api.web.app.pump;

import de.insulink.api.pump.Pump;
import de.insulink.api.pump.PumpRepository;
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
 * Refreshes the stored blob of a pump that is already registered.
 *
 * <p>A running pod's stored state changes as its counters advance, so the app
 * rewrites the blob rather than registering a second pump for the same pod.
 */
@RestController
public final class PumpUpdateController extends AppRestController {
  private final PumpRepository pumpRepository;

  private PumpUpdateController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, PumpRepository pumpRepository
  ) {
    super(authenticationKey, userRepository);
    this.pumpRepository = pumpRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/pump/update/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> updatePump(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var userId = findUserId(request);
    var pumpId = body.getUUID("pump_id");
    var data = body.getString("data");
    return pumpRepository.findById(pumpId)
      .thenCompose(pump -> applyUpdate(pump, data, userId));
  }

  private CompletableFuture<ApiResponse> applyUpdate(
    Optional<Pump> pump, String data, UUID userId
  ) {
    return pump.isEmpty() ?
      ApiResponse.error(1000).future() :
      updatePump(pump.get(), data, userId);
  }

  /**
   * Writes the new blob, refusing a pump that belongs to somebody else so a
   * guessed id cannot overwrite another account's pod.
   */
  private CompletableFuture<ApiResponse> updatePump(
    Pump pump, String data, UUID userId
  ) {
    if (!pump.userId().equals(userId)) {
      return ApiResponse.error(1001).future();
    }
    pump.updateData(data);
    return pumpRepository.save(pump).thenApply(_ -> ApiResponse.success());
  }
}
