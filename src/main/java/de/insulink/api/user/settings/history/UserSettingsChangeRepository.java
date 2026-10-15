package de.insulink.api.user.settings.history;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface UserSettingsChangeRepository
  extends DatabaseRepository<UserSettingsChange, UUID> {
  @Async
  CompletableFuture<List<UserSettingsChange>> findByUserIdOrderByChangedAtDesc(
    UUID userId);
}
