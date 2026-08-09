package de.insulink.api.web.app.health;

import de.insulink.api.health.Hba1cReading;
import de.insulink.api.health.Hba1cReadingRepository;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HbA1c endpoints as the app sees them: readings come back as percent+time,
 * a push merges rather than replaces, and a delete names the readings to drop.
 */
@AppControllerTest(Hba1cController.class)
final class Hba1cControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final long SPRING = 1_700_000_000_000L;
  private static final long AUTUMN = 1_710_000_000_000L;

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private Hba1cReadingRepository readingRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubEmptyHistory() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(readingRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(readingRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(readingRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
  }

  private Hba1cReading reading(double percent, long recordedAt) {
    return Hba1cReading.create(UUID.randomUUID(), USER_ID, percent, recordedAt);
  }

  private ResultActions callReadings(String path, String payload)
    throws Exception {
    return endpoint.call(post(path)
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .contentType(MediaType.APPLICATION_JSON)
      .content(payload));
  }

  @Test
  void findRendersAReadingAsPercentAndTime() throws Exception {
    Mockito.when(readingRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(reading(6.8, SPRING))));
    endpoint.call(get("/health/hba1c/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.readings[0].percent").value(6.8))
      .andExpect(jsonPath("$.readings[0].time").value(SPRING));
  }

  @Test
  void syncStoresTheReadingsForTheUser() throws Exception {
    callReadings("/health/hba1c/sync/", """
      {"readings": [
        {"percent": 6.8, "time": 1700000000000},
        {"percent": 7.4, "time": 1710000000000}
      ]}""").andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(Hba1cReading.class);
    Mockito.verify(readingRepository, Mockito.times(2)).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getAllValues().getFirst().userId());
    Assertions.assertEquals(6.8, saved.getAllValues().getFirst().percent());
    Assertions.assertEquals(AUTUMN, saved.getAllValues().getLast().recordedAt());
  }

  /**
   * The reason readings merge instead of replacing: a device that only holds part
   * of the history must not wipe the rest by pushing what it has.
   */
  @Test
  void syncingReadingsNeverDeletes() throws Exception {
    Mockito.when(readingRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(reading(6.8, SPRING))));
    callReadings("/health/hba1c/sync/", "{\"readings\": []}")
      .andExpect(status().isOk());
    Mockito.verify(readingRepository, Mockito.never()).delete(Mockito.any());
  }

  @Test
  void deleteRemovesOnlyTheNamedReading() throws Exception {
    var doomed = reading(6.8, SPRING);
    var kept = reading(7.4, AUTUMN);
    Mockito.when(readingRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(doomed, kept)));
    callReadings("/health/hba1c/delete/", """
      {"readings": [{"time": 1700000000000}]}""")
      .andExpect(jsonPath("$.success").value(true));
    Mockito.verify(readingRepository).delete(doomed);
    Mockito.verify(readingRepository, Mockito.never()).delete(kept);
  }
}
