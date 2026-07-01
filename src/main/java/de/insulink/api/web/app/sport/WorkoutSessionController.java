package de.insulink.api.web.app.sport;

import de.insulink.api.sport.WorkoutSession;
import de.insulink.api.sport.WorkoutSessionRepository;
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
 * The user's completed workouts (logbook). {@code find} returns them oldest
 * first; {@code sync} replaces the set with the app's complete current list.
 */
@RestController
public final class WorkoutSessionController extends AppRestController {
  private final WorkoutSessionRepository sessionRepository;

  private WorkoutSessionController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, WorkoutSessionRepository sessionRepository
  ) {
    super(authenticationKey, userRepository);
    this.sessionRepository = sessionRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/sport/workouts/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findWorkouts(
    HttpServletRequest request
  ) {
    return sessionRepository.findByUserIdOrderByStartedAt(findUserId(request))
      .thenApply(sessions -> sessions.stream().map(this::information).toList())
      .thenApply(sessions -> ApiResponse.success(Map.of("workouts", sessions)));
  }

  @AppEndpoint
  @RequestMapping(path = "/sport/workouts/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncWorkouts(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var fresh = ApiRequestBody.of(payload, response).getObjectList("workouts")
      .stream().map(entry -> session(userId, entry)).toList();
    return sessionRepository.findByUserIdOrderByStartedAt(userId)
      .thenCompose(existing ->
        SportCollection.create(sessionRepository).replace(existing, fresh));
  }

  private WorkoutSession session(UUID userId, ApiRequestBody entry) {
    return WorkoutSession.create(UUID.randomUUID(), userId,
      entry.getString("id"), entry.getString("routine"),
      entry.getLong("started"), entry.raw().getJSONArray("sets").toString());
  }

  private Map<String, Object> information(WorkoutSession session) {
    return Map.of("id", session.clientId(), "routine", session.routineClientId(),
      "started", session.startedAt(), "sets", new JSONArray(session.sets()).toList());
  }
}
