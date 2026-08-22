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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The pump-update endpoint, which is what keeps a stored pod usable: a running
 * pod's counters advance, so the app rewrites the blob rather than registering
 * the pod twice. The two failure paths matter more than the happy one — an
 * unknown id must not create anything, and another account's pump must not be
 * writable through a guessed id.
 */
@AppControllerTest(PumpUpdateController.class)
final class PumpControllerTest {
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

  private Pump pumpOf(UUID ownerId, String data) {
    return Pump.create(PUMP_ID, ownerId, PumpType.OMNIPOD_DASH, data,
      1_700_000_000_000L, 1_700_000_288_000L, null);
  }

  private String updateBody(String data) {
    return "{\"pump_id\":\"" + PUMP_ID + "\",\"data\":\"" + data + "\"}";
  }

  @Test
  void updatingRewritesTheStoredBlob() throws Exception {
    var pump = pumpOf(USER_ID, "{\"unique_id\":4242}");
    Mockito.when(pumpRepository.findById(PUMP_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(pump)));

    endpoint.call(post("/pump/update/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(updateBody("refreshed")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));

    var saved = ArgumentCaptor.forClass(Pump.class);
    Mockito.verify(pumpRepository).save(saved.capture());
    assertEquals("refreshed", saved.getValue().data());
  }

  @Test
  void anUnknownPumpIsRejectedWithoutSavingAnything() throws Exception {
    Mockito.when(pumpRepository.findById(PUMP_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    endpoint.call(post("/pump/update/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(updateBody("refreshed")))
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(1000));

    Mockito.verify(pumpRepository, Mockito.never()).save(Mockito.any());
  }

  /**
   * A pump id guessed from another account must not be writable — the blob
   * authorises talking to somebody's insulin pump.
   */
  @Test
  void anotherAccountsPumpCannotBeOverwritten() throws Exception {
    var foreignPump = pumpOf(UUID.randomUUID(), "{\"unique_id\":1}");
    Mockito.when(pumpRepository.findById(PUMP_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(foreignPump)));

    endpoint.call(post("/pump/update/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(updateBody("attacker")))
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(1001));

    Mockito.verify(pumpRepository, Mockito.never()).save(Mockito.any());
    assertEquals("{\"unique_id\":1}", foreignPump.data());
  }
}
