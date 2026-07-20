package de.insulink.api.inventory;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface InventoryItemRepository extends DatabaseRepository<InventoryItem, UUID> {
  @Async
  CompletableFuture<List<InventoryItem>> findByUserId(UUID userId);
}
