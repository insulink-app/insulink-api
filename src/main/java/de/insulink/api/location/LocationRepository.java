package de.insulink.api.location;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface LocationRepository extends DatabaseRepository<LocationEntry, UUID> {
  @Async
  CompletableFuture<List<LocationEntry>> findByUserId(UUID userId);
}
