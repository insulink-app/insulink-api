package de.insulink.api.sensor;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface SensorRepository extends DatabaseRepository<Sensor, UUID> {
  @Async
  CompletableFuture<List<Sensor>> findByUserId(UUID userId);

  @Async
  /**
   * The sensor to offer back to the app: the newest one the user has NOT said is
   * gone. A discarded sensor stays in the table for the history and is skipped
   * here, which is the whole difference between discarding and deleting.
   */
  CompletableFuture<Optional<Sensor>>
    findFirstByUserIdAndDiscardedAtIsNullOrderByRegisteredAtDesc(UUID userId);
}