package de.insulink.api.web.app.user;

import de.insulink.api.user.UserRepository;
import de.insulink.api.user.settings.history.UserSettingsChange;
import de.insulink.api.user.settings.history.UserSettingsChangeRepository;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserSettingsHistoryController extends AppRestController {
  private final UserSettingsChangeRepository changeRepository;

  private UserSettingsHistoryController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, UserSettingsChangeRepository changeRepository
  ) {
    super(authenticationKey, userRepository);
    this.changeRepository = changeRepository;
  }

  /** Every recorded change to the caller's settings, newest first. */
  @AppEndpoint
  @RequestMapping(path = "/user/settings/history/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findSettingsHistory(
    HttpServletRequest request
  ) {
    return changeRepository.findByUserIdOrderByChangedAtDesc(findUserId(request))
      .thenApply(changes -> changes.stream().map(this::changeInformation).toList())
      .thenApply(changes -> ApiResponse.success(Map.of("changes", changes)));
  }

  /**
   * A HashMap, not {@code Map.of}: the old value of an added key and the new
   * value of a removed one are null, and must reach the client as JSON null.
   */
  private Map<String, Object> changeInformation(UserSettingsChange change) {
    var information = new HashMap<String, Object>();
    information.put("key", change.settingKey());
    information.put("old_value", change.oldValue());
    information.put("new_value", change.newValue());
    information.put("time", change.changedAt());
    return information;
  }
}
