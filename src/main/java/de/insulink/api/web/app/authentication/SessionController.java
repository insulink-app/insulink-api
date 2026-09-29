package de.insulink.api.web.app.authentication;

import com.maxmind.geoip2.DatabaseReader;
import de.insulink.api.user.User;
import de.insulink.api.user.UserRepository;
import de.insulink.api.user.session.UserSession;
import de.insulink.api.user.session.UserSessionRepository;
import de.insulink.api.web.request.ApiRequestBody;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class SessionController extends AuthenticationController {
  private SessionController(
    @Qualifier("authenticationKey") Key authenticationKey,
    @Qualifier("refreshKey") Key refreshKey,
    UserRepository userRepository, UserSessionRepository sessionRepository,
    DatabaseReader geoDatabaseReader
  ) {
    super(authenticationKey, refreshKey, userRepository, sessionRepository,
      geoDatabaseReader);
  }

  @RequestMapping(path = "/refresh/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> refresh(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = ApiRequestBody.of(payload, response);
    var refreshToken = body.getString("refresh_token");
    var result = verifyToken(refreshKey(), refreshToken);
    if (result == null) {
      return ApiResponse.error(1000).future();
    }
    var userId = UUID.fromString(result.get("id", String.class));
    var sessionId = UUID.fromString(result.get("session", String.class));
    return userRepository().existsById(userId)
      .thenCompose(userExists -> sessionRepository().existsById(sessionId)
        .thenCompose(sessionExists -> refresh(refreshToken, userId,
          sessionId, userExists, sessionExists)));
  }

  private CompletableFuture<ApiResponse> refresh(
    String refreshToken, UUID userId, UUID sessionId, boolean userExists,
    boolean sessionExists
  ) {
    if (!userExists || !sessionExists) {
      return ApiResponse.error(1001).future();
    }
    return userRepository().findById(userId)
      .thenCompose(user -> sessionRepository().findById(sessionId)
        .thenApply(session -> refresh(refreshToken,
          user.get(), session.get())));
  }

  private ApiResponse refresh(
    String refreshToken, User user, UserSession session
  ) {
    if (!session.acceptsRefreshToken(refreshToken)) {
      return ApiResponse.error(1002);
    }
    var authenticationToken = generateAuthenticationToken(user.id(), session.id());
    return ApiResponse.success(Map.of(
      "authentication_token", authenticationToken,
      "refresh_token", redeemRefreshToken(refreshToken, user, session)));
  }

  /**
   * The refresh token to hand back. Only the current one rotates; the one it
   * replaced gets the current one again, so a retried or doubled refresh (the
   * app and its background service at once) ends on the same token instead of
   * racing each other into a logout.
   */
  private String redeemRefreshToken(
    String refreshToken, User user, UserSession session
  ) {
    if (!refreshToken.equals(session.lastRefreshToken())) {
      return session.lastRefreshToken();
    }
    var newRefreshToken = generateRefreshToken(user.id(), session.id());
    session.updateRefreshToken(newRefreshToken);
    sessionRepository().save(session);
    return newRefreshToken;
  }

  @RequestMapping(path = "/logout/", method = RequestMethod.GET)
  public CompletableFuture<Void> logout(
    HttpServletRequest request
  ) {
    var sessionId = findSessionId(request);
    return findUser(request)
      .exceptionally(_ -> null)
      .thenCompose(user -> user == null ?
        CompletableFuture.completedFuture(null) :
        sessionRepository().findById(sessionId).thenApply(Optional::get)
          .thenCompose(this::closeSession));
  }

  private CompletableFuture<Void> closeSession(UserSession session) {
    session.close();
    return sessionRepository().save(session).thenApply(_ -> null);
  }

  @AppEndpoint
  @RequestMapping(path = "/authorized/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> isAuthorized(
    HttpServletRequest request
  ) {
    return findUser(request)
      .exceptionally(_ -> null)
      .thenApply(user -> ApiResponse.success(Map.of("authorized", user != null)));
  }
}