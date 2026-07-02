package de.insulink.api.sport;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface WorkoutSessionRepository
  extends DatabaseRepository<WorkoutSession, UUID> {
  @Async
  CompletableFuture<List<WorkoutSession>> findByUserIdOrderByStartedAt(UUID userId);
}
