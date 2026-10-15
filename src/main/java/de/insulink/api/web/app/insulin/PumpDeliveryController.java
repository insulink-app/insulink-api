package de.insulink.api.web.app.insulin;

import de.insulink.api.insulin.delivery.PumpDelivery;
import de.insulink.api.insulin.delivery.PumpDeliveryRepository;
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
 * The pump's delivery log: every bolus, prime and cannula volume, oldest first
 * ({@code {at, kind, source, units, confirmed}}). {@code sync} merges the app's
 * local log by {@code at} (see {@link PumpDeliverySync}). Not read as insulin on
 * board anywhere — see {@link PumpDelivery}.
 */
@RestController
public final class PumpDeliveryController extends AppRestController {
  /** Matches the app's local cap (200 deliveries + 20 unconfirmed), with slack. */
  private static final int MAX_DELIVERIES = 250;
  private static final int MAX_SLUG_LENGTH = 32;

  private final PumpDeliveryRepository deliveryRepository;

  private PumpDeliveryController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, PumpDeliveryRepository deliveryRepository
  ) {
    super(authenticationKey, userRepository);
    this.deliveryRepository = deliveryRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/insulin/delivery/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findDeliveries(HttpServletRequest request) {
    var sync = PumpDeliverySync.create(deliveryRepository);
    return deliveryRepository.findByUserIdOrderByDeliveredAt(findUserId(request))
      .thenApply(deliveries -> deliveries.stream().map(sync::information).toList())
      .thenApply(deliveries -> ApiResponse.success(Map.of("deliveries", deliveries)));
  }

  @AppEndpoint
  @RequestMapping(path = "/insulin/delivery/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncDeliveries(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var fresh = ApiRequestBody.of(payload, response).getObjectList("deliveries")
      .stream().limit(MAX_DELIVERIES).map(entry -> delivery(userId, entry)).toList();
    return deliveryRepository.findByUserIdOrderByDeliveredAt(userId).thenCompose(existing ->
      PumpDeliverySync.create(deliveryRepository).merge(existing, fresh));
  }

  private PumpDelivery delivery(UUID userId, ApiRequestBody entry) {
    return PumpDelivery.create(UUID.randomUUID(), userId, entry.getLong("at"),
      entry.getString("kind", MAX_SLUG_LENGTH), entry.getString("source", MAX_SLUG_LENGTH),
      entry.getDouble("units"), entry.getBoolean("confirmed"));
  }
}
