package de.insulink.api.web.app.authentication;

import com.maxmind.geoip2.DatabaseReader;
import de.insulink.api.hashing.Hashing;
import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import de.insulink.api.user.session.UserSessionRepository;
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
import java.util.concurrent.CompletableFuture;

@RestController
public class SignInController extends AuthenticationController {
  private final Hashing hashing;
  private final SignInThrottle throttle;
  private final String unknownAccountHash;

  private SignInController(
    @Qualifier("authenticationKey") Key authenticationKey,
    @Qualifier("refreshKey") Key refreshKey,
    UserRepository userRepository, UserSessionRepository sessionRepository,
    DatabaseReader geoDatabaseReader, Hashing hashing, SignInThrottle throttle
  ) {
    super(authenticationKey, refreshKey, userRepository, sessionRepository,
      geoDatabaseReader);
    this.hashing = hashing;
    this.throttle = throttle;
    this.unknownAccountHash = hashing.hash("unknown account");
  }

  /** 1000 for wrong credentials, 1003 while the throttle refuses attempts. */
  @RequestMapping(path = "/signin/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> signIn(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var name = body.getString("name");
    var address = String.valueOf(request.getHeader("X-Real-IP"));
    if (throttle.isBlocked(name, address)) {
      return ApiResponse.error(1003).future();
    }
    return signIn(name, body.getString("password"))
      .exceptionally(_ -> null)
      .thenCompose(user -> signedIn(request, user, name, address));
  }

  private CompletableFuture<ApiResponse> signedIn(
    HttpServletRequest request, User user, String name, String address
  ) {
    if (user == null) {
      throttle.recordFailure(name, address);
      return ApiResponse.error(1000).future();
    }
    throttle.recordSuccess(name);
    return authenticationResponse(request, user);
  }

  private CompletableFuture<User> signIn(String name, String password) {
    return userRepository().findByName(name)
      .thenApply(user -> signIn(user.orElse(null), password));
  }

  /**
   * An unknown name still costs one hash check, so the answer time does not
   * tell which names exist.
   */
  private User signIn(User user, String password) {
    if (user == null) {
      hashing.matches(password, unknownAccountHash);
      return null;
    }
    if (!hashing.matches(password, user.password())) {
      return null;
    }
    return user;
  }
}
