package de.insulink.api.web.app.sport;

import de.insulink.api.sport.CardioTraining;
import de.insulink.api.sport.CardioTrainingRepository;
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
 * The user's endurance trainings. {@code find} returns them oldest first with
 * their route; {@code sync} replaces the set with the app's complete current
 * list.
 */
@RestController
public final class CardioTrainingController extends AppRestController {
  private final CardioTrainingRepository trainingRepository;

  private CardioTrainingController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, CardioTrainingRepository trainingRepository
  ) {
    super(authenticationKey, userRepository);
    this.trainingRepository = trainingRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/sport/trainings/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findTrainings(
    HttpServletRequest request
  ) {
    return trainingRepository.findByUserIdOrderByStartMs(findUserId(request))
      .thenApply(trainings -> trainings.stream().map(this::information).toList())
      .thenApply(trainings -> ApiResponse.success(Map.of("trainings", trainings)));
  }

  @AppEndpoint
  @RequestMapping(path = "/sport/trainings/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncTrainings(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var fresh = ApiRequestBody.of(payload, response).getObjectList("trainings")
      .stream().map(entry -> training(userId, entry)).toList();
    return trainingRepository.findByUserIdOrderByStartMs(userId)
      .thenCompose(existing ->
        SportCollection.create(trainingRepository).replace(existing, fresh));
  }

  private CardioTraining training(UUID userId, ApiRequestBody entry) {
    return CardioTraining.create(UUID.randomUUID(), userId, entry.getString("id"),
      entry.getString("type"), entry.getLong("start"), entry.getLong("end"),
      entry.getDouble("dist"), entry.getBoolean("auto"),
      entry.raw().getJSONArray("track").toString());
  }

  private Map<String, Object> information(CardioTraining training) {
    return Map.of("id", training.clientId(), "type", training.type(),
      "start", training.startedAt(), "end", training.endedAt(),
      "dist", training.distance(), "auto", training.detected(),
      "track", new JSONArray(training.track()).toList());
  }
}
