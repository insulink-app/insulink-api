package de.insulink.api.sport;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface SportRoutineRepository
  extends DatabaseRepository<SportRoutine, UUID> {
  @Async
  CompletableFuture<List<SportRoutine>> findByUserIdOrderByOrderIndex(UUID userId);
}
