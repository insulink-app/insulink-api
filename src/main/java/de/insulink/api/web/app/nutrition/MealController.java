package de.insulink.api.web.app.nutrition;

import de.insulink.api.nutrition.Meal;
import de.insulink.api.nutrition.MealRepository;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.request.ApiRequestBody;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The user's meal log. {@code find} returns the meals oldest first; {@code sync}
 * replaces the set with the app's complete current list.
 */
@RestController
public final class MealController extends AppRestController {
  private final MealRepository mealRepository;

  private MealController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, MealRepository mealRepository
  ) {
    super(authenticationKey, userRepository);
    this.mealRepository = mealRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/nutrition/meals/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findMeals(HttpServletRequest request) {
    return mealRepository.findByUserIdOrderByTime(findUserId(request))
      .thenApply(meals -> meals.stream().map(this::information).toList())
      .thenApply(meals -> ApiResponse.success(Map.of("meals", meals)));
  }

  @AppEndpoint
  @RequestMapping(path = "/nutrition/meals/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncMeals(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var fresh = ApiRequestBody.of(payload, response).getObjectList("meals")
      .stream().map(entry -> meal(userId, entry)).toList();
    return mealRepository.findByUserIdOrderByTime(userId)
      .thenCompose(existing ->
        NutritionCollection.create(mealRepository).replace(existing, fresh));
  }

  private Meal meal(UUID userId, ApiRequestBody entry) {
    return Meal.create(UUID.randomUUID(), userId, entry.getLong("time"),
      entry.getDouble("carbs"), entry.getInt("glucose"), entry.getDouble("bolus"),
      entry.raw().getJSONArray("entries").toString());
  }

  private Map<String, Object> information(Meal meal) {
    return Map.of("time", meal.time(), "carbs", meal.carbs(),
      "glucose", meal.glucose(), "bolus", meal.bolus(),
      "entries", new JSONArray(meal.entries()).toList());
  }
}
