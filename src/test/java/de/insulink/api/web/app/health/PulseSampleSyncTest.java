package de.insulink.api.web.app.health;

import de.insulink.api.health.PulseSample;
import de.insulink.api.health.PulseSampleRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Pulse arrives as overlapping windows from a live push, so merging has to be
 * idempotent on the timestamp: pushing the same second twice writes once, a
 * corrected bpm updates the stored row instead of adding a second one, and a
 * push never deletes what it happens not to contain.
 */
final class PulseSampleSyncTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final long FIRST_SECOND = 1_700_000_000_000L;
  private static final long SECOND_SECOND = 1_700_000_001_000L;

  private PulseSampleRepository sampleRepository;
  private PulseSampleSync sync;

  @BeforeEach
  void createSync() {
    sampleRepository = Mockito.mock(PulseSampleRepository.class);
    Mockito.when(sampleRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    sync = PulseSampleSync.create(sampleRepository);
  }

  private PulseSample sample(UUID id, long recordedAt, int bpm) {
    return PulseSample.create(id, USER_ID, recordedAt, bpm);
  }

  private List<PulseSample> saved() {
    var captor = ArgumentCaptor.forClass(PulseSample.class);
    Mockito.verify(sampleRepository, Mockito.atLeast(0)).save(captor.capture());
    return captor.getAllValues();
  }

  @Test
  void anUnseenSampleIsStoredAsItArrived() {
    var fresh = sample(UUID.randomUUID(), FIRST_SECOND, 72);
    sync.merge(List.of(), List.of(fresh)).join();
    Assertions.assertEquals(List.of(fresh), saved());
  }

  @Test
  void reSendingTheSameSampleWritesNothing() {
    sync.merge(List.of(sample(UUID.randomUUID(), FIRST_SECOND, 72)),
      List.of(sample(UUID.randomUUID(), FIRST_SECOND, 72))).join();
    Mockito.verify(sampleRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void aCorrectedBpmUpdatesTheStoredRowUnderItsOwnId() {
    var storedId = UUID.randomUUID();
    sync.merge(List.of(sample(storedId, FIRST_SECOND, 72)),
      List.of(sample(UUID.randomUUID(), FIRST_SECOND, 75))).join();
    Assertions.assertEquals(1, saved().size());
    Assertions.assertEquals(storedId, saved().getFirst().id());
    Assertions.assertEquals(75, saved().getFirst().bpm());
  }

  @Test
  void anOverlappingWindowOnlyWritesTheSecondsThatAreNew() {
    sync.merge(List.of(sample(UUID.randomUUID(), FIRST_SECOND, 72)),
      List.of(sample(UUID.randomUUID(), FIRST_SECOND, 72),
        sample(UUID.randomUUID(), SECOND_SECOND, 73))).join();
    Assertions.assertEquals(1, saved().size());
    Assertions.assertEquals(SECOND_SECOND, saved().getFirst().recordedAt());
  }

  @Test
  void mergeNeverDeletesHistoryThePushDoesNotCover() {
    sync.merge(List.of(sample(UUID.randomUUID(), FIRST_SECOND, 72)), List.of())
      .join();
    Mockito.verify(sampleRepository, Mockito.never()).delete(Mockito.any());
  }

  /**
   * The wire form is deliberately short — a live chart pulls thousands of these.
   */
  @Test
  void aSampleIsRenderedAsTheShortTimeAndBpmPair() {
    var information = sync.information(sample(UUID.randomUUID(), FIRST_SECOND, 72));
    Assertions.assertEquals(FIRST_SECOND, information.get("t"));
    Assertions.assertEquals(72, information.get("b"));
  }
}
