package de.insulink.api.insulin.bolus;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface BolusRepository extends DatabaseRepository<BolusEntry, UUID> {
  @Async
  CompletableFuture<List<BolusEntry>> findByUserIdOrderByRecordedAt(UUID userId);
}
