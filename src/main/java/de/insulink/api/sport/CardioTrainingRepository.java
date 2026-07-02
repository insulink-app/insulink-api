package de.insulink.api.sport;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface CardioTrainingRepository
  extends DatabaseRepository<CardioTraining, UUID> {
  @Async
  CompletableFuture<List<CardioTraining>> findByUserIdOrderByStartedAt(UUID userId);
}
