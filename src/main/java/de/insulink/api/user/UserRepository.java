package de.insulink.api.user;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
public interface UserRepository extends DatabaseRepository<User, UUID> {
  @Async
  CompletableFuture<Optional<User>> findByName(String name);
}