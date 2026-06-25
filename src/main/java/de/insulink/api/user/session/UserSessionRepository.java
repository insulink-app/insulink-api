package de.insulink.api.user.session;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface UserSessionRepository extends DatabaseRepository<UserSession, UUID> {
  @Async
  CompletableFuture<List<UserSession>> findByUserIdAndStatus(
    UUID userId, UserSessionStatus status);
}