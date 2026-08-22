package de.insulink.api.web.app.pump;

import de.insulink.api.pump.Pump;
import de.insulink.api.pump.PumpRepository;
import de.insulink.api.pump.PumpType;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
import de.insulink.api.web.TestAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Saying a pump is gone, which is what stops the app offering it back at every
 * launch.
 *
 * The point of the endpoint is what it does NOT do: the row stays, because it is
 * the user's pump history. Only the offering stops.
 */
@AppControllerTest(PumpDiscardController.class)
final class PumpDiscardControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID PUMP_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private PumpRepository pumpRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubRepository() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(pumpRepository.save(Mockito.any()))
      .thenAnswer(invocation ->
        CompletableFuture.completedFuture(invocation.getArgument(0)));
  }

  private Pump pumpOf(UUID ownerId) {
    return Pump.create(PUMP_ID, ownerId, PumpType.OMNIPOD_DASH, "blob",
      1_700_000_000_000L, 1_700_000_288_000L, null);
  }

  private String discardBody() {
    return "{\"pump_id\":\"" + PUMP_ID + "\"}";
  }

  @Test
  void discardingStampsThePumpAndKeepsIt() throws Exception {
    var pump = pumpOf(USER_ID);
    Mockito.when(pumpRepository.findById(PUMP_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(pump)));

    endpoint.call(post("/pump/discard/")
      .contentType(MediaType.APPLICATION_JSON)
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .content(discardBody()));

    var saved = ArgumentCaptor.forClass(Pump.class);
    Mockito.verify(pumpRepository).save(saved.capture());
    assertNotNull(saved.getValue().discardedAt());
    Mockito.verify(pumpRepository, Mockito.never()).delete(Mockito.any());
  }

  /**
   * The row is the history. Deleting would answer the same question by throwing
   * away the record of a pump that was actually worn.
   */
  @Test
  void theRowIsNeverDeleted() throws Exception {
    Mockito.when(pumpRepository.findById(PUMP_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(pumpOf(USER_ID))));

    endpoint.call(post("/pump/discard/")
      .contentType(MediaType.APPLICATION_JSON)
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .content(discardBody()));

    Mockito.verify(pumpRepository, Mockito.never()).deleteById(Mockito.any());
  }

  /** A guessed id must not retire somebody else's pump. */
  @Test
  void anotherAccountsPumpCannotBeDiscarded() throws Exception {
    var pump = pumpOf(UUID.randomUUID());
    Mockito.when(pumpRepository.findById(PUMP_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(pump)));

    endpoint.call(post("/pump/discard/")
      .contentType(MediaType.APPLICATION_JSON)
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .content(discardBody()));

    assertNull(pump.discardedAt());
    Mockito.verify(pumpRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void anUnknownPumpSavesNothing() throws Exception {
    Mockito.when(pumpRepository.findById(PUMP_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    endpoint.call(post("/pump/discard/")
      .contentType(MediaType.APPLICATION_JSON)
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .content(discardBody()));

    Mockito.verify(pumpRepository, Mockito.never()).save(Mockito.any());
  }

  /** Repeating it must not rewrite when the user actually said so. */
  @Test
  void discardingTwiceKeepsTheFirstMoment() {
    var pump = pumpOf(USER_ID);

    pump.discard(1_700_000_500_000L);
    pump.discard(1_800_000_000_000L);

    org.junit.jupiter.api.Assertions
      .assertEquals(1_700_000_500_000L, pump.discardedAt());
  }
}
