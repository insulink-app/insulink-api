package de.insulink.api.web.app.sport;

import de.insulink.api.sport.ActiveWorkout;
import de.insulink.api.sport.ActiveWorkoutRepository;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.request.ApiRequestBody;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import com.google.common.collect.Maps;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The user's in-progress workout, shared across their devices. {@code find}
 * returns the current snapshot (nothing when no workout runs), {@code sync}
 * upserts it and {@code clear} ends it. Whichever device drives the workout
 * pushes on every change, so another device — the app after a restart, or the
 * web panel — can pick the same workout up mid-set and finish it.
 * <p>
 * The snapshot is stored as the sender's own JSON blob: its shape is the app's
 * {@code WorkoutSnapshot}, agreed between the clients, and nothing here reads
 * into it. A {@code sync} without one is rejected rather than stored, so a
 * malformed push cannot leave a workout the clients then fail to parse.
 */
@RestController
public final class ActiveWorkoutController extends AppRestController {
  private final ActiveWorkoutRepository workoutRepository;

  private ActiveWorkoutController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, ActiveWorkoutRepository workoutRepository
  ) {
    super(authenticationKey, userRepository);
    this.workoutRepository = workoutRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/sport/workout/active/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findActiveWorkout(
    HttpServletRequest request
  ) {
    return workoutRepository.findByUserId(findUserId(request))
      .thenApply(this::information);
  }

  @AppEndpoint
  @RequestMapping(path = "/sport/workout/active/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncActiveWorkout(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var body = ApiRequestBody.of(payload, response);
    if (!body.has("workout")) {
      return ApiResponse.error(1000).future();
    }
    var snapshot = body.getObject("workout").raw().toString();
    return workoutRepository.findByUserId(userId)
      .thenApply(existing -> workout(userId, existing, snapshot))
      .thenCompose(workoutRepository::save)
      .thenApply(saved -> ApiResponse.success(
        Map.<String, Object>of("updated", saved.updatedAt())));
  }

  @AppEndpoint
  @RequestMapping(path = "/sport/workout/active/clear/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> clearActiveWorkout(
    HttpServletRequest request
  ) {
    return workoutRepository.findByUserId(findUserId(request))
      .thenCompose(existing -> existing
        .map(workoutRepository::delete)
        .orElseGet(() -> CompletableFuture.<Void>completedFuture(null)))
      .thenApply(_ -> ApiResponse.success());
  }

  /**
   * Reuses the row the user already has, so the snapshot is overwritten in place
   * and a user never accumulates more than one running workout.
   */
  private ActiveWorkout workout(
    UUID userId, Optional<ActiveWorkout> existing, String data
  ) {
    return ActiveWorkout.create(existing.map(ActiveWorkout::id)
      .orElseGet(UUID::randomUUID), userId, data, System.currentTimeMillis());
  }

  /**
   * Answers with the snapshot and when it was last written; a response without a
   * {@code workout} means no workout is running.
   */
  private ApiResponse information(Optional<ActiveWorkout> workout) {
    if (workout.isEmpty()) {
      return ApiResponse.success();
    }
    var body = Maps.<String, Object>newHashMap();
    body.put("workout", new JSONObject(workout.get().data()).toMap());
    body.put("updated", workout.get().updatedAt());
    return ApiResponse.success(body);
  }
}
