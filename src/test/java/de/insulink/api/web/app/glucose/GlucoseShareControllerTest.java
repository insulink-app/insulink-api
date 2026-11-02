package de.insulink.api.web.app.glucose;

import de.insulink.api.glucose.share.GlucoseShare;
import de.insulink.api.glucose.share.GlucoseShareRepository;
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

/** Creating and revoking share links, each only by the account it belongs to. */
@AppControllerTest(GlucoseShareController.class)
final class GlucoseShareControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID SHARE_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private GlucoseShareRepository shareRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubRepository() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(shareRepository.generateAvailableId(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(SHARE_ID));
    Mockito.when(shareRepository.save(Mockito.any()))
      .thenAnswer(invocation ->
        CompletableFuture.completedFuture(invocation.getArgument(0)));
    Mockito.when(shareRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
  }

  private void storeShareOf(UUID ownerId) {
    Mockito.when(shareRepository.findById(SHARE_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(
        GlucoseShare.create(SHARE_ID, ownerId, "Mum", 1L))));
  }

  private org.springframework.test.web.servlet.ResultActions call(
    String path, String body
  ) throws Exception {
    return endpoint.call(post(path)
      .contentType(MediaType.APPLICATION_JSON)
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .content(body));
  }

  @Test
  void aCreatedLinkBelongsToTheCaller() throws Exception {
    call("/glucose/share/create/", "{\"name\":\"Mum\"}")
      .andExpect(jsonPath("$.share.id").value(SHARE_ID.toString()))
      .andExpect(jsonPath("$.share.name").value("Mum"));

    var saved = ArgumentCaptor.forClass(GlucoseShare.class);
    Mockito.verify(shareRepository).save(saved.capture());
    assertEquals(USER_ID, saved.getValue().userId());
  }

  @Test
  void deletingOwnLinkRevokesIt() throws Exception {
    storeShareOf(USER_ID);

    call("/glucose/share/delete/", "{\"share_id\":\"" + SHARE_ID + "\"}")
      .andExpect(jsonPath("$.success").value(true));

    Mockito.verify(shareRepository).delete(Mockito.any());
  }

  @Test
  void anotherAccountsLinkIsNotDeleted() throws Exception {
    storeShareOf(UUID.randomUUID());

    call("/glucose/share/delete/", "{\"share_id\":\"" + SHARE_ID + "\"}")
      .andExpect(jsonPath("$.success").value(false));

    Mockito.verify(shareRepository, Mockito.never()).delete(Mockito.any());
  }
}
