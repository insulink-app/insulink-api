package de.insulink.api.web.app;

import de.insulink.api.event.Event;
import de.insulink.api.event.EventRepository;
import de.insulink.api.glucose.GlucoseEntry;
import de.insulink.api.glucose.GlucoseRepository;
import de.insulink.api.sensor.Sensor;
import de.insulink.api.sensor.SensorRepository;
import de.insulink.api.sensor.SensorType;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
import de.insulink.api.web.TestAuthentication;
import de.insulink.api.web.app.event.EventHistoryController;
import de.insulink.api.web.app.glucose.GlucoseHistoryController;
import de.insulink.api.web.app.sensor.SensorUpdateController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The read-back endpoints the archive views are drawn from, plus the sensor
 * update. Glucose leaves the api the way it entered — mg/dL and the epoch
 * millisecond it was recorded at, no rescaling — and the sensor update is the
 * one place where a request names a row by id, so it has to prove the row
 * belongs to the caller before writing to it.
 */
@AppControllerTest({GlucoseHistoryController.class, EventHistoryController.class,
  SensorUpdateController.class})
final class HistoryEndpointTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID SENSOR_ID = UUID.randomUUID();
  private static final long RECORDED_AT = 1_700_000_000_000L;

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private GlucoseRepository glucoseRepository;
  @MockitoBean
  private EventRepository eventRepository;
  @MockitoBean
  private SensorRepository sensorRepository;

  private AsyncEndpoint endpoint;
  private Sensor sensor;

  @BeforeEach
  void stubHistory() {
    endpoint = AsyncEndpoint.on(mockMvc);
    sensor = Sensor.create(SENSOR_ID, USER_ID, SensorType.DEXCOM_G7, "old-data",
      RECORDED_AT, RECORDED_AT + 864_000_000L, null);
    Mockito.when(glucoseRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(eventRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(sensorRepository.findById(SENSOR_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(sensor)));
    Mockito.when(sensorRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
  }

  private ResultActions updateSensor(UUID sensorId, String data)
    throws Exception {
    return endpoint.call(post("/sensor/update/")
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .contentType(MediaType.APPLICATION_JSON)
      .content("{\"sensor_id\": \"" + sensorId + "\", \"data\": \"" + data + "\"}"));
  }

  @Test
  void theGlucoseArchiveIsEmptyButSuccessfulForANewUser() throws Exception {
    endpoint.call(get("/glucose/history/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.entries").isEmpty());
  }

  @Test
  void aStoredReadingLeavesInTheUnitAndTimeItArrivedIn() throws Exception {
    Mockito.when(glucoseRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(GlucoseEntry
        .create(UUID.randomUUID(), USER_ID, 118.5f, RECORDED_AT))));
    endpoint.call(get("/glucose/history/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.entries[0].value").value(118.5))
      .andExpect(jsonPath("$.entries[0].time").value(RECORDED_AT));
  }

  @Test
  void aStoredEventIsRenderedWithItsTypeTimeAndData() throws Exception {
    Mockito.when(eventRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(Event.create(
        UUID.randomUUID(), USER_ID, "glucose_low", "54", RECORDED_AT))));
    endpoint.call(get("/event/history/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.entries[0].type").value("glucose_low"))
      .andExpect(jsonPath("$.entries[0].time").value(RECORDED_AT))
      .andExpect(jsonPath("$.entries[0].data").value("54"));
  }

  @Test
  void theSensorsPairingDataIsReplacedByAnUpdate() throws Exception {
    updateSensor(SENSOR_ID, "new-data")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
    Assertions.assertEquals("new-data", sensor.data());
    Mockito.verify(sensorRepository).save(sensor);
  }

  @Test
  void updatingASensorThatDoesNotExistIsRefused() throws Exception {
    var unknownId = UUID.randomUUID();
    Mockito.when(sensorRepository.findById(unknownId))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    updateSensor(unknownId, "new-data")
      .andExpect(jsonPath("$.error.code").value(1000));
    Mockito.verify(sensorRepository, Mockito.never()).save(Mockito.any());
  }

  /**
   * Sensor ids are the only ones a client names directly, so ownership is
   * checked here rather than assumed from the token.
   */
  @Test
  void aSensorBelongingToSomebodyElseCannotBeUpdated() throws Exception {
    var foreignSensor = Sensor.create(SENSOR_ID, UUID.randomUUID(),
      SensorType.DEXCOM_G7, "not-yours", RECORDED_AT, RECORDED_AT, null);
    Mockito.when(sensorRepository.findById(SENSOR_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(foreignSensor)));
    updateSensor(SENSOR_ID, "new-data")
      .andExpect(jsonPath("$.error.code").value(1001));
    Assertions.assertEquals("not-yours", foreignSensor.data());
    Mockito.verify(sensorRepository, Mockito.never()).save(Mockito.any());
  }
}
