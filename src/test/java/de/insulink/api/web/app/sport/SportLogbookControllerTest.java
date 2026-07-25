package de.insulink.api.web.app.sport;

import de.insulink.api.sport.SportRoutine;
import de.insulink.api.sport.SportRoutineRepository;
import de.insulink.api.sport.WorkoutSession;
import de.insulink.api.sport.WorkoutSessionRepository;
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
 * Routine templates and the completed-workout logbook, both replace-all. Their
 * nested content — a routine's items, a workout's sets — is the app's own JSON
 * and travels through unread; what has to survive is the app-side id the
 * logbook links a workout back to its routine by.
 */
@AppControllerTest({SportRoutineController.class, WorkoutSessionController.class})
final class SportLogbookControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final long STARTED_AT = 1_700_000_000_000L;

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private SportRoutineRepository routineRepository;
  @MockitoBean
  private WorkoutSessionRepository sessionRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubEmptyLogbook() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(routineRepository.findByUserIdOrderByOrderIndex(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(sessionRepository.findByUserIdOrderByStartedAt(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(routineRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(sessionRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(routineRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(sessionRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
  }

  private ResultActions sync(String path, String payload) throws Exception {
    return endpoint.call(post(path)
      .header("Authorization", TestAuthentication.bearer(USER_ID))
      .contentType(MediaType.APPLICATION_JSON)
      .content(payload));
  }

  @Test
  void findRendersARoutineWithItsItems() throws Exception {
    Mockito.when(routineRepository.findByUserIdOrderByOrderIndex(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(SportRoutine.create(
        UUID.randomUUID(), USER_ID, "app-push", "Push",
        "[{\"exercise\":\"app-bench\",\"sets\":3}]", 0))));
    endpoint.call(get("/sport/routines/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.routines[0].id").value("app-push"))
      .andExpect(jsonPath("$.routines[0].name").value("Push"))
      .andExpect(jsonPath("$.routines[0].items[0].exercise").value("app-bench"))
      .andExpect(jsonPath("$.routines[0].items[0].sets").value(3));
  }

  @Test
  void syncStoresTheRoutinesInTheOrderTheyArrived() throws Exception {
    sync("/sport/routines/sync/", """
      {"routines": [
        {"id": "app-push", "name": "Push", "items": [{"exercise": "app-bench"}]},
        {"id": "app-pull", "name": "Pull", "items": []}
      ]}""").andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(SportRoutine.class);
    Mockito.verify(routineRepository, Mockito.times(2)).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getAllValues().getFirst().userId());
    Assertions.assertEquals("app-push", saved.getAllValues().getFirst().clientId());
    Assertions.assertEquals(0, saved.getAllValues().getFirst().orderIndex());
    Assertions.assertTrue(saved.getAllValues().getFirst().items()
      .contains("app-bench"));
    Assertions.assertEquals(1, saved.getAllValues().getLast().orderIndex());
  }

  @Test
  void syncDropsTheRoutinesTheUserHadBefore() throws Exception {
    var stale = SportRoutine.create(UUID.randomUUID(), USER_ID, "gone", "Alt",
      "[]", 0);
    Mockito.when(routineRepository.findByUserIdOrderByOrderIndex(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(stale)));
    sync("/sport/routines/sync/", "{\"routines\": []}").andExpect(status().isOk());
    Mockito.verify(routineRepository).delete(stale);
    Mockito.verify(routineRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void findRendersALoggedWorkoutWithItsSets() throws Exception {
    Mockito.when(sessionRepository.findByUserIdOrderByStartedAt(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(
        WorkoutSession.create(UUID.randomUUID(), USER_ID, "app-workout-1",
          "app-push", STARTED_AT, "[{\"reps\":10,\"kg\":60}]"))));
    endpoint.call(get("/sport/workouts/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.workouts[0].id").value("app-workout-1"))
      .andExpect(jsonPath("$.workouts[0].routine").value("app-push"))
      .andExpect(jsonPath("$.workouts[0].started").value(STARTED_AT))
      .andExpect(jsonPath("$.workouts[0].sets[0].reps").value(10))
      .andExpect(jsonPath("$.workouts[0].sets[0].kg").value(60));
  }

  /**
   * The routine id stored on a workout is the app's, not a row id — that link
   * is what lets a pulled logbook still name the routine it was done from.
   */
  @Test
  void aLoggedWorkoutKeepsTheAppSideLinkToItsRoutine() throws Exception {
    sync("/sport/workouts/sync/", """
      {"workouts": [
        {"id": "app-workout-1", "routine": "app-push", "started": 1700000000000,
         "sets": [{"reps": 10, "kg": 60}]}
      ]}""").andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(WorkoutSession.class);
    Mockito.verify(sessionRepository).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getValue().userId());
    Assertions.assertEquals("app-workout-1", saved.getValue().clientId());
    Assertions.assertEquals("app-push", saved.getValue().routineClientId());
    Assertions.assertEquals(STARTED_AT, saved.getValue().startedAt());
    Assertions.assertTrue(saved.getValue().sets().contains("60"),
      saved.getValue().sets());
  }

  /**
   * The client id is a session's natural key (its start), so a list carrying it
   * twice is one workout logged twice — which is what a workout finished on two
   * devices produces. Storing both rows would show it twice in every logbook,
   * and each replace-all round trip would carry the pair straight back.
   */
  @Test
  void syncKeepsOneWorkoutPerClientId() throws Exception {
    sync("/sport/workouts/sync/", """
      {"workouts": [
        {"id": "app-workout-1", "routine": "app-push", "started": 1700000000000,
         "sets": [{"reps": 10}]},
        {"id": "app-workout-1", "routine": "app-push", "started": 1700000000000,
         "sets": [{"reps": 12}]}
      ]}""").andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(WorkoutSession.class);
    Mockito.verify(sessionRepository).save(saved.capture());
    Assertions.assertTrue(saved.getValue().sets().contains("12"),
      saved.getValue().sets());
  }

  @Test
  void syncDropsTheWorkoutsTheUserHadBefore() throws Exception {
    var stale = WorkoutSession.create(UUID.randomUUID(), USER_ID, "gone",
      "app-push", 0L, "[]");
    Mockito.when(sessionRepository.findByUserIdOrderByStartedAt(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(stale)));
    sync("/sport/workouts/sync/", "{\"workouts\": []}").andExpect(status().isOk());
    Mockito.verify(sessionRepository).delete(stale);
  }
}
