package de.insulink.api.web.app.sport;

import de.insulink.api.sport.CardioTraining;
import de.insulink.api.sport.CardioTrainingRepository;
import de.insulink.api.sport.SportMeasurement;
import de.insulink.api.sport.SportMeasurementRepository;
import de.insulink.api.sport.SportMeasurementType;
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
 * The two sport surfaces that are not replace-all in the same way. Measurements
 * merge and get their own explicit delete, because a Google Health import must
 * never wipe what the app alone knows. Cardio trainings do replace all, and
 * carry the recorded GPS track through as the array it was pushed as.
 */
@AppControllerTest({SportMeasurementController.class,
  CardioTrainingController.class})
final class SportMeasurementControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final long MORNING = 1_700_000_000_000L;

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private SportMeasurementRepository measurementRepository;
  @MockitoBean
  private CardioTrainingRepository trainingRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubEmptyHistory() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(measurementRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(trainingRepository.findByUserIdOrderByStartedAt(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(measurementRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(measurementRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(trainingRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(trainingRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
  }

  private SportMeasurement steps(double value, long recordedAt) {
    return SportMeasurement.create(UUID.randomUUID(), USER_ID,
      SportMeasurementType.STEPS, value, recordedAt);
  }

  private ResultActions callMeasurements(String path, String payload)
    throws Exception {
    return endpoint.call(post(path)
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .contentType(MediaType.APPLICATION_JSON)
      .content(payload));
  }

  @Test
  void findRendersAMeasurementWithItsTypeAsTheEnumName() throws Exception {
    Mockito.when(measurementRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(steps(8000, MORNING))));
    endpoint.call(get("/sport/measurements/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.entries[0].type").value("STEPS"))
      .andExpect(jsonPath("$.entries[0].value").value(8000.0))
      .andExpect(jsonPath("$.entries[0].time").value(MORNING));
  }

  @Test
  void syncStoresTheMeasurementsForTheUser() throws Exception {
    callMeasurements("/sport/measurements/sync/", """
      {"entries": [
        {"type": "STEPS", "value": 8000, "time": 1700000000000},
        {"type": "WEIGHT", "value": 74.2, "time": 1700000000000}
      ]}""").andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(SportMeasurement.class);
    Mockito.verify(measurementRepository, Mockito.times(2)).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getAllValues().getFirst().userId());
    Assertions.assertEquals(SportMeasurementType.STEPS,
      saved.getAllValues().getFirst().type());
    Assertions.assertEquals(74.2, saved.getAllValues().getLast().value());
  }

  /**
   * The whole reason measurements merge instead of replacing: a partial push
   * must leave the rest of the history alone.
   */
  @Test
  void syncingMeasurementsNeverDeletes() throws Exception {
    Mockito.when(measurementRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(steps(8000, MORNING))));
    callMeasurements("/sport/measurements/sync/", "{\"entries\": []}")
      .andExpect(status().isOk());
    Mockito.verify(measurementRepository, Mockito.never()).delete(Mockito.any());
  }

  @Test
  void deleteRemovesOnlyTheNamedMeasurement() throws Exception {
    var doomed = steps(8000, MORNING);
    var kept = steps(3000, 1_700_040_000_000L);
    Mockito.when(measurementRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(doomed, kept)));
    callMeasurements("/sport/measurements/delete/", """
      {"entries": [{"type": "STEPS", "time": 1700000000000}]}""")
      .andExpect(jsonPath("$.success").value(true));
    Mockito.verify(measurementRepository).delete(doomed);
    Mockito.verify(measurementRepository, Mockito.never()).delete(kept);
  }

  @Test
  void deletingSomethingTheUserDoesNotHaveIsStillASuccess() throws Exception {
    callMeasurements("/sport/measurements/delete/", """
      {"entries": [{"type": "WEIGHT", "time": 1700000000000}]}""")
      .andExpect(jsonPath("$.success").value(true));
    Mockito.verify(measurementRepository, Mockito.never()).delete(Mockito.any());
  }

  @Test
  void findRendersATrainingWithItsRecordedTrack() throws Exception {
    Mockito.when(trainingRepository.findByUserIdOrderByStartedAt(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(
        CardioTraining.create(UUID.randomUUID(), USER_ID, "app-run-1", "running",
          MORNING, MORNING + 1_800_000L, 5200.0, true,
          "[{\"lat\":50.77,\"lng\":6.08}]"))));
    endpoint.call(get("/sport/trainings/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.trainings[0].id").value("app-run-1"))
      .andExpect(jsonPath("$.trainings[0].type").value("running"))
      .andExpect(jsonPath("$.trainings[0].dist").value(5200.0))
      .andExpect(jsonPath("$.trainings[0].auto").value(true))
      .andExpect(jsonPath("$.trainings[0].track[0].lat").value(50.77));
  }

  @Test
  void syncStoresATrainingWithItsTrackAndReplacesTheOldOnes() throws Exception {
    var stale = CardioTraining.create(UUID.randomUUID(), USER_ID, "gone", "running",
      0L, 0L, 0.0, false, "[]");
    Mockito.when(trainingRepository.findByUserIdOrderByStartedAt(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(stale)));
    endpoint.call(post("/sport/trainings/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"trainings": [
            {"id": "app-run-1", "type": "running", "start": 1700000000000,
             "end": 1700001800000, "dist": 5200.0, "auto": true,
             "track": [{"lat": 50.77, "lng": 6.08}]}
          ]}"""))
      .andExpect(jsonPath("$.success").value(true));
    Mockito.verify(trainingRepository).delete(stale);
    var saved = ArgumentCaptor.forClass(CardioTraining.class);
    Mockito.verify(trainingRepository).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getValue().userId());
    Assertions.assertEquals("app-run-1", saved.getValue().clientId());
    Assertions.assertTrue(saved.getValue().detected());
    Assertions.assertTrue(saved.getValue().track().contains("50.77"),
      saved.getValue().track());
  }
}
