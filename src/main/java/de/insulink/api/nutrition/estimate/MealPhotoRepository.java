package de.insulink.api.nutrition.estimate;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface MealPhotoRepository extends DatabaseRepository<MealPhoto, UUID> {
  @Async
  CompletableFuture<Long> countByUserIdAndTakenAtGreaterThan(UUID userId, long takenAfter);
}
