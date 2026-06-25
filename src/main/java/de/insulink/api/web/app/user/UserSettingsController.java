package de.insulink.api.web.app.user;

import de.insulink.api.user.UserRepository;
import de.insulink.api.user.settings.UserSettings;
import de.insulink.api.user.settings.UserSettingsRepository;
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
import java.util.concurrent.CompletableFuture;

@RestController
public final class UserSettingsController extends AppRestController {
  private final UserSettingsRepository settingsRepository;

  private UserSettingsController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, UserSettingsRepository settingsRepository
  ) {
    super(authenticationKey, userRepository);
    this.settingsRepository = settingsRepository;
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
    if (!isValidJson(content)) {
      return ApiResponse.error(1000).future();
    }
    var settings = UserSettings.create(findUserId(request), content,
      System.currentTimeMillis());
    return settingsRepository.save(settings).thenApply(_ -> ApiResponse.success());
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
