package de.insulink.api.nutrition;

import de.insulink.api.database.UserOwnedRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface DrinkRepository extends UserOwnedRepository<Drink, UUID> {
  @Async
  CompletableFuture<List<Drink>> findByUserIdOrderByDrinkedAt(UUID userId);
}
