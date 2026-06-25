package de.insulink.api.web.app.glucose;

import de.insulink.api.glucose.GlucoseEntry;
import de.insulink.api.glucose.GlucoseRepository;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class GlucoseHistoryController extends AppRestController {
  private final GlucoseRepository glucoseRepository;

  private GlucoseHistoryController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, GlucoseRepository glucoseRepository
  ) {
    super(authenticationKey, userRepository);
    this.glucoseRepository = glucoseRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/glucose/history/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findGlucoseHistory(
    HttpServletRequest request
  ) {
    return glucoseRepository.findByUserId(findUserId(request))
      .thenApply(entries -> entries.stream()
        .map(this::glucoseEntryInformation).toList())
      .thenApply(entries -> ApiResponse.success(Map.of("entries", entries)));
  }

  private Map<String, Object> glucoseEntryInformation(GlucoseEntry entry) {
    return Map.of("value", entry.value(), "time", entry.recordedAt());
  }
}