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
  CompletableFuture<Optional<Pump>> findFirstByUserIdOrderByRegisteredAtDesc(UUID userId);
}