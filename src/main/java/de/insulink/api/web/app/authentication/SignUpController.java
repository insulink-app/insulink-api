package de.insulink.api.web.app.authentication;

import com.google.common.collect.Lists;
import com.maxmind.geoip2.DatabaseReader;
import de.insulink.api.hashing.Hashing;
import de.insulink.api.iterator.AsyncIterator;
import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import de.insulink.api.user.device.UserDevice;
import de.insulink.api.user.device.UserDeviceRepository;
import de.insulink.api.user.session.UserSessionRepository;
import de.insulink.api.user.settings.UserSettings;
import de.insulink.api.user.settings.UserSettingsRepository;
import de.insulink.api.web.request.ApiRequestBody;
import de.insulink.api.web.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public class SignUpController extends AuthenticationController {
  private final UserDeviceRepository deviceRepository;
  private final UserSettingsRepository settingsRepository;
  private final Hashing hashing;

  private SignUpController(
    @Qualifier("authenticationKey") Key authenticationKey,
    @Qualifier("refreshKey") Key refreshKey,
    UserRepository userRepository, UserDeviceRepository deviceRepository,
    UserSessionRepository sessionRepository,
    UserSettingsRepository settingsRepository,
    DatabaseReader geoDatabaseReader, Hashing hashing
  ) {
    super(authenticationKey, refreshKey, userRepository, sessionRepository,
      geoDatabaseReader);
    this.deviceRepository = deviceRepository;
    this.settingsRepository = settingsRepository;
    this.hashing = hashing;
  }

  @RequestMapping(path = "/signup/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> signUp(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    return signUp(body)
      .exceptionally(_ -> null)
      .thenCompose(user -> user == null ?
        ApiResponse.error(1000).future() :
        authenticationResponse(request, user));
  }

  private CompletableFuture<User> signUp(
    ApiRequestBody body
  ) {
    return userRepository().generateAvailableId(UUID::randomUUID)
      .thenCompose(userId -> signUp(userId, body));
  }

  private CompletableFuture<User> signUp(
    UUID userId, ApiRequestBody body
  ) {
    var legalAccepted = body.getBoolean("legal_accepted");
    if (!legalAccepted) {
      return CompletableFuture.completedFuture(null);
    }
    var name = body.getSanitizedString("name");
    if (name.isBlank()) {
      return CompletableFuture.completedFuture(null);
    }
    return deviceRepository.generateAvailableId(UUID::randomUUID)
      .thenCompose(deviceId -> signUp(userId, name, body.getString("password"),
        body.getString("language"), legalAccepted, deviceId,
        body.getString("device_id"), body.getString("operating_system"),
        body.getString("operating_system_version"),
        body.getString("device_brand"), body.getString("device_model"),
        body.getString("device_name"), body.getString("settings")));
  }

  private CompletableFuture<User> signUp(
    UUID id, String name, String password, String language, boolean compliant,
    UUID deviceId, String publicDeviceId, String operatingSystem,
    String operatingSystemVersion, String deviceBrand, String deviceModel,
    String deviceName, String settings
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    var user = User.create(id, name, hashing.hash(password), language, compliant,
      System.currentTimeMillis());
    var device = UserDevice.create(deviceId, id, publicDeviceId, operatingSystem,
      operatingSystemVersion, deviceBrand, deviceModel, deviceName);
    processes.add(userRepository().save(user).thenApply(_ -> null));
    processes.add(deviceRepository.save(device).thenApply(_ -> null));
    processes.add(settingsRepository.save(UserSettings.create(id, settings))
      .thenApply(_ -> null));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(_ -> user);
  }
}
