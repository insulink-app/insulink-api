package de.insulink.api.web.app.nutrition;

import de.insulink.api.nutrition.Meal;
import de.insulink.api.nutrition.MealRepository;
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
 * The meal log, replace-all like the rest of the nutrition collections. The
 * per-meal food list is stored as the JSON array the app sent and handed back
 * as an array again, not as the string it is stored in.
 */
@AppControllerTest(MealController.class)
final class MealControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private MealRepository mealRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubEmptyLog() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(mealRepository.findByUserIdOrderByTime(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(mealRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(mealRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
  }

  private void sync(String payload) throws Exception {
    endpoint.call(post("/nutrition/meals/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void findReturnsAnEmptyLogForANewUser() throws Exception {
    endpoint.call(get("/nutrition/meals/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.meals").isEmpty());
  }

  @Test
  void findRendersAMealWithItsFoodEntriesBackAsAnArray() throws Exception {
    Mockito.when(mealRepository.findByUserIdOrderByTime(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(Meal.create(
        UUID.randomUUID(), USER_ID, 1_700_000_000_000L, 62.5, 120, 6.0,
        "[{\"name\":\"Brot\",\"carbs\":30}]"))));
    endpoint.call(get("/nutrition/meals/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.meals[0].time").value(1_700_000_000_000L))
      .andExpect(jsonPath("$.meals[0].carbs").value(62.5))
      .andExpect(jsonPath("$.meals[0].glucose").value(120))
      .andExpect(jsonPath("$.meals[0].bolus").value(6.0))
      .andExpect(jsonPath("$.meals[0].entries[0].name").value("Brot"))
      .andExpect(jsonPath("$.meals[0].entries[0].carbs").value(30));
  }

  @Test
  void syncStoresThePushedMealsAgainstTheUser() throws Exception {
    sync("""
      {"meals": [
        {"time": 1700000000000, "carbs": 62.5, "glucose": 120, "bolus": 6.0,
         "entries": [{"name": "Brot", "carbs": 30}]}
      ]}""");
    var saved = ArgumentCaptor.forClass(Meal.class);
    Mockito.verify(mealRepository).save(saved.capture());
    var meal = saved.getValue();
    Assertions.assertEquals(USER_ID, meal.userId());
    Assertions.assertEquals(1_700_000_000_000L, meal.time());
    Assertions.assertEquals(62.5, meal.carbs());
    Assertions.assertEquals(120, meal.glucose());
    Assertions.assertEquals(6.0, meal.bolus());
    Assertions.assertTrue(meal.entries().contains("Brot"), meal.entries());
  }

  @Test
  void syncDropsTheMealsTheUserHadBefore() throws Exception {
    var stale = Meal.create(UUID.randomUUID(), USER_ID, 0L, 1.0, 100, 1.0, "[]");
    Mockito.when(mealRepository.findByUserIdOrderByTime(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(stale)));
    sync("{\"meals\": []}");
    Mockito.verify(mealRepository).delete(stale);
    Mockito.verify(mealRepository, Mockito.never()).save(Mockito.any());
  }
}
