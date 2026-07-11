package de.insulink.api.web.app.nutrition;

import de.insulink.api.nutrition.Drink;
import de.insulink.api.nutrition.DrinkRepository;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.request.ApiRequestBody;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 * The user's logged drinks (hydration). {@code find} returns them oldest first;
 * {@code sync} replaces the set with the app's complete current list.
 */
@RestController
public final class DrinkController extends AppRestController {
  private final DrinkRepository drinkRepository;

  private DrinkController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, DrinkRepository drinkRepository
  ) {
    super(authenticationKey, userRepository);
    this.drinkRepository = drinkRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/nutrition/drinks/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findDrinks(HttpServletRequest request) {
    return drinkRepository.findByUserIdOrderByDrinkedAt(findUserId(request))
      .thenApply(drinks -> drinks.stream().map(this::information).toList())
      .thenApply(drinks -> ApiResponse.success(Map.of("drinks", drinks)));
  }

  @AppEndpoint
  @RequestMapping(path = "/nutrition/drinks/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncDrinks(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var fresh = ApiRequestBody.of(payload, response).getObjectList("drinks")
      .stream().map(entry -> drink(userId, entry)).toList();
    return drinkRepository.findByUserIdOrderByDrinkedAt(userId)
      .thenCompose(existing ->
        NutritionCollection.create(drinkRepository).replace(existing, fresh));
  }

  private Drink drink(UUID userId, ApiRequestBody entry) {
    return Drink.create(UUID.randomUUID(), userId, entry.getLong("at"),
      entry.getInt("ml"), entry.getString("kind"));
  }

  private Map<String, Object> information(Drink drink) {
    return Map.of("at", drink.drinkedAt(), "ml", drink.ml(), "kind", drink.kind());
  }
}
