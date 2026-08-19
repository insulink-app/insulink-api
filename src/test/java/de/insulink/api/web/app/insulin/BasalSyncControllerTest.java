package de.insulink.api.web.app.insulin;

import de.insulink.api.insulin.basal.BasalEntry;
import de.insulink.api.insulin.basal.BasalRepository;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Basal deliveries the pump reports, which the forecasting sidecar reads as part
 * of insulin on board. Append-only: each row measures a window that has already
 * passed and can never be revised, so nothing is replaced.
 */
@AppControllerTest(BasalSyncController.class)
final class BasalSyncControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private BasalRepository basalRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubRepository() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(basalRepository.generateAvailableId(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
    Mockito.when(basalRepository.save(Mockito.any()))
      .thenAnswer(invocation ->
        CompletableFuture.completedFuture(invocation.getArgument(0)));
  }

  private String body(String deliveries) {
    return "{\"deliveries\":" + deliveries + "}";
  }

  @Test
  void storesEveryDeliveredWindow() throws Exception {
    endpoint.call(post("/insulin/basal/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body("[{\"at\":1700000000000,\"units\":0.25},"
          + "{\"at\":1700000900000,\"units\":0.24}]")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.stored").value(2));

    var saved = ArgumentCaptor.forClass(BasalEntry.class);
    Mockito.verify(basalRepository, Mockito.times(2)).save(saved.capture());
    assertEquals(USER_ID, saved.getAllValues().getFirst().userId());
    assertEquals(0.25f, saved.getAllValues().getFirst().insulin());
    assertEquals(1700000000000L, saved.getAllValues().getFirst().recordedAt());
  }

  /**
   * A window that delivered nothing — a suspended or alarming pod — is a real
   * observation for the app's own accounting, but there is no insulin to record.
   */
  @Test
  void aWindowThatDeliveredNothingIsNotStored() throws Exception {
    endpoint.call(post("/insulin/basal/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body("[{\"at\":1700000000000,\"units\":0}]")))
      .andExpect(jsonPath("$.stored").value(0));

    Mockito.verify(basalRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void anEmptyBatchIsAcceptedWithoutTouchingTheDatabase() throws Exception {
    endpoint.call(post("/insulin/basal/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body("[]")))
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.stored").value(0));

    Mockito.verify(basalRepository, Mockito.never()).save(Mockito.any());
  }

  /**
   * Every row is written against the caller's own id, so a body cannot smuggle
   * insulin history into somebody else's account.
   */
  @Test
  void deliveriesAreStoredAgainstTheCaller() throws Exception {
    endpoint.call(post("/insulin/basal/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body("[{\"at\":1,\"units\":1.5,\"user_id\":\""
          + UUID.randomUUID() + "\"}]")))
      .andExpect(jsonPath("$.stored").value(1));

    var saved = ArgumentCaptor.forClass(BasalEntry.class);
    Mockito.verify(basalRepository).save(saved.capture());
    assertEquals(USER_ID, saved.getValue().userId());
  }
}
