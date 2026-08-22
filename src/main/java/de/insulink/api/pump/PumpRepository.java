package de.insulink.api.pump;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface PumpRepository extends DatabaseRepository<Pump, UUID> {
  @Async
  CompletableFuture<List<Pump>> findByUserId(UUID userId);

  @Async
  /**
   * The pod to offer back to the app: the newest one the user has NOT said is
   * gone. A discarded pod stays in the table for the history and is skipped
   * here, which is the whole difference between discarding and deleting.
   */
  CompletableFuture<Optional<Pump>>
    findFirstByUserIdAndDiscardedAtIsNullOrderByRegisteredAtDesc(UUID userId);
}