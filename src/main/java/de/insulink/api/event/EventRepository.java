package de.insulink.api.event;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface EventRepository extends DatabaseRepository<Event, UUID> {
  @Async
  CompletableFuture<List<Event>> findByUserId(UUID userId);
}
