package de.insulink.api.sport;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface ActiveWorkoutRepository
  extends DatabaseRepository<ActiveWorkout, UUID> {
  @Async
  CompletableFuture<Optional<ActiveWorkout>> findByUserId(UUID userId);
}
