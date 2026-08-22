package de.insulink.api.web.app.sensor;

import de.insulink.api.sensor.Sensor;
import de.insulink.api.sensor.SensorRepository;
import de.insulink.api.sensor.SensorType;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Saying a sensor is gone, which is what stops the app offering it back at every
 * launch.
 *
 * The point of the endpoint is what it does NOT do: the row stays, because it is
 * the user's sensor history. Only the offering stops.
 */
@AppControllerTest(SensorDiscardController.class)
final class SensorDiscardControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID SENSOR_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private SensorRepository sensorRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubRepository() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(sensorRepository.save(Mockito.any()))
      .thenAnswer(invocation ->
        CompletableFuture.completedFuture(invocation.getArgument(0)));
  }

  private Sensor sensorOf(UUID ownerId) {
    return Sensor.create(SENSOR_ID, ownerId, SensorType.DEXCOM_G7, "blob",
      1_700_000_000_000L, 1_700_000_288_000L, null);
  }

  private String discardBody() {
    return "{\"sensor_id\":\"" + SENSOR_ID + "\"}";
  }

  @Test
  void discardingStampsTheSensorAndKeepsIt() throws Exception {
    var sensor = sensorOf(USER_ID);
    Mockito.when(sensorRepository.findById(SENSOR_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(sensor)));

    endpoint.call(post("/sensor/discard/")
      .contentType(MediaType.APPLICATION_JSON)
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .content(discardBody()));

    var saved = ArgumentCaptor.forClass(Sensor.class);
    Mockito.verify(sensorRepository).save(saved.capture());
    assertNotNull(saved.getValue().discardedAt());
    Mockito.verify(sensorRepository, Mockito.never()).delete(Mockito.any());
  }

  /**
   * The row is the history. Deleting would answer the same question by throwing
   * away the record of a sensor that was actually worn.
   */
  @Test
  void theRowIsNeverDeleted() throws Exception {
    Mockito.when(sensorRepository.findById(SENSOR_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(sensorOf(USER_ID))));

    endpoint.call(post("/sensor/discard/")
      .contentType(MediaType.APPLICATION_JSON)
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .content(discardBody()));

    Mockito.verify(sensorRepository, Mockito.never()).deleteById(Mockito.any());
  }

  /** A guessed id must not retire somebody else's sensor. */
  @Test
  void anotherAccountsSensorCannotBeDiscarded() throws Exception {
    var sensor = sensorOf(UUID.randomUUID());
    Mockito.when(sensorRepository.findById(SENSOR_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(sensor)));

    endpoint.call(post("/sensor/discard/")
      .contentType(MediaType.APPLICATION_JSON)
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .content(discardBody()));

    assertNull(sensor.discardedAt());
    Mockito.verify(sensorRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void anUnknownSensorSavesNothing() throws Exception {
    Mockito.when(sensorRepository.findById(SENSOR_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    endpoint.call(post("/sensor/discard/")
      .contentType(MediaType.APPLICATION_JSON)
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .content(discardBody()));

    Mockito.verify(sensorRepository, Mockito.never()).save(Mockito.any());
  }

  /** Repeating it must not rewrite when the user actually said so. */
  @Test
  void discardingTwiceKeepsTheFirstMoment() {
    var sensor = sensorOf(USER_ID);

    sensor.discard(1_700_000_500_000L);
    sensor.discard(1_800_000_000_000L);

    org.junit.jupiter.api.Assertions
      .assertEquals(1_700_000_500_000L, sensor.discardedAt());
  }
}
