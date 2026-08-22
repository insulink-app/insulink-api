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
 * Marks a stored pump as gone, so it stops being offered back to the app.
 *
 * The record exists so a reinstalled app can reconnect to a pod somebody is
 * still wearing. Once that pod is dead it has nothing left to offer, and the app
 * went on suggesting it at every launch with no way to say no.
 *
 * The row is KEPT. It is the user's pump history and belongs in the log whatever
 * happened to the hardware; what has to stop is only the offering, which
 * {@code findFirstByUserIdAndDiscardedAtIsNullOrderByRegisteredAtDesc} does by
 * skipping it. Deleting would answer the same question by throwing away the
 * record of a pod that was worn, which is not the same thing at all.
 */
@RestController
public final class PumpDiscardController extends AppRestController {
  private final PumpRepository pumpRepository;

  private PumpDiscardController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, PumpRepository pumpRepository
  ) {
    super(authenticationKey, userRepository);
    this.pumpRepository = pumpRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/pump/discard/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> discardPump(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var userId = findUserId(request);
    var pumpId = body.getUUID("pump_id");
    return pumpRepository.findById(pumpId)
      .thenCompose(pump -> discard(pump, userId));
  }

  private CompletableFuture<ApiResponse> discard(
    Optional<Pump> pump, UUID userId
  ) {
    return pump.isEmpty() ?
      ApiResponse.error(1000).future() :
      discardOwned(pump.get(), userId);
  }

  /**
   * Marks the record, refusing a pump that belongs to somebody else so a guessed
   * id cannot retire another account's pod.
   */
  private CompletableFuture<ApiResponse> discardOwned(Pump pump, UUID userId) {
    if (!pump.userId().equals(userId)) {
      return ApiResponse.error(1001).future();
    }
    pump.discard(System.currentTimeMillis());
    return pumpRepository.save(pump).thenApply(_ -> ApiResponse.success());
  }
}
