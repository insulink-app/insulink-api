package de.insulink.api.web.app.sport;

import de.insulink.api.sport.SportExercise;
import de.insulink.api.sport.SportExerciseRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The exercise library is replace-all: the app pushes its complete list and the
 * server drops whatever it had. What travels back out is the app's own id, not
 * the row id, so a pull restores the same exercises the app links its routines
 * against, in the order they were pushed.
 */
@AppControllerTest(SportExerciseController.class)
final class SportExerciseControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private SportExerciseRepository exerciseRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubEmptyLibrary() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(exerciseRepository.findByUserIdOrderByOrderIndex(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(exerciseRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(exerciseRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
  }

  private SportExercise exercise(String clientId, String name, int orderIndex) {
    return SportExercise.create(UUID.randomUUID(), USER_ID, clientId, name,
      "strength", orderIndex);
  }

  private void sync(String payload) throws Exception {
    endpoint.call(post("/sport/exercises/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void findReturnsAnEmptyLibraryForANewUser() throws Exception {
    endpoint.call(get("/sport/exercises/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.exercises").isEmpty());
  }

  @Test
  void findHandsBackTheAppSideIdAndTheStoredOrder() throws Exception {
    Mockito.when(exerciseRepository.findByUserIdOrderByOrderIndex(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(
        exercise("app-squat", "Kniebeuge", 0),
        exercise("app-bench", "Bankdrücken", 1))));
    endpoint.call(get("/sport/exercises/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.exercises[0].id").value("app-squat"))
      .andExpect(jsonPath("$.exercises[0].name").value("Kniebeuge"))
      .andExpect(jsonPath("$.exercises[0].kind").value("strength"))
      .andExpect(jsonPath("$.exercises[1].id").value("app-bench"));
  }

  @Test
  void syncStoresThePushedExercisesInTheOrderTheyArrived() throws Exception {
    sync("""
      {"exercises": [
        {"id": "app-squat", "name": "Kniebeuge", "kind": "strength"},
        {"id": "app-run", "name": "Laufen", "kind": "cardio"}
      ]}""");
    var saved = ArgumentCaptor.forClass(SportExercise.class);
    Mockito.verify(exerciseRepository, Mockito.times(2)).save(saved.capture());
    var exercises = saved.getAllValues();
    Assertions.assertEquals(USER_ID, exercises.getFirst().userId());
    Assertions.assertEquals("app-squat", exercises.getFirst().clientId());
    Assertions.assertEquals(0, exercises.getFirst().orderIndex());
    Assertions.assertEquals("cardio", exercises.getLast().kind());
    Assertions.assertEquals(1, exercises.getLast().orderIndex());
  }

  /**
   * Replace-all is what makes a deletion in the app stick, so the previously
   * stored rows have to be gone before the fresh list is written.
   */
  @Test
  void syncDropsTheExercisesTheUserHadBefore() throws Exception {
    var stale = exercise("app-gone", "Alt", 0);
    Mockito.when(exerciseRepository.findByUserIdOrderByOrderIndex(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(stale)));
    sync("{\"exercises\": []}");
    Mockito.verify(exerciseRepository).delete(stale);
    Mockito.verify(exerciseRepository, Mockito.never()).save(Mockito.any());
  }
}
