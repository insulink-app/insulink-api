package de.insulink.api.web.app.insulin;

import de.insulink.api.insulin.delivery.PumpDelivery;
import de.insulink.api.insulin.delivery.PumpDeliveryRepository;
import de.insulink.api.iterator.AsyncIterator;
import de.insulink.api.web.response.ApiResponse;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reconciles the stored pump deliveries against the app's local log, keyed by
 * {@code deliveredAt}: {@link #merge} inserts unseen deliveries and updates one
 * whose units or confirmation changed (a bolus stopped part-way), without ever
 * deleting — the app wipes its log with every pod, the account keeps it all.
 */
@RequiredArgsConstructor(staticName = "create")
final class PumpDeliverySync {
  private final PumpDeliveryRepository deliveryRepository;

  CompletableFuture<ApiResponse> merge(
    List<PumpDelivery> existing, List<PumpDelivery> fresh
  ) {
    var existingByTime = existing.stream().collect(Collectors.toMap(
      PumpDelivery::deliveredAt, Function.identity(), (kept, _) -> kept));
    var changes = fresh.stream()
      .map(delivery -> reconcile(existingByTime, delivery))
      .filter(Objects::nonNull)
      .toList();
    return AsyncIterator.execute(changes, deliveryRepository::save)
      .thenApply(_ -> ApiResponse.success());
  }

  /**
   * The row to save for one pushed delivery: the delivery itself when its time
   * is new, a corrected copy carrying the existing id when units or confirmation
   * moved, or {@code null} when nothing changed (so it is skipped).
   */
  private PumpDelivery reconcile(
    Map<Long, PumpDelivery> existingByTime, PumpDelivery delivery
  ) {
    var match = existingByTime.get(delivery.deliveredAt());
    if (match == null) {
      return delivery;
    }
    if (match.units() == delivery.units() && match.confirmed() == delivery.confirmed()) {
      return null;
    }
    return PumpDelivery.create(match.id(), delivery.userId(), delivery.deliveredAt(),
      delivery.kind(), delivery.source(), delivery.units(), delivery.confirmed());
  }

  Map<String, Object> information(PumpDelivery delivery) {
    return Map.of("at", delivery.deliveredAt(), "kind", delivery.kind(),
      "source", delivery.source(), "units", delivery.units(),
      "confirmed", delivery.confirmed());
  }
}
