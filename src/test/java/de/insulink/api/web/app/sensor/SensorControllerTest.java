package de.insulink.api.web.app.sensor;

import de.insulink.api.sensor.Sensor;
import de.insulink.api.sensor.SensorRepository;
import de.insulink.api.sensor.SensorType;
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
 * Registering a sensor and reading it back. The pairing blob the app worked out
 * over BLE is stored verbatim — the api never interprets it — and the "current"
 * sensor is simply the most recently registered one, so a user who has never
 * paired anything gets an error code rather than an empty success the app would
 * have to special-case.
 */
@AppControllerTest({SensorRegistrationController.class,
  SensorInformationController.class})
final class SensorControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String PAIRING_DATA = "a1b2c3d4";

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private SensorRepository sensorRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubNoSensors() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(sensorRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(sensorRepository
      .findFirstByUserIdAndDiscardedAtIsNullOrderByRegisteredAtDesc(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    Mockito.when(sensorRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(sensorRepository.generateAvailableId(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
  }

  private Sensor sensor(SensorType type, long registeredAt, long expiresAt) {
    return Sensor.create(UUID.randomUUID(), USER_ID, type, PAIRING_DATA,
      registeredAt, expiresAt, null);
  }

  @Test
  void aRegisteredSensorIsStoredWithItsPairingDataUntouched() throws Exception {
    endpoint.call(post("/sensor/register/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"type": "DEXCOM_G7", "data": "a1b2c3d4", "expires_at": 1700864000000}"""))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.sensor_id").isNotEmpty());
    var saved = ArgumentCaptor.forClass(Sensor.class);
    Mockito.verify(sensorRepository).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getValue().userId());
    Assertions.assertEquals(SensorType.DEXCOM_G7, saved.getValue().type());
    Assertions.assertEquals(PAIRING_DATA, saved.getValue().data());
    Assertions.assertEquals(1_700_864_000_000L, saved.getValue().expiresAt());
    Assertions.assertTrue(saved.getValue().registeredAt() > 0L);
  }

  @Test
  void bothSupportedSensorTypesCanBeRegistered() throws Exception {
    endpoint.call(post("/sensor/register/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"type": "ABBOTT_LIBRE3", "data": "a1b2c3d4", "expires_at": 0}"""))
      .andExpect(status().isOk());
    var saved = ArgumentCaptor.forClass(Sensor.class);
    Mockito.verify(sensorRepository).save(saved.capture());
    Assertions.assertEquals(SensorType.ABBOTT_LIBRE3, saved.getValue().type());
  }

  @Test
  void aUserWithoutASensorGetsAnErrorCodeRatherThanAnEmptyAnswer()
    throws Exception {
    endpoint.call(get("/sensor/current/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value(1000));
  }

  @Test
  void theCurrentSensorIsRenderedWithItsLifetime() throws Exception {
    Mockito.when(sensorRepository
      .findFirstByUserIdAndDiscardedAtIsNullOrderByRegisteredAtDesc(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(
        sensor(SensorType.DEXCOM_G7, 1_700_000_000_000L, 1_700_864_000_000L))));
    endpoint.call(get("/sensor/current/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.data").value(PAIRING_DATA))
      .andExpect(jsonPath("$.registered_at").value(1_700_000_000_000L))
      .andExpect(jsonPath("$.expires_at").value(1_700_864_000_000L));
  }

  @Test
  void theHistoryListsEverySensorTheUserEverPaired() throws Exception {
    Mockito.when(sensorRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(
        sensor(SensorType.DEXCOM_G7, 1L, 2L),
        sensor(SensorType.ABBOTT_LIBRE3, 3L, 4L))));
    endpoint.call(get("/sensor/history/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.sensors.length()").value(2))
      .andExpect(jsonPath("$.sensors[1].registered_at").value(3));
  }

  @Test
  void theHistoryOfAUserWhoNeverPairedAnythingIsEmptyButSuccessful()
    throws Exception {
    endpoint.call(get("/sensor/history/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.sensors").isEmpty());
  }
}
