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

  private SignInController(
    @Qualifier("authenticationKey") Key authenticationKey,
    @Qualifier("refreshKey") Key refreshKey,
    UserRepository userRepository, UserSessionRepository sessionRepository,
    DatabaseReader geoDatabaseReader, Hashing hashing
  ) {
    super(authenticationKey, refreshKey, userRepository, sessionRepository,
      geoDatabaseReader);
    this.hashing = hashing;
  }

  @RequestMapping(path = "/signin/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> signIn(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var name = body.getString("name");
    var password = body.getString("password");
    return signIn(name, password)
      .exceptionally(_ -> null)
      .thenCompose(user -> user == null ?
        ApiResponse.error(1000).future() :
        authenticationResponse(request, user));
  }

  private CompletableFuture<User> signIn(String name, String password) {
    return userRepository().findByName(name)
      .thenApply(user -> signIn(user.orElse(null), password));
  }

  private User signIn(User user, String password) {
    if (user == null) {
      return null;
    }
    if (!hashing.matches(password, user.password())) {
      return null;
    }
    return user;
  }
}
