package de.insulink.api.web.app.pump;

import de.insulink.api.pump.contact.PumpContact;
import de.insulink.api.pump.contact.PumpContactRepository;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
import de.insulink.api.web.TestAuthentication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Pod contacts arrive as the app's whole two-day window on every push, so only
 * the times not stored yet are written and nothing is ever removed.
 */
@AppControllerTest(PumpContactController.class)
final class PumpContactControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final long FIRST_MINUTE = 1_700_000_040_000L;
  private static final long SECOND_MINUTE = 1_700_000_100_000L;

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private PumpContactRepository contactRepository;

  @BeforeEach
  void stubStoredContact() {
    Mockito.when(contactRepository.findByUserIdOrderByContactedAt(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(
        PumpContact.create(UUID.randomUUID(), USER_ID, FIRST_MINUTE))));
    Mockito.when(contactRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
  }

  @Test
  void onlyTheUnseenContactIsStored() throws Exception {
    AsyncEndpoint.on(mockMvc).call(post("/pump/contact/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"contacts\": [" + FIRST_MINUTE + ", " + SECOND_MINUTE + "]}"))
      .andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(PumpContact.class);
    Mockito.verify(contactRepository).save(saved.capture());
    Assertions.assertEquals(SECOND_MINUTE, saved.getValue().contactedAt());
    Assertions.assertEquals(USER_ID, saved.getValue().userId());
  }

  @Test
  void storedContactsAreListedAsEpochMillis() throws Exception {
    AsyncEndpoint.on(mockMvc).call(get("/pump/contact/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.contacts[0]").value(FIRST_MINUTE));
  }
}
