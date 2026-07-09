package de.insulink.api.health;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface PulseSampleRepository
  extends DatabaseRepository<PulseSample, UUID> {
  @Async
  CompletableFuture<List<PulseSample>> findByUserIdOrderByRecordedAt(UUID userId);
}
