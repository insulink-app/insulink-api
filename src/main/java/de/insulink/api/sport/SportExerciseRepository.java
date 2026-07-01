package de.insulink.api.sport;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface SportExerciseRepository
  extends DatabaseRepository<SportExercise, UUID> {
  @Async
  CompletableFuture<List<SportExercise>> findByUserIdOrderByOrderIndex(UUID userId);
}
