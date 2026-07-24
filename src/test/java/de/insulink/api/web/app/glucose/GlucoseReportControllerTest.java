package de.insulink.api.web.app.glucose;

import de.insulink.api.glucose.GlucoseEntry;
import de.insulink.api.glucose.GlucoseRepository;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Glucose reporting is append-only and deduplicated by reading timestamp: the
 * app resends whatever it has buffered, and only the minutes the user does not
 * already have on the server are stored. Values stay mg/dL and the time stays
 * the epoch-millisecond value the app sent — no rescaling anywhere on the way in.
 */
@AppControllerTest(GlucoseReportController.class)
final class GlucoseReportControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private GlucoseRepository glucoseRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubEmptyHistory() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(glucoseRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(glucoseRepository.existsById(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(false));
    Mockito.when(glucoseRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
  }

  private void report(String payload) throws Exception {
    endpoint.call(post("/glucose/report/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
  }

  private List<GlucoseEntry> savedEntries() {
    var saved = ArgumentCaptor.forClass(GlucoseEntry.class);
    Mockito.verify(glucoseRepository, Mockito.atLeastOnce()).save(saved.capture());
    return saved.getAllValues();
  }

  @Test
  void aReportedReadingIsStoredForTheUserWithItsValueAndTimeUnchanged()
    throws Exception {
    report("""
      {"entries": [{"glucose": 118.5, "time": 1700000040000}]}""");
    var entry = savedEntries().getFirst();
    Assertions.assertEquals(USER_ID, entry.userId());
    Assertions.assertEquals(118.5f, entry.value());
    Assertions.assertEquals(1_700_000_040_000L, entry.recordedAt());
  }

  @Test
  void everyReadingOfABatchIsStored() throws Exception {
    report("""
      {"entries": [
        {"glucose": 100, "time": 1700000000000},
        {"glucose": 105, "time": 1700000300000},
        {"glucose": 110, "time": 1700000600000}
      ]}""");
    Mockito.verify(glucoseRepository, Mockito.times(3)).save(Mockito.any());
  }

  @Test
  void aReadingForAMinuteAlreadyOnTheServerIsNotStoredTwice() throws Exception {
    Mockito.when(glucoseRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(GlucoseEntry
        .create(UUID.randomUUID(), USER_ID, 100f, 1_700_000_000_000L))));
    report("""
      {"entries": [
        {"glucose": 100, "time": 1700000000000},
        {"glucose": 105, "time": 1700000300000}
      ]}""");
    var entries = savedEntries();
    Assertions.assertEquals(1, entries.size());
    Assertions.assertEquals(1_700_000_300_000L, entries.getFirst().recordedAt());
  }

  /**
   * The app retries a failed upload with the same buffer, so a batch that
   * repeats a timestamp inside itself must still only produce one row.
   */
  @Test
  void aTimestampRepeatedInsideOneBatchIsStoredOnlyOnce() throws Exception {
    report("""
      {"entries": [
        {"glucose": 100, "time": 1700000000000},
        {"glucose": 101, "time": 1700000000000}
      ]}""");
    Mockito.verify(glucoseRepository, Mockito.times(1)).save(Mockito.any());
  }

  @Test
  void anEmptyBatchIsAcceptedAndStoresNothing() throws Exception {
    report("{\"entries\": []}");
    Mockito.verify(glucoseRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void aRequestWithoutEntriesIsRejectedRatherThanStoringAnything()
    throws Exception {
    Mockito.when(userRepository.findById(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    endpoint.call(post("/glucose/report/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isOk());
    Mockito.verify(glucoseRepository, Mockito.never()).save(Mockito.any());
  }
}
