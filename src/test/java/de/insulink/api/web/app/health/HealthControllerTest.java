package de.insulink.api.web.app.health;

import de.insulink.api.health.HealthDay;
import de.insulink.api.health.HealthDayRepository;
import de.insulink.api.health.LivePulse;
import de.insulink.api.health.LivePulseCache;
import de.insulink.api.health.PulseSample;
import de.insulink.api.health.PulseSampleRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The three health surfaces and the different promises they make. The daily
 * archive is replace-all and stores the app's blob verbatim; the intraday pulse
 * curve merges instead, so an incremental push of the live tail cannot erase the
 * rest of the day; the live relay writes nothing at all and only answers with a
 * reading while one is fresh.
 */
@AppControllerTest({HealthDayController.class, PulseController.class,
  LivePulseController.class})
final class HealthControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final long RECORDED_AT = 1_700_000_000_000L;

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private HealthDayRepository dayRepository;
  @MockitoBean
  private PulseSampleRepository sampleRepository;
  @MockitoBean
  private LivePulseCache liveCache;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubEmptyArchive() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(dayRepository.findByUserIdOrderByDateKey(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(sampleRepository.findByUserIdOrderByRecordedAt(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(dayRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(dayRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(sampleRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
  }

  @Test
  void aStoredDayIsHandedBackAsTheObjectTheAppPushed() throws Exception {
    Mockito.when(dayRepository.findByUserIdOrderByDateKey(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(HealthDay.create(
        UUID.randomUUID(), USER_ID, "2026-07-22",
        "{\"d\":\"2026-07-22\",\"resting_bpm\":54,\"sleep_min\":431}"))));
    endpoint.call(get("/health/days/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.days[0].d").value("2026-07-22"))
      .andExpect(jsonPath("$.days[0].resting_bpm").value(54))
      .andExpect(jsonPath("$.days[0].sleep_min").value(431));
  }

  /**
   * The api never reads into a day, so whatever metrics a newer app version adds
   * survive a round trip without a change here.
   */
  @Test
  void aDayIsStoredUnderItsDateKeyWithTheWholeBlobKept() throws Exception {
    endpoint.call(post("/health/days/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"days": [{"d": "2026-07-22", "resting_bpm": 54,
                     "something_new": "kept"}]}"""))
      .andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(HealthDay.class);
    Mockito.verify(dayRepository).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getValue().userId());
    Assertions.assertEquals("2026-07-22", saved.getValue().dateKey());
    Assertions.assertTrue(saved.getValue().data().contains("something_new"),
      saved.getValue().data());
  }

  @Test
  void syncingDaysReplacesTheArchiveTheUserHadBefore() throws Exception {
    var stale = HealthDay.create(UUID.randomUUID(), USER_ID, "2026-07-21", "{}");
    Mockito.when(dayRepository.findByUserIdOrderByDateKey(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(stale)));
    endpoint.call(post("/health/days/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"days\": []}"))
      .andExpect(status().isOk());
    Mockito.verify(dayRepository).delete(stale);
  }

  @Test
  void theIntradayCurveIsReturnedInTheShortWireForm() throws Exception {
    Mockito.when(sampleRepository.findByUserIdOrderByRecordedAt(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(
        PulseSample.create(UUID.randomUUID(), USER_ID, RECORDED_AT, 72))));
    endpoint.call(get("/health/pulse/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.samples[0].t").value(RECORDED_AT))
      .andExpect(jsonPath("$.samples[0].b").value(72));
  }

  @Test
  void pushedSamplesAreStoredForTheUser() throws Exception {
    endpoint.call(post("/health/pulse/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"samples": [{"t": 1700000000000, "b": 72},
                       {"t": 1700000001000, "b": 73}]}"""))
      .andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(PulseSample.class);
    Mockito.verify(sampleRepository, Mockito.times(2)).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getAllValues().getFirst().userId());
    Assertions.assertEquals(RECORDED_AT, saved.getAllValues().getFirst().recordedAt());
    Assertions.assertEquals(73, saved.getAllValues().getLast().bpm());
  }

  /**
   * Unlike every other collection, pushing pulse must not delete: the app sends
   * only the newest tail.
   */
  @Test
  void pushingTheLiveTailDoesNotDeleteTheRestOfTheDay() throws Exception {
    Mockito.when(sampleRepository.findByUserIdOrderByRecordedAt(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(
        PulseSample.create(UUID.randomUUID(), USER_ID, RECORDED_AT, 72))));
    endpoint.call(post("/health/pulse/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"samples\": [{\"t\": 1700000600000, \"b\": 80}]}"))
      .andExpect(status().isOk());
    Mockito.verify(sampleRepository, Mockito.never()).delete(Mockito.any());
  }

  @Test
  void aPushedLiveReadingGoesToTheCacheAndNotToTheDatabase() throws Exception {
    Mockito.when(liveCache.isWatched(USER_ID)).thenReturn(true);
    endpoint.call(post("/health/pulse/live/push/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"b\": 88}"))
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.live").value(true));
    Mockito.verify(liveCache).record(USER_ID, 88);
    Mockito.verifyNoInteractions(sampleRepository);
  }

  @Test
  void aLivePushWithoutAReadingIsRefused() throws Exception {
    endpoint.call(post("/health/pulse/live/push/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(jsonPath("$.error.code").value(1000));
    Mockito.verify(liveCache, Mockito.never())
      .record(Mockito.any(), Mockito.anyInt());
  }

  @Test
  void theLiveReadingIsHandedToWhoeverIsWatching() throws Exception {
    Mockito.when(liveCache.find(USER_ID)).thenReturn(
      Optional.of(LivePulse.create(88, RECORDED_AT)));
    endpoint.call(get("/health/pulse/live/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.b").value(88));
  }

  /**
   * A response without a reading is how the panel learns the band went quiet —
   * it needs no clock of its own for that.
   */
  @Test
  void aBandThatWentQuietAnswersWithoutAReading() throws Exception {
    Mockito.when(liveCache.find(USER_ID)).thenReturn(Optional.empty());
    endpoint.call(get("/health/pulse/live/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.b").doesNotExist());
  }
}
