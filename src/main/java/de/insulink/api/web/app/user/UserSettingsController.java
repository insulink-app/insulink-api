package de.insulink.api.web.app.user;

import de.insulink.api.iterator.AsyncIterator;
import de.insulink.api.user.UserRepository;
import de.insulink.api.user.settings.UserSettings;
import de.insulink.api.user.settings.UserSettingsRepository;
import de.insulink.api.user.settings.history.UserSettingsChangeRepository;
import de.insulink.api.user.settings.history.UserSettingsDiff;
import de.insulink.api.web.request.ApiRequestBody;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserSettingsController extends AppRestController {
  private final UserSettingsRepository settingsRepository;
  private final UserSettingsChangeRepository changeRepository;

  private UserSettingsController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, UserSettingsRepository settingsRepository,
    UserSettingsChangeRepository changeRepository
  ) {
    super(authenticationKey, userRepository);
    this.settingsRepository = settingsRepository;
    this.changeRepository = changeRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/user/settings/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findUserSettings(
    HttpServletRequest request
  ) {
    return settingsRepository.findById(findUserId(request))
      .thenApply(settings -> ApiResponse.success(Map.of("settings",
        settings.map(UserSettings::content).orElse("{}"))));
  }

  @AppEndpoint
  @RequestMapping(path = "/user/settings/change/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> changeUserSettings(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var content = ApiRequestBody.of(payload, response).getString("settings");
    if (content.length() > UserSettings.MAXIMUM_CONTENT_LENGTH || !isValidJson(content)) {
      return ApiResponse.error(1000).future();
    }
    var settings = UserSettings.create(findUserId(request), content,
      System.currentTimeMillis());
    return settingsRepository.findById(settings.userId())
      .thenCompose(stored -> storeSettings(stored, settings))
      .thenApply(_ -> ApiResponse.success());
  }

  /**
   * Saves the new blob and records every key whose value differs from the
   * stored one, so it can later be traced when a setting changed. A stored blob
   * that is missing or not JSON (signup does not validate it) counts as empty.
   */
  private CompletableFuture<?> storeSettings(
    Optional<UserSettings> stored, UserSettings settings
  ) {
    var changes = UserSettingsDiff.create(settings.userId(), settings.lastUpdatedAt())
      .changes(stored.map(UserSettings::content).filter(this::isValidJson)
        .orElse("{}"), settings.content());
    return settingsRepository.save(settings)
      .thenCompose(_ -> AsyncIterator.execute(changes, changeRepository::save));
  }

  private boolean isValidJson(String content) {
    try {
      new JSONObject(content);
      return true;
    } catch (JSONException _) {
      return false;
    }
  }
}
