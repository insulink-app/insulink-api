package de.insulink.api.web.app.sport;

import de.insulink.api.sport.SportExercise;
import de.insulink.api.sport.SportExerciseRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * The user's exercise-template library. {@code find} returns them in the user's
 * order; {@code sync} replaces the set with the app's complete current list.
 */
@RestController
public final class SportExerciseController extends AppRestController {
  private final SportExerciseRepository exerciseRepository;

  private SportExerciseController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, SportExerciseRepository exerciseRepository
  ) {
    super(authenticationKey, userRepository);
    this.exerciseRepository = exerciseRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/sport/exercises/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findExercises(
    HttpServletRequest request
  ) {
    return exerciseRepository.findByUserIdOrderByOrderIndex(findUserId(request))
      .thenApply(exercises -> exercises.stream().map(this::information).toList())
      .thenApply(exercises -> ApiResponse.success(Map.of("exercises", exercises)));
  }

  @AppEndpoint
  @RequestMapping(path = "/sport/exercises/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncExercises(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var entries = ApiRequestBody.of(payload, response).getObjectList("exercises");
    var fresh = exercises(userId, entries);
    return exerciseRepository.findByUserIdOrderByOrderIndex(userId)
      .thenCompose(existing ->
        SportCollection.create(exerciseRepository).replace(existing, fresh));
  }

  private List<SportExercise> exercises(UUID userId, List<ApiRequestBody> entries) {
    return IntStream.range(0, entries.size())
      .mapToObj(index -> exercise(userId, entries.get(index), index)).toList();
  }

  private SportExercise exercise(UUID userId, ApiRequestBody entry, int index) {
    return SportExercise.create(UUID.randomUUID(), userId,
      entry.getString("id"), entry.getString("name"),
      entry.getString("kind"), index);
  }

  private Map<String, Object> information(SportExercise exercise) {
    return Map.of("id", exercise.clientId(), "name", exercise.name(),
      "kind", exercise.kind());
  }
}
