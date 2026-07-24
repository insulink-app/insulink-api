package de.insulink.api.web.app.health;

import de.insulink.api.health.LivePulseCache;
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
import java.util.concurrent.CompletableFuture;

/**
 * Relays the worn band's current heart rate between a user's own devices: the
 * phone {@code push}es every reading, the web panel {@code find}s it about once
 * a second while a routine runs. Nothing is written to the database — see
 * {@link LivePulseCache}; the durable curve travels on {@code /health/pulse/}
 * instead. Both endpoints are deliberately tiny: they are called at ~1 Hz.
 */
@RestController
public final class LivePulseController extends AppRestController {
  private final LivePulseCache liveCache;

  private LivePulseController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, LivePulseCache liveCache
  ) {
    super(authenticationKey, userRepository);
    this.liveCache = liveCache;
  }

  /**
   * The current reading, or a response without a {@code b} when the band went
   * quiet. The cache only ever hands back a fresh reading, so the caller needs
   * no clock of its own to judge one: present means live.
   */
  @AppEndpoint
  @RequestMapping(path = "/health/pulse/live/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findLivePulse(HttpServletRequest request) {
    return liveCache.find(findUserId(request))
      .map(pulse -> ApiResponse.success(Map.<String, Object>of("b", pulse.bpm())))
      .orElseGet(ApiResponse::success)
      .future();
  }

  @AppEndpoint
  @RequestMapping(path = "/health/pulse/live/push/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> pushLivePulse(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    if (!body.has("b")) {
      return ApiResponse.error(1000).future();
    }
    var userId = findUserId(request);
    liveCache.record(userId, body.getInt("b"));
    return ApiResponse.success(Map.of("live", liveCache.isWatched(userId))).future();
  }
}
