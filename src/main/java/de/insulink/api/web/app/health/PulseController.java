package de.insulink.api.web.app.health;

import de.insulink.api.health.PulseSample;
import de.insulink.api.health.PulseSampleRepository;
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
 * The user's granular intraday pulse curve, one row per minute-aligned instant.
 * {@code find} returns every sample oldest first ({@code {t, b}} = recordedAt in
 * epoch millis + bpm); {@code sync} merges the pushed samples by {@code recordedAt}
 * (see {@link PulseSampleSync}) — inserting new timestamps and updating changed
 * ones without deleting, so the app can push just the live tail incrementally.
 */
@RestController
public final class PulseController extends AppRestController {
  private final PulseSampleRepository sampleRepository;

  private PulseController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, PulseSampleRepository sampleRepository
  ) {
    super(authenticationKey, userRepository);
    this.sampleRepository = sampleRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/health/pulse/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findSamples(HttpServletRequest request) {
    var sync = PulseSampleSync.create(sampleRepository);
    return sampleRepository.findByUserIdOrderByRecordedAt(findUserId(request))
      .thenApply(samples -> samples.stream().map(sync::information).toList())
      .thenApply(samples -> ApiResponse.success(Map.of("samples", samples)));
  }

  @AppEndpoint
  @RequestMapping(path = "/health/pulse/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncSamples(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var fresh = ApiRequestBody.of(payload, response).getObjectList("samples")
      .stream().map(sample -> sample(userId, sample)).toList();
    return sampleRepository.findByUserIdOrderByRecordedAt(userId).thenCompose(existing ->
      PulseSampleSync.create(sampleRepository).merge(existing, fresh));
  }

  private PulseSample sample(UUID userId, ApiRequestBody entry) {
    return PulseSample.create(
      UUID.randomUUID(), userId, entry.getLong("t"), entry.getInt("b"));
  }
}
