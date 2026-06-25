package de.insulink.api.web.app.authentication;

import com.google.common.collect.Lists;
import com.maxmind.geoip2.DatabaseReader;
import de.insulink.api.web.request.ApiRequestBody;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.iterator.AsyncIterator;
import de.insulink.api.sms.SmsRateLimit;
import de.insulink.api.sms.SmsVerification;
import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import de.insulink.api.user.device.UserDevice;
import de.insulink.api.user.device.UserDeviceRepository;
import de.insulink.api.user.session.UserAgent;
import de.insulink.api.user.session.UserSession;
import de.insulink.api.user.session.UserSessionRepository;
import de.insulink.api.user.session.UserSessionStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public class SignupController extends AuthenticationController {
  private final UserDeviceRepository userDeviceRepository;
  private final UserSessionRepository userSessionRepository;
  private final SmsVerification smsVerification;
  private final SmsRateLimit smsRateLimit;
  private final DatabaseReader geoDatabaseReader;

  private SignupController(
    @Qualifier("verificationKey") Key verificationKey,
    @Qualifier("authenticationKey") Key authenticationKey,
    @Qualifier("refreshKey") Key refreshKey,
    UserRepository userRepository, UserDeviceRepository userDeviceRepository,
    UserSessionRepository userSessionRepository,
    SmsVerification smsVerification, SmsRateLimit smsRateLimit,
    DatabaseReader geoDatabaseReader
  ) {
    super(verificationKey, authenticationKey, refreshKey, userRepository);
    this.userDeviceRepository = userDeviceRepository;
    this.userSessionRepository = userSessionRepository;
    this.smsVerification = smsVerification;
    this.smsRateLimit = smsRateLimit;
    this.geoDatabaseReader = geoDatabaseReader;
  }

  @RequestMapping(path = "/signup/request/code/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> sendSignupCode(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var phoneNumber = body.getString("phone_number");
    if (!smsRateLimit.isAllowed(phoneNumber)) {
      return ApiResponse.error(1000).future();
    }
    return smsVerification.sendVerificationCode(phoneNumber)
      .exceptionally(_ -> null)
      .thenApply(verification -> verification != null &&
        "pending".equals(verification.getStatus().toString()))
      .thenApply(success -> success ?
        ApiResponse.success() : ApiResponse.error(1001));
  }

  @RequestMapping(path = "/signup/verify/code/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> verifySignupCode(
    HttpServletRequest request,@RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var phoneNumber = body.getString("phone_number");
    var code = body.getString("code");
    var publicKey = body.getString("public_key");
    var firebaseToken = body.getString("firebase_token");
    return smsVerification.verifyCode(phoneNumber, code)
      .thenCompose(approved -> verifySignupCode(request, phoneNumber, publicKey,
        firebaseToken, approved));
  }

  private CompletableFuture<ApiResponse> verifySignupCode(
    HttpServletRequest request, String phoneNumber, String publicKey,
    String firebaseToken, boolean approved
  ) {
    if (!approved) {
      return ApiResponse.error(1000).future();
    }
    return userRepository().findByPhoneNumber(phoneNumber)
      .thenCompose(user -> verifySignupCode(request, phoneNumber, publicKey,
        firebaseToken, user.orElse(null)));
  }

  private CompletableFuture<ApiResponse> verifySignupCode(
    HttpServletRequest request, String phoneNumber, String publicKey,
    String firebaseToken, User user
  ) {
    if (user == null) {
      var verificationToken = generateVerificationToken(phoneNumber);
      return ApiResponse.success(Map.of("new_user", true,
        "verification_token", verificationToken)).future();
    }
    user.changePublicKey(publicKey);
    user.changeFirebaseToken(firebaseToken);
    userRepository().save(user);
    return completeSignup(request, user).thenApply(response ->
      response.expand(Map.of("new_user", false)));
  }

  @RequestMapping(path = "/signup/complete/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> completeSignup(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var verificationToken = body.getString("verification_token");
    var result = verifyToken(verificationKey(), verificationToken);
    if (result == null) {
      return ApiResponse.error(1000).future();
    }
    var phoneNumber = result.get("phone_number", String.class);
    return signupUser(phoneNumber, body)
      .exceptionally(_ -> null)
      .thenCompose(user -> user == null ?
        ApiResponse.error(1001).future() :
        completeSignup(request, user));
  }

  private CompletableFuture<User> signupUser(
    String phoneNumber, ApiRequestBody body
  ) {
    return userRepository().generateAvailableId(UUID::randomUUID)
      .thenCompose(userId -> signupUser(phoneNumber, userId, body));
  }

  private CompletableFuture<User> signupUser(
    String phoneNumber, UUID userId, ApiRequestBody body
  ) {
    var legalAccepted = body.getBoolean("legal_accepted");
    if (!legalAccepted) {
      return CompletableFuture.completedFuture(null);
    }
    var name = body.getSanitizedString("name");
    if (name.isBlank()) {
      return CompletableFuture.completedFuture(null);
    }
    return userDeviceRepository.generateAvailableId(UUID::randomUUID)
      .thenCompose(deviceId -> signupUser(userId, phoneNumber, name,
        body.getString("language"), body.getString("public_key"),
        body.getString("firebase_token"), legalAccepted, deviceId,
        body.getString("device_id"), body.getString("operating_system"),
        body.getString("operating_system_version"),
        body.getString("device_brand"), body.getString("device_model"),
        body.getString("device_name")));
  }

  private CompletableFuture<User> signupUser(
    UUID id, String phoneNumber, String name, String language, String publicKey,
    String firebaseToken, boolean compliant,
    UUID deviceId, String publicDeviceId, String operatingSystem,
    String operatingSystemVersion, String deviceBrand, String deviceModel,
    String deviceName
  ) {
    var processes = Lists.<CompletableFuture<Void>>newArrayList();
    var user = User.create(id, phoneNumber, name, language, compliant, publicKey,
      firebaseToken, System.currentTimeMillis());
    var device = UserDevice.create(deviceId, id, publicDeviceId, operatingSystem,
      operatingSystemVersion, deviceBrand, deviceModel, deviceName);
    processes.add(userRepository().save(user).thenApply(_ -> null));
    processes.add(userDeviceRepository.save(device).thenApply(_ -> null));
    return AsyncIterator.execute(processes, process -> process)
      .thenApply(_ -> user);
  }

  private CompletableFuture<ApiResponse> completeSignup(
    HttpServletRequest request, User user
  ) {
    return userSessionRepository.generateAvailableId(UUID::randomUUID)
      .thenComposeAsync(sessionId -> completeSignup(request, user, sessionId));
  }

  private CompletableFuture<ApiResponse> completeSignup(
    HttpServletRequest request, User user, UUID sessionId
  ) {
    var authenticationToken = generateAuthenticationToken(user.id(), sessionId);
    var refreshToken = generateRefreshToken(user.id(), sessionId);
    return storeSession(request, user.id(), sessionId, refreshToken)
      .thenApply(_ -> ApiResponse.success(Map.of("user", user.id(),
        "phone_number", user.phoneNumber(), "name", user.name(),
        "authentication_token", authenticationToken,
        "refresh_token", refreshToken)));
  }

  private CompletableFuture<UserSession> storeSession(
    HttpServletRequest request, UUID userId, UUID sessionId, String refreshToken
  ) {
    var country = "";
    var city = "";
    var platform = "";
    var ipAddress = request.getHeader("X-Real-IP");
    try {
      var location = geoDatabaseReader.city(InetAddress.getByName(ipAddress));
      country = location.country().name();
      city = location.city().name();
      platform = UserAgent.create(request.getHeader("User-Agent")).findPlatform();
    } catch (Exception ignored) {
    }
    var session = UserSession.create(sessionId, userId,
      UserSessionStatus.ACTIVE, platform, ipAddress, country, city,
      System.currentTimeMillis(), refreshToken, System.currentTimeMillis());
    return userSessionRepository.save(session);
  }
}
