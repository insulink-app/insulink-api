package de.insulink.api.insulin.basal;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface BasalRepository extends DatabaseRepository<BasalEntry, UUID> {
  @Async
  CompletableFuture<List<BasalEntry>> findByUserIdOrderByRecordedAt(UUID userId);
}
