package de.insulink.api.insulin.delivery;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface PumpDeliveryRepository extends DatabaseRepository<PumpDelivery, UUID> {
  @Async
  CompletableFuture<List<PumpDelivery>> findByUserIdOrderByDeliveredAt(UUID userId);
}
