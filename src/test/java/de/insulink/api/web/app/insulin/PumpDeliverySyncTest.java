package de.insulink.api.web.app.insulin;

import de.insulink.api.insulin.delivery.PumpDelivery;
import de.insulink.api.insulin.delivery.PumpDeliveryRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The app pushes its whole per-pod log every time it changes, so the merge has
 * to be idempotent on the delivery time: an unchanged entry writes nothing, a
 * bolus stopped part-way updates the stored row, and an entry the app no longer
 * holds (wiped with the pod) is never deleted.
 */
final class PumpDeliverySyncTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final long DELIVERED_AT = 1_700_000_000_000L;

  private PumpDeliveryRepository deliveryRepository;
  private PumpDeliverySync sync;

  @BeforeEach
  void createSync() {
    deliveryRepository = Mockito.mock(PumpDeliveryRepository.class);
    Mockito.when(deliveryRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    sync = PumpDeliverySync.create(deliveryRepository);
  }

  private PumpDelivery bolus(UUID id, double units, boolean confirmed) {
    return PumpDelivery.create(id, USER_ID, DELIVERED_AT, "bolus", "app", units, confirmed);
  }

  private List<PumpDelivery> saved() {
    var captor = ArgumentCaptor.forClass(PumpDelivery.class);
    Mockito.verify(deliveryRepository, Mockito.atLeast(0)).save(captor.capture());
    return captor.getAllValues();
  }

  @Test
  void anUnseenDeliveryIsStoredAsItArrived() {
    var fresh = bolus(UUID.randomUUID(), 4.0, true);
    sync.merge(List.of(), List.of(fresh)).join();
    Assertions.assertEquals(List.of(fresh), saved());
  }

  @Test
  void reSendingTheSameDeliveryWritesNothing() {
    sync.merge(List.of(bolus(UUID.randomUUID(), 4.0, true)),
      List.of(bolus(UUID.randomUUID(), 4.0, true))).join();
    Assertions.assertTrue(saved().isEmpty());
  }

  @Test
  void aBolusStoppedPartWayUpdatesTheStoredRow() {
    var storedId = UUID.randomUUID();
    sync.merge(List.of(bolus(storedId, 4.0, true)),
      List.of(bolus(UUID.randomUUID(), 1.5, true))).join();
    Assertions.assertEquals(storedId, saved().getFirst().id());
    Assertions.assertEquals(1.5, saved().getFirst().units());
  }

  @Test
  void aDeliveryMissingFromThePushIsKept() {
    sync.merge(List.of(bolus(UUID.randomUUID(), 4.0, true)), List.of()).join();
    Mockito.verify(deliveryRepository, Mockito.never()).delete(Mockito.any());
    Mockito.verify(deliveryRepository, Mockito.never()).deleteById(Mockito.any());
  }
}
