package de.insulink.api.nutrition;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface MealRepository extends DatabaseRepository<Meal, UUID> {
  @Async
  CompletableFuture<List<Meal>> findByUserIdOrderByTime(UUID userId);
}
