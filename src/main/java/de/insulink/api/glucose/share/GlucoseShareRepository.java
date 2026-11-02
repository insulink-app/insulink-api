package de.insulink.api.glucose.share;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface GlucoseShareRepository extends DatabaseRepository<GlucoseShare, UUID> {
  @Async
  CompletableFuture<List<GlucoseShare>> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
