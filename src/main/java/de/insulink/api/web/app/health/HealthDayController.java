package de.insulink.api.web.app.health;

import de.insulink.api.health.HealthDay;
import de.insulink.api.health.HealthDayRepository;
import de.insulink.api.iterator.AsyncIterator;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.request.ApiRequestBody;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
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
 * The user's Google Health / Health Connect archive (resting heart rate, sleep,
 * blood oxygen, hypnogram) stored one blob per day. {@code find} returns every
 * day oldest first; {@code sync} replaces the set with the app's complete current
 * archive — the app only ever adds/updates days, so replace-all keeps it correct
 * without per-day endpoints.
 */
@RestController
public final class HealthDayController extends AppRestController {
  private final HealthDayRepository dayRepository;

  private HealthDayController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, HealthDayRepository dayRepository
  ) {
    super(authenticationKey, userRepository);
    this.dayRepository = dayRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/health/days/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findDays(HttpServletRequest request) {
    return dayRepository.findByUserIdOrderByDateKey(findUserId(request))
      .thenApply(days -> days.stream().map(this::information).toList())
      .thenApply(days -> ApiResponse.success(Map.of("days", days)));
  }

  @AppEndpoint
  @RequestMapping(path = "/health/days/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncDays(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var fresh = ApiRequestBody.of(payload, response).getObjectList("days")
      .stream().map(entry -> day(userId, entry)).toList();
    return dayRepository.findByUserIdOrderByDateKey(userId).thenCompose(existing ->
      AsyncIterator.execute(existing, dayRepository::delete)
        .thenCompose(deleted -> AsyncIterator.execute(fresh, dayRepository::save))
        .thenApply(saved -> ApiResponse.success()));
  }

  private HealthDay day(UUID userId, ApiRequestBody entry) {
    return HealthDay.create(UUID.randomUUID(), userId,
      entry.getString("d"), entry.raw().toString());
  }

  private Map<String, Object> information(HealthDay day) {
    return new JSONObject(day.data()).toMap();
  }
}
