package de.insulink.api.database;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Minting a row id. Every entity in the app gets its id this way, so a taken id
 * has to be retried rather than handed out — a duplicate would land as a save
 * over somebody else's row. Backed by a stand-in repository instead of a mock,
 * because a mock stubs the very default method under test away.
 */
final class DatabaseRepositoryTest {
  /**
   * The smallest thing that can answer {@code existsById}: a fixed set of ids
   * that count as taken, recording every id it was asked about.
   */
  private static final class TakenIds implements DatabaseRepository<String, UUID> {
    private final Set<UUID> taken;
    private final List<UUID> checked = new java.util.ArrayList<>();

    private TakenIds(Set<UUID> taken) {
      this.taken = taken;
    }

    @Override
    public CompletableFuture<Boolean> existsById(UUID id) {
      checked.add(id);
      return CompletableFuture.completedFuture(taken.contains(id));
    }

    @Override
    public CompletableFuture<Optional<String>> findById(UUID id) {
      return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<String>> findAll() {
      return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<String> save(String entity) {
      return CompletableFuture.completedFuture(entity);
    }

    @Override
    public CompletableFuture<Void> deleteById(UUID id) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> delete(String entity) {
      return CompletableFuture.completedFuture(null);
    }
  }

  private UUID freeId;
  private UUID takenId;

  @BeforeEach
  void createIds() {
    freeId = UUID.randomUUID();
    takenId = UUID.randomUUID();
  }

  @Test
  void anUnusedIdIsHandedOutAsGenerated() {
    var repository = new TakenIds(Set.of());
    Assertions.assertEquals(freeId,
      repository.generateAvailableId(() -> freeId).join());
    Assertions.assertEquals(List.of(freeId), repository.checked);
  }

  @Test
  void aTakenIdIsRetriedUntilAFreeOneComesUp() {
    var repository = new TakenIds(Set.of(takenId));
    var generated = new ArrayDeque<>(List.of(takenId, takenId, freeId));
    Assertions.assertEquals(freeId,
      repository.generateAvailableId(generated::poll).join());
    Assertions.assertEquals(List.of(takenId, takenId, freeId), repository.checked);
  }

  /**
   * A generator that blows up must not leave the caller waiting on a future
   * that never completes.
   */
  @Test
  void aGeneratorThatFailsEndsInACompletedFutureRatherThanAHang() {
    var repository = new TakenIds(Set.of());
    var result = repository.generateAvailableId(() -> {
      throw new IllegalStateException("no id source");
    });
    Assertions.assertTrue(result.isDone());
    Assertions.assertNull(result.join());
  }
}
