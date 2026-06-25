package de.insulink.api.glucose;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface GlucoseRepository extends DatabaseRepository<GlucoseEntry, UUID> {
  @Async
  CompletableFuture<List<GlucoseEntry>> findByUserId(UUID userId);
}