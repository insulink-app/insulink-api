package de.insulink.api.web.app.sport;

import de.insulink.api.sport.ActiveWorkout;
import de.insulink.api.sport.ActiveWorkoutRepository;
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

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The running workout, handed between a user's devices. What matters here is
 * that a user can only ever have one: every push overwrites the same row, so a
 * second device picking the workout up and pushing on does not create a rival
 * workout. The snapshot itself is the clients' own JSON and travels through
 * unread — but a push without one is refused, so nothing unparseable is left
 * behind for the other device to choke on.
 */
@AppControllerTest(ActiveWorkoutController.class)
final class ActiveWorkoutControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String SNAPSHOT =
    "{\"routine\":\"Push\",\"set\":3,\"started_at\":1700000000000}";

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private ActiveWorkoutRepository workoutRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubNoRunningWorkout() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(workoutRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    Mockito.when(workoutRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(workoutRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
  }

  private ActiveWorkout runningWorkout(UUID id) {
    return ActiveWorkout.create(id, USER_ID, SNAPSHOT, 1_700_000_000_000L);
  }

  private org.springframework.test.web.servlet.ResultActions sync(String payload)
    throws Exception {
    return endpoint.call(post("/sport/workout/active/sync/")
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .contentType(MediaType.APPLICATION_JSON)
      .content(payload));
  }

  @Test
  void findAnswersWithoutAWorkoutWhenNoneIsRunning() throws Exception {
    endpoint.call(get("/sport/workout/active/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.workout").doesNotExist());
  }

  @Test
  void findHandsTheSnapshotBackAsTheObjectItWasPushedAs() throws Exception {
    Mockito.when(workoutRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(
        Optional.of(runningWorkout(UUID.randomUUID()))));
    endpoint.call(get("/sport/workout/active/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.workout.routine").value("Push"))
      .andExpect(jsonPath("$.workout.set").value(3))
      .andExpect(jsonPath("$.updated").value(1_700_000_000_000L));
  }

  @Test
  void syncStoresTheSnapshotForTheUserAndReportsWhenItWasWritten()
    throws Exception {
    sync("{\"workout\": " + SNAPSHOT + "}")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.updated").isNumber());
    var saved = ArgumentCaptor.forClass(ActiveWorkout.class);
    Mockito.verify(workoutRepository).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getValue().userId());
    Assertions.assertTrue(saved.getValue().data().contains("\"routine\""),
      saved.getValue().data());
    Assertions.assertTrue(saved.getValue().updatedAt() > 0L);
  }

  /**
   * The second device has to write the same row, otherwise both devices end up
   * driving a workout of their own.
   */
  @Test
  void aPushFromASecondDeviceOverwritesTheRowThatIsAlreadyThere()
    throws Exception {
    var existingId = UUID.randomUUID();
    Mockito.when(workoutRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(
        Optional.of(runningWorkout(existingId))));
    sync("{\"workout\": {\"routine\":\"Push\",\"set\":4}}");
    var saved = ArgumentCaptor.forClass(ActiveWorkout.class);
    Mockito.verify(workoutRepository).save(saved.capture());
    Assertions.assertEquals(existingId, saved.getValue().id());
    Assertions.assertTrue(saved.getValue().data().contains("4"),
      saved.getValue().data());
  }

  /**
   * The zombie workout: a device mirrors the session it is following, another
   * one finishes it, and the push that was already on its way writes the row
   * back — every device then offers to resume a workout that is over, and
   * finishing it logs the session a second time under the same id. The stamp the
   * sender carries names a row that is gone, which is what gives it away.
   */
  @Test
  void aPushCarryingOnAWorkoutThatWasEndedElsewhereIsRefused() throws Exception {
    sync("{\"workout\": " + SNAPSHOT + ", \"updated\": 1700000000000}")
      .andExpect(jsonPath("$.error.code").value(1001));
    Mockito.verify(workoutRepository, Mockito.never()).save(Mockito.any());
  }

  /**
   * A device STARTING a workout knows no stamp, so it must still be able to
   * create the row — that is the normal first push of every session.
   */
  @Test
  void aPushWithoutAStampStartsAWorkoutEvenWhenNoneRuns() throws Exception {
    sync("{\"workout\": " + SNAPSHOT + ", \"updated\": 0}")
      .andExpect(jsonPath("$.success").value(true));
    Mockito.verify(workoutRepository).save(Mockito.any());
  }

  @Test
  void aPushWithoutASnapshotIsRefusedInsteadOfStored() throws Exception {
    sync("{}").andExpect(jsonPath("$.error.code").value(1000));
    Mockito.verify(workoutRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void clearingEndsTheRunningWorkout() throws Exception {
    var workout = runningWorkout(UUID.randomUUID());
    Mockito.when(workoutRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(Optional.of(workout)));
    endpoint.call(post("/sport/workout/active/clear/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.success").value(true));
    Mockito.verify(workoutRepository).delete(workout);
  }

  /**
   * Both devices may send the clear; the second one must not fail.
   */
  @Test
  void clearingWhenNothingRunsIsStillASuccess() throws Exception {
    endpoint.call(post("/sport/workout/active/clear/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
    Mockito.verify(workoutRepository, Mockito.never()).delete(Mockito.any());
  }
}
