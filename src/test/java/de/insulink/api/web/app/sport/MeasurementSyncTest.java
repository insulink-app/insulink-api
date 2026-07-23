package de.insulink.api.web.app.sport;

import de.insulink.api.sport.SportMeasurement;
import de.insulink.api.sport.SportMeasurementRepository;
import de.insulink.api.sport.SportMeasurementType;
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
 * The rule that keeps two sources of activity data from destroying each other:
 * a push may insert and correct, but never delete on its own. Only an explicit
 * remove drops rows, and it drops exactly the keys it names. Identity is
 * type + timestamp, so the same metric at a new instant is a new row while the
 * same metric at the same instant is an update carrying the stored id forward.
 */
final class MeasurementSyncTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final long MORNING = 1_700_000_000_000L;
  private static final long EVENING = 1_700_040_000_000L;

  private SportMeasurementRepository measurementRepository;
  private MeasurementSync sync;

  @BeforeEach
  void createSync() {
    measurementRepository = Mockito.mock(SportMeasurementRepository.class);
    Mockito.when(measurementRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(measurementRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    sync = MeasurementSync.create(measurementRepository);
  }

  private SportMeasurement measurement(
    UUID id, SportMeasurementType type, double value, long recordedAt
  ) {
    return SportMeasurement.create(id, USER_ID, type, value, recordedAt);
  }

  private SportMeasurement steps(UUID id, double value, long recordedAt) {
    return measurement(id, SportMeasurementType.STEPS, value, recordedAt);
  }

  private List<SportMeasurement> saved() {
    var captor = ArgumentCaptor.forClass(SportMeasurement.class);
    Mockito.verify(measurementRepository, Mockito.atLeast(0)).save(captor.capture());
    return captor.getAllValues();
  }

  @Test
  void anUnseenMeasurementIsInsertedAsItArrived() {
    var fresh = steps(UUID.randomUUID(), 8000, MORNING);
    sync.merge(List.of(), List.of(fresh)).join();
    Assertions.assertEquals(List.of(fresh), saved());
  }

  @Test
  void aMeasurementThatDidNotChangeIsNotWrittenAgain() {
    var stored = steps(UUID.randomUUID(), 8000, MORNING);
    sync.merge(List.of(stored), List.of(steps(UUID.randomUUID(), 8000, MORNING)))
      .join();
    Mockito.verify(measurementRepository, Mockito.never()).save(Mockito.any());
  }

  /**
   * A corrected value has to land on the row that is already there, otherwise
   * the unique constraint on type + timestamp rejects the write.
   */
  @Test
  void aChangedValueUpdatesTheStoredRowRatherThanAddingASecondOne() {
    var storedId = UUID.randomUUID();
    sync.merge(List.of(steps(storedId, 8000, MORNING)),
      List.of(steps(UUID.randomUUID(), 8600, MORNING))).join();
    var written = saved();
    Assertions.assertEquals(1, written.size());
    Assertions.assertEquals(storedId, written.getFirst().id());
    Assertions.assertEquals(8600, written.getFirst().value());
    Assertions.assertEquals(MORNING, written.getFirst().recordedAt());
  }

  @Test
  void theSameMetricAtAnotherInstantIsItsOwnMeasurement() {
    sync.merge(List.of(steps(UUID.randomUUID(), 8000, MORNING)),
      List.of(steps(UUID.randomUUID(), 3000, EVENING))).join();
    Assertions.assertEquals(1, saved().size());
    Assertions.assertEquals(EVENING, saved().getFirst().recordedAt());
  }

  @Test
  void twoMetricsAtTheSameInstantDoNotCollide() {
    sync.merge(
      List.of(steps(UUID.randomUUID(), 8000, MORNING)),
      List.of(measurement(UUID.randomUUID(), SportMeasurementType.CALORIES,
        450, MORNING))).join();
    Assertions.assertEquals(1, saved().size());
    Assertions.assertEquals(SportMeasurementType.CALORIES,
      saved().getFirst().type());
  }

  /**
   * The whole point of merge: a second data source that only knows part of the
   * history must not be able to erase the rest of it.
   */
  @Test
  void mergeNeverDeletesAnythingNotEvenRowsThePushDoesNotMention() {
    sync.merge(List.of(steps(UUID.randomUUID(), 8000, MORNING)), List.of()).join();
    Mockito.verify(measurementRepository, Mockito.never()).delete(Mockito.any());
  }

  @Test
  void removeDeletesExactlyTheKeysItWasGiven() {
    var doomed = steps(UUID.randomUUID(), 8000, MORNING);
    var kept = steps(UUID.randomUUID(), 3000, EVENING);
    sync.remove(List.of(doomed, kept), Set.of(MeasurementSync.MeasurementKey
      .of(SportMeasurementType.STEPS, MORNING))).join();
    Mockito.verify(measurementRepository).delete(doomed);
    Mockito.verify(measurementRepository, Mockito.never()).delete(kept);
  }

  @Test
  void removeIgnoresAKeyTheUserDoesNotHave() {
    var kept = steps(UUID.randomUUID(), 3000, EVENING);
    sync.remove(List.of(kept), Set.of(MeasurementSync.MeasurementKey
      .of(SportMeasurementType.WEIGHT, MORNING))).join();
    Mockito.verify(measurementRepository, Mockito.never()).delete(Mockito.any());
  }

  @Test
  void bothHalvesAnswerWithASuccessTheAppCanRead() {
    Assertions.assertEquals(true,
      sync.merge(List.of(), List.of()).join().getBody().get("success"));
    Assertions.assertEquals(true,
      sync.remove(List.of(), Set.of()).join().getBody().get("success"));
  }
}
