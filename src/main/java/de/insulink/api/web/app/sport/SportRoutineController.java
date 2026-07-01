package de.insulink.api.web.app.sport;

import de.insulink.api.sport.SportRoutine;
import de.insulink.api.sport.SportRoutineRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * The user's routine templates. {@code find} returns them in order with their
 * items; {@code sync} replaces the set with the app's complete current list.
 */
@RestController
public final class SportRoutineController extends AppRestController {
  private final SportRoutineRepository routineRepository;

  private SportRoutineController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, SportRoutineRepository routineRepository
  ) {
    super(authenticationKey, userRepository);
    this.routineRepository = routineRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/sport/routines/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findRoutines(
    HttpServletRequest request
  ) {
    return routineRepository.findByUserIdOrderByOrderIndex(findUserId(request))
      .thenApply(routines -> routines.stream().map(this::information).toList())
      .thenApply(routines -> ApiResponse.success(Map.of("routines", routines)));
  }

  @AppEndpoint
  @RequestMapping(path = "/sport/routines/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncRoutines(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var entries = ApiRequestBody.of(payload, response).getObjectList("routines");
    var fresh = IntStream.range(0, entries.size())
      .mapToObj(index -> routine(userId, entries.get(index), index)).toList();
    return routineRepository.findByUserIdOrderByOrderIndex(userId)
      .thenCompose(existing ->
        SportCollection.create(routineRepository).replace(existing, fresh));
  }

  private SportRoutine routine(UUID userId, ApiRequestBody entry, int index) {
    return SportRoutine.create(UUID.randomUUID(), userId, entry.getString("id"),
      entry.getString("name"), entry.raw().getJSONArray("items").toString(), index);
  }

  private Map<String, Object> information(SportRoutine routine) {
    return Map.of("id", routine.clientId(), "name", routine.name(),
      "items", new JSONArray(routine.items()).toList());
  }
}
