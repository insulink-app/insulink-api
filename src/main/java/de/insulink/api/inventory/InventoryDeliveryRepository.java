package de.insulink.api.inventory;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface InventoryDeliveryRepository extends DatabaseRepository<InventoryDelivery, UUID> {
  @Async
  CompletableFuture<List<InventoryDelivery>> findByUserId(UUID userId);
}
