package de.insulink.api.web.app.location;

import de.insulink.api.location.LocationEntry;
import de.insulink.api.location.LocationRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Location reports come in resent batches like the glucose ones and are
 * deduplicated by their timestamp, so a retried upload does not double the
 * user's trail. Coordinates are stored exactly as reported — no rounding here,
 * the app decides what precision it hands over.
 */
@AppControllerTest(LocationReportController.class)
final class LocationReportControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final long RECORDED_AT = 1_700_000_000_000L;

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private LocationRepository locationRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubEmptyTrail() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(locationRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(locationRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
  }

  private void report(String payload) throws Exception {
    endpoint.call(post("/location/report/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
  }

  private List<LocationEntry> saved() {
    var captor = ArgumentCaptor.forClass(LocationEntry.class);
    Mockito.verify(locationRepository, Mockito.atLeast(0)).save(captor.capture());
    return captor.getAllValues();
  }

  @Test
  void aReportedPositionIsStoredForTheUserExactlyAsReported() throws Exception {
    report("""
      {"entries": [{"lat": 50.7753, "lng": 6.0839, "time": 1700000000000}]}""");
    var entry = saved().getFirst();
    Assertions.assertEquals(USER_ID, entry.userId());
    Assertions.assertEquals(50.7753, entry.latitude());
    Assertions.assertEquals(6.0839, entry.longitude());
    Assertions.assertEquals(RECORDED_AT, entry.recordedAt());
  }

  @Test
  void everyPositionOfABatchIsStored() throws Exception {
    report("""
      {"entries": [
        {"lat": 50.77, "lng": 6.08, "time": 1700000000000},
        {"lat": 50.78, "lng": 6.09, "time": 1700000060000}
      ]}""");
    Assertions.assertEquals(2, saved().size());
  }

  @Test
  void aPositionAlreadyOnTheServerIsNotStoredTwice() throws Exception {
    Mockito.when(locationRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(LocationEntry.create(
        UUID.randomUUID(), USER_ID, 50.77, 6.08, RECORDED_AT))));
    report("""
      {"entries": [
        {"lat": 50.77, "lng": 6.08, "time": 1700000000000},
        {"lat": 50.78, "lng": 6.09, "time": 1700000060000}
      ]}""");
    Assertions.assertEquals(1, saved().size());
    Assertions.assertEquals(1_700_000_060_000L, saved().getFirst().recordedAt());
  }

  @Test
  void aTimestampRepeatedInsideOneBatchIsStoredOnce() throws Exception {
    report("""
      {"entries": [
        {"lat": 50.77, "lng": 6.08, "time": 1700000000000},
        {"lat": 50.78, "lng": 6.09, "time": 1700000000000}
      ]}""");
    Assertions.assertEquals(1, saved().size());
  }

  @Test
  void anEmptyBatchIsAcceptedAndStoresNothing() throws Exception {
    report("{\"entries\": []}");
    Mockito.verify(locationRepository, Mockito.never()).save(Mockito.any());
  }
}
