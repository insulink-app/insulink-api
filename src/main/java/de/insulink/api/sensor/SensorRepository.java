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
  CompletableFuture<Optional<Sensor>> findFirstByUserIdOrderByRegisteredAtDesc(UUID userId);
}