package de.insulink.api.pump.contact;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface PumpContactRepository extends DatabaseRepository<PumpContact, UUID> {
  @Async
  CompletableFuture<List<PumpContact>> findByUserIdOrderByContactedAt(UUID userId);
}
