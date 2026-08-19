package de.insulink.api.web.app.insulin;

import de.insulink.api.insulin.InsulinType;
import de.insulink.api.insulin.basal.BasalEntry;
import de.insulink.api.insulin.basal.BasalRepository;
import de.insulink.api.iterator.AsyncListIterator;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Records basal insulin the pump delivered, which the forecasting sidecar reads
 * as part of insulin on board.
 *
 * <p><b>Append-only, not replace-all.</b> Every other sync in this API replaces a
 * whole collection because the app owns the truth and can resend it. These are
 * measurements of windows that have already passed: they cannot be revised, only
 * added to, and a replace-all would delete history the app no longer holds.
 *
 * <p>Boluses do not come through here — they arrive as meal records. Accepting them
 * twice would have the model count every dose twice.
 */
@RestController
public final class BasalSyncController extends AppRestController {
  /**
   * The most deliveries accepted in one request. The app batches to match; a body
   * beyond this is truncated rather than rejected, so a client that ignores the
   * limit still makes progress instead of failing forever.
   */
  private static final int MAX_DELIVERIES = 200;

  private final BasalRepository basalRepository;

  private BasalSyncController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, BasalRepository basalRepository
  ) {
    super(authenticationKey, userRepository);
    this.basalRepository = basalRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/insulin/basal/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncBasal(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var userId = findUserId(request);
    var deliveries = body.getObjectList("deliveries");
    if (deliveries.isEmpty()) {
      return ApiResponse.success(Map.of("stored", 0)).future();
    }
    var accepted = deliveries.subList(0, Math.min(deliveries.size(), MAX_DELIVERIES));
    return storeAll(accepted, userId);
  }

  private CompletableFuture<ApiResponse> storeAll(
    List<ApiRequestBody> deliveries, UUID userId
  ) {
    return AsyncListIterator
      .<ApiRequestBody, BasalEntry>execute(deliveries, delivery -> store(delivery, userId))
      .thenApply(this::storedResponse);
  }

  private ApiResponse storedResponse(List<BasalEntry> stored) {
    return ApiResponse.success(Map.of("stored", stored.size()));
  }

  /**
   * Stores one delivered window.
   *
   * <p>{@code glucose} is zero: a basal delivery has no glucose input that fed a
   * calculation, unlike the bolus rows this table's shape was drawn for. The
   * forecasting loader reads only the timestamp and the units.
   */
  private CompletableFuture<List<BasalEntry>> store(
    ApiRequestBody delivery, UUID userId
  ) {
    var units = delivery.getFloat("units");
    var recordedAt = delivery.getLong("at");
    if (units <= 0) {
      return CompletableFuture.completedFuture(List.of());
    }
    return basalRepository.generateAvailableId(UUID::randomUUID)
      .thenApply(id -> BasalEntry.create(id, userId, 0f, units,
        InsulinType.FAST_ACTING, recordedAt))
      .thenCompose(basalRepository::save)
      .thenApply(List::of);
  }
}
