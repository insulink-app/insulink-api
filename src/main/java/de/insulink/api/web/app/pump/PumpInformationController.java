package de.insulink.api.web.app.pump;

import com.google.common.collect.Maps;
import de.insulink.api.pump.Pump;
import de.insulink.api.pump.PumpRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Hands a stored pump back to the app, which is what lets a user reconnect to a
 * pod they are still wearing after reinstalling or resetting the app.
 */
@RestController
public final class PumpInformationController extends AppRestController {
  private final PumpRepository pumpRepository;

  private PumpInformationController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, PumpRepository pumpRepository
  ) {
    super(authenticationKey, userRepository);
    this.pumpRepository = pumpRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/pump/current/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findCurrentPump(
    HttpServletRequest request
  ) {
    var userId = findUserId(request);
    return pumpRepository.findFirstByUserIdOrderByRegisteredAtDesc(userId)
      .thenApply(this::currentPumpResponse);
  }

  @AppEndpoint
  @RequestMapping(path = "/pump/history/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findPumpHistory(
    HttpServletRequest request
  ) {
    var userId = findUserId(request);
    return pumpRepository.findByUserId(userId).thenApply(this::historyResponse);
  }

  private ApiResponse currentPumpResponse(Optional<Pump> pump) {
    return pump.map(this::pumpInformation).map(ApiResponse::success)
      .orElseGet(() -> ApiResponse.error(1000));
  }

  private ApiResponse historyResponse(List<Pump> pumps) {
    return ApiResponse.success(Map.of("pumps",
      pumps.stream().map(this::pumpInformation).toList()));
  }

  private Map<String, Object> pumpInformation(Pump pump) {
    var information = Maps.<String, Object>newHashMap();
    information.put("id", pump.id());
    information.put("type", pump.type());
    information.put("data", pump.data());
    information.put("registered_at", pump.registeredAt());
    information.put("expires_at", pump.expiresAt());
    return information;
  }
}
