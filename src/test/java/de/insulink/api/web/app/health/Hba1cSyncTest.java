package de.insulink.api.web.app.health;

import de.insulink.api.health.Hba1cReading;
import de.insulink.api.health.Hba1cReadingRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The same rule the sport measurements follow, on the HbA1c table: a push may
 * insert and correct but never delete, only an explicit remove drops rows, and it
 * drops exactly the timestamps it names. Identity here is the timestamp alone —
 * the table holds one kind of value — so a corrected lab result at the same
 * instant is an update that carries the stored id forward, not a second row.
 */
final class Hba1cSyncTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final long SPRING = 1_700_000_000_000L;
  private static final long AUTUMN = 1_710_000_000_000L;

  private Hba1cReadingRepository readingRepository;
  private Hba1cSync sync;

  @BeforeEach
  void createSync() {
    readingRepository = Mockito.mock(Hba1cReadingRepository.class);
    Mockito.when(readingRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(readingRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    sync = Hba1cSync.create(readingRepository);
  }

  private Hba1cReading reading(UUID id, double percent, long recordedAt) {
    return Hba1cReading.create(id, USER_ID, percent, recordedAt);
  }

  private List<Hba1cReading> saved() {
    var captor = ArgumentCaptor.forClass(Hba1cReading.class);
    Mockito.verify(readingRepository, Mockito.atLeast(0)).save(captor.capture());
    return captor.getAllValues();
  }

  private List<Hba1cReading> deleted() {
    var captor = ArgumentCaptor.forClass(Hba1cReading.class);
    Mockito.verify(readingRepository, Mockito.atLeast(0)).delete(captor.capture());
    return captor.getAllValues();
  }

  @Test
  void anUnseenReadingIsInsertedAsItArrived() {
    var fresh = reading(UUID.randomUUID(), 6.8, SPRING);
    sync.merge(List.of(), List.of(fresh)).join();
    Assertions.assertEquals(List.of(fresh), saved());
  }

  @Test
  void aReadingThatDidNotChangeIsNotWrittenAgain() {
    var stored = reading(UUID.randomUUID(), 6.8, SPRING);
    sync.merge(List.of(stored), List.of(reading(UUID.randomUUID(), 6.8, SPRING)))
      .join();
    Assertions.assertEquals(List.of(), saved());
  }

  @Test
  void aCorrectedResultUpdatesInPlaceUnderTheStoredId() {
    var stored = reading(UUID.randomUUID(), 6.8, SPRING);
    sync.merge(List.of(stored), List.of(reading(UUID.randomUUID(), 7.1, SPRING)))
      .join();
    var written = saved();
    Assertions.assertEquals(1, written.size());
    Assertions.assertEquals(stored.id(), written.getFirst().id());
    Assertions.assertEquals(7.1, written.getFirst().percent());
  }

  @Test
  void aPushThatOmitsAStoredReadingLeavesItAlone() {
    var stored = reading(UUID.randomUUID(), 6.8, SPRING);
    sync.merge(List.of(stored), List.of(reading(UUID.randomUUID(), 7.4, AUTUMN)))
      .join();
    Assertions.assertEquals(List.of(), deleted());
  }

  @Test
  void removeDropsExactlyTheNamedTimestamps() {
    var spring = reading(UUID.randomUUID(), 6.8, SPRING);
    var autumn = reading(UUID.randomUUID(), 7.4, AUTUMN);
    sync.remove(List.of(spring, autumn), Set.of(SPRING)).join();
    Assertions.assertEquals(List.of(spring), deleted());
  }
}
