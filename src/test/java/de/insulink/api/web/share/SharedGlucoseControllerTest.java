package de.insulink.api.web.share;

import de.insulink.api.glucose.GlucoseEntry;
import de.insulink.api.glucose.GlucoseRepository;
import de.insulink.api.glucose.share.GlucoseShare;
import de.insulink.api.glucose.share.GlucoseShareRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * The public end of a share link: no session, the link's id is the credential,
 * and it hands out glucose of the owner only.
 */
@AppControllerTest(SharedGlucoseController.class)
final class SharedGlucoseControllerTest {
  private static final UUID OWNER_ID = UUID.randomUUID();
  private static final UUID SHARE_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private GlucoseShareRepository shareRepository;
  @MockitoBean
  private GlucoseRepository glucoseRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void startEndpoint() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(shareRepository.findById(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
  }

  @Test
  void aLinkShowsItsOwnersGlucoseWithoutASession() throws Exception {
    Mockito.when(shareRepository.findById(SHARE_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(
        GlucoseShare.create(SHARE_ID, OWNER_ID, "Mum", 1L))));
    Mockito.when(glucoseRepository
        .findByUserIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(
          Mockito.eq(OWNER_ID), Mockito.anyLong()))
      .thenReturn(CompletableFuture.completedFuture(List.of(
        GlucoseEntry.create(UUID.randomUUID(), OWNER_ID, 123f, 1_700_000_000_000L))));

    endpoint.call(get("/share/" + SHARE_ID + "/"))
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.entries[0].value").value(123.0))
      .andExpect(jsonPath("$.entries[0].time").value(1_700_000_000_000L));
  }

  @Test
  void aDeletedLinkShowsNothing() throws Exception {
    endpoint.call(get("/share/" + SHARE_ID + "/"))
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.entries").doesNotExist());
  }

  @Test
  void aMalformedLinkIsUnknownNotAnError() throws Exception {
    endpoint.call(get("/share/not-a-uuid/"))
      .andExpect(jsonPath("$.success").value(false));
  }
}
